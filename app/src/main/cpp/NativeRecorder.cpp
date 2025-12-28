#include <jni.h>
#include <string>
#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window_jni.h>
#include <android/api-level.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <vector>
#include <queue>
#include "Aec3Processor.h"
#include "RnNoiseProcessor.h"

#define LOG_TAG "NativeRecorder"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Forward declarations
extern "C" JNIEXPORT jboolean JNICALL Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(JNIEnv*, jobject);

struct Packet {
    uint8_t* data;
    size_t size;
    AMediaCodecBufferInfo info;
    int trackIndex;
};

struct RecorderContext {
    AMediaCodec* videoCodec = nullptr;
    AMediaCodec* audioCodec = nullptr;
    AMediaMuxer* muxer = nullptr;
    ANativeWindow* inputSurface = nullptr;
    
    int videoTrackIndex = -1;
    int audioTrackIndex = -1;
    
    std::atomic<bool> isRecording{false};
    std::atomic<bool> isPaused{false};
    std::atomic<bool> muxerStarted{false};
    
    std::thread videoPollingThread;
    std::thread audioPollingThread;
    std::mutex contextMutex;
    
    // Muxer start synchronization
    int expectedTracks = 1; // Default video only
    int addedTracks = 0;
    std::mutex muxerMutex;
    std::queue<Packet*> packetQueue; // Buffer packets until muxer starts
    
    // Timestamp adjustment
    std::atomic<int64_t> firstFrameTime{-1};
    std::atomic<int64_t> totalPauseDuration{0};
    int64_t pauseStartTime = 0;
    
    int fd = -1; // Keep fd reference for safety (though muxer usually manages it)

    // Track state for monotonicity
    int64_t lastVideoPts = -1;
    int64_t lastAudioPts = -1;

    // Audio sample tracking
    std::atomic<int64_t> audioStartTime{-1};
    std::atomic<int64_t> totalAudioFrames{0};

    std::unique_ptr<bokbok::Aec3Processor> aecProcessor;
    std::unique_ptr<bokbok::RnNoiseProcessor> rnnoiseProcessor;

    // Audio level metering
    std::atomic<float> micRmsLevel{0.0f};
    std::atomic<float> micPeakLevel{0.0f};
    std::atomic<float> internalRmsLevel{0.0f};
    std::atomic<float> internalPeakLevel{0.0f};

    // Aggressive Noise Gate with Hysteresis
    // AGGRESSIVE: High threshold to completely eliminate speaker feedback when not speaking
    static constexpr float NOISE_GATE_THRESHOLD = 100.0f;  // Much higher to catch only real voice
    static constexpr float NOISE_GATE_HYSTERESIS = 90.0f;  // Hysteresis to prevent flutter
    static constexpr int GATE_HOLD_FRAMES = 5;             // ~50ms hold (fast release)
    static constexpr int GATE_ATTACK_FRAMES = 1;           // ~10ms attack (fast open)
    int gateHoldCounter = 0;
    bool isGateOpen = false;
    float gateGain = 0.0f;  // Smooth gain for gate (0.0 = closed, 1.0 = open)
    
    // Mix Ratio (user-controlled)
    std::atomic<float> internalAudioRatio{1.0f};  // 0.0 to 1.0 (default: full internal)
    std::atomic<float> micAudioRatio{1.0f};       // 0.0 to 1.0 (default: full mic)
    
    // Mic post-gain (applied after all processing)
    static constexpr float MIC_POST_GAIN = 50.0f;  // Boost clean voice signal to match internal audio levels
};

static RecorderContext* gCtx = nullptr;
static std::mutex gGlobalMutex;

static int64_t getCurrentTimeUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

// Helper to write buffered packets once muxer starts
static void flushPacketQueue(RecorderContext* ctx) {
    if (!ctx->muxerStarted) return;
    
    while (!ctx->packetQueue.empty()) {
        Packet* p = ctx->packetQueue.front();
        ctx->packetQueue.pop();
        
        AMediaMuxer_writeSampleData(ctx->muxer, p->trackIndex, p->data, &p->info);
        
        delete[] p->data;
        delete p;
    }
}

static void handleOutputBuffer(RecorderContext* ctx, int trackIndex, uint8_t* buffer, AMediaCodecBufferInfo* info) {
    std::lock_guard<std::mutex> lock(ctx->muxerMutex);
    if (!ctx->muxer) return;

    // First frame initialization (global sync point)
    int64_t rawPts = info->presentationTimeUs;
    if (ctx->firstFrameTime.load() < 0) {
        ctx->firstFrameTime.store(rawPts);
    }

    int64_t adjustedPts = rawPts - ctx->firstFrameTime.load();

    // Subtract pause duration for video specifically. 
    // Video raw timestamps (uptime) continue to tick during pause.
    // Audio timestamps (based on sample count) do not, so we don't subtract here.
    if (trackIndex == ctx->videoTrackIndex) {
        adjustedPts -= ctx->totalPauseDuration.load();
    }

    // Strictly monotonic check to satisfy MPEG4Writer
    int64_t* lastPtsRef = (trackIndex == ctx->videoTrackIndex) ? &ctx->lastVideoPts : &ctx->lastAudioPts;
    if (adjustedPts <= *lastPtsRef) {
        adjustedPts = *lastPtsRef + 1000; // Force 1ms separation to avoid "burst" jitter
    }
    *lastPtsRef = adjustedPts;
    info->presentationTimeUs = adjustedPts;

    if (ctx->muxerStarted) {
        if (!ctx->packetQueue.empty()) {
            flushPacketQueue(ctx);
        }
        AMediaMuxer_writeSampleData(ctx->muxer, trackIndex, buffer, info);
    } else {
        // Buffer until muxer starts
        Packet* p = new Packet();
        p->data = new uint8_t[info->size];
        memcpy(p->data, buffer + info->offset, info->size);
        p->info = *info;
        p->trackIndex = trackIndex;
        ctx->packetQueue.push(p);
    }
}

static void pollVideo(RecorderContext* ctx) {
    AMediaCodecBufferInfo info;
    while (ctx->isRecording.load()) {
        if (ctx->isPaused.load()) {
             std::this_thread::sleep_for(std::chrono::milliseconds(10));
             continue;
        }

        ssize_t status = AMediaCodec_dequeueOutputBuffer(ctx->videoCodec, &info, 5000);
        if (status >= 0) {
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                AMediaCodec_releaseOutputBuffer(ctx->videoCodec, status, false);
                continue;
            }

            if (info.size > 0 && ctx->videoTrackIndex >= 0) {
                uint8_t* buffer = AMediaCodec_getOutputBuffer(ctx->videoCodec, status, nullptr);
                if (buffer) {
                    handleOutputBuffer(ctx, ctx->videoTrackIndex, buffer, &info);
                }
            }
            AMediaCodec_releaseOutputBuffer(ctx->videoCodec, status, false);
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) break;
        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            std::lock_guard<std::mutex> lock(ctx->muxerMutex);
            if (ctx->muxerStarted) continue;
            
            AMediaFormat* newFormat = AMediaCodec_getOutputFormat(ctx->videoCodec);
            if (newFormat) {
                ctx->videoTrackIndex = AMediaMuxer_addTrack(ctx->muxer, newFormat);
                ctx->addedTracks++;
                LOGI("Video track added: %d", ctx->videoTrackIndex);
                
                if (ctx->addedTracks == ctx->expectedTracks) {
                    AMediaMuxer_start(ctx->muxer);
                    ctx->muxerStarted = true;
                    LOGI("Muxer started!");
                    flushPacketQueue(ctx);
                }
                AMediaFormat_delete(newFormat);
            }
        }
    }
}

static void pollAudio(RecorderContext* ctx) {
    if (!ctx->audioCodec) return;
    AMediaCodecBufferInfo info;
    
    while (ctx->isRecording.load()) {
        if (ctx->isPaused.load()) {
             std::this_thread::sleep_for(std::chrono::milliseconds(10));
             continue;
        }

        ssize_t status = AMediaCodec_dequeueOutputBuffer(ctx->audioCodec, &info, 5000);
        if (status >= 0) {
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_CODEC_CONFIG) {
                AMediaCodec_releaseOutputBuffer(ctx->audioCodec, status, false);
                continue;
            }

            if (info.size > 0 && ctx->audioTrackIndex >= 0) {
                uint8_t* buffer = AMediaCodec_getOutputBuffer(ctx->audioCodec, status, nullptr);
                if (buffer) {
                    handleOutputBuffer(ctx, ctx->audioTrackIndex, buffer, &info);
                }
            }
            AMediaCodec_releaseOutputBuffer(ctx->audioCodec, status, false);
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) break;
        } else if (status == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            std::lock_guard<std::mutex> lock(ctx->muxerMutex);
            if (ctx->muxerStarted) continue;

            AMediaFormat* newFormat = AMediaCodec_getOutputFormat(ctx->audioCodec);
            if (newFormat) {
                ctx->audioTrackIndex = AMediaMuxer_addTrack(ctx->muxer, newFormat);
                ctx->addedTracks++;
                LOGI("Audio track added: %d", ctx->audioTrackIndex);

                if (ctx->addedTracks == ctx->expectedTracks) {
                    AMediaMuxer_start(ctx->muxer);
                    ctx->muxerStarted = true;
                    LOGI("Muxer started (Audio Trigger)!");
                    flushPacketQueue(ctx);
                }
                AMediaFormat_delete(newFormat);
            }
        }
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("Native Recorder Engine v3.0 (Audio Enabled)");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_setup(
        JNIEnv* env, jobject, jint width, jint height, jint bitrate, jint fps, jboolean useHevc, jboolean audioEnabled, jstring outputPath) {
    
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    
    if (gCtx) {
        Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(env, nullptr); 
    }
    
    gCtx = new RecorderContext();
    gCtx->expectedTracks = audioEnabled ? 2 : 1; 

    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    gCtx->fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(outputPath, path);
    
    if (gCtx->fd < 0) return JNI_FALSE;
    gCtx->muxer = AMediaMuxer_new(gCtx->fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    if (!gCtx->muxer) {
        close(gCtx->fd);
        gCtx->fd = -1;
        return JNI_FALSE;
    }

    // --- VIDEO SETUP ---
    const char* mime = useHevc ? "video/hevc" : "video/avc";
    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, width);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, height);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, bitrate);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, fps);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
    
    // Bitrate mode (CBR) and frame repetition are available in MediaCodec, 
    // but the NDK constants might be missing in older toolchains.
    // We use string literals for broader compatibility.
    if (android_get_device_api_level() >= 28) {
        AMediaFormat_setInt32(format, "bitrate-mode", 2); // 2 = BITRATE_MODE_CBR
    }
    
    // Crucial for maintaining constant FPS metadata: 
    // This tells the encoder to repeat the last frame if no new data arrives 
    // within 1/FPS seconds.
    int64_t repeatUs = 1000000LL / fps;
    AMediaFormat_setInt64(format, "repeat-previous-frame-after", repeatUs);

    gCtx->videoCodec = AMediaCodec_createEncoderByType(mime);
    AMediaCodec_configure(gCtx->videoCodec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaCodec_createInputSurface(gCtx->videoCodec, &gCtx->inputSurface);
    AMediaFormat_delete(format);

    // --- AUDIO SETUP ---
    const char* audioMime = "audio/mp4a-latm";
    AMediaFormat* aFormat = AMediaFormat_new();
    AMediaFormat_setString(aFormat, AMEDIAFORMAT_KEY_MIME, audioMime);
    AMediaFormat_setInt32(aFormat, AMEDIAFORMAT_KEY_AAC_PROFILE, 2); // AAC-LC
    AMediaFormat_setInt32(aFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, 48000);
    AMediaFormat_setInt32(aFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, 2);
    AMediaFormat_setInt32(aFormat, AMEDIAFORMAT_KEY_BIT_RATE, 256000);
    const int MAX_INPUT_SIZE = 16384; 
    AMediaFormat_setInt32(aFormat, AMEDIAFORMAT_KEY_MAX_INPUT_SIZE, MAX_INPUT_SIZE);

    gCtx->audioCodec = AMediaCodec_createEncoderByType(audioMime);
    if (gCtx->audioCodec) {
        AMediaCodec_configure(gCtx->audioCodec, aFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
        // Initialize AEC3 Processor (48kHz, Stereo)
        gCtx->aecProcessor = std::make_unique<bokbok::Aec3Processor>(48000, 2);
        // Initialize RNNoise Processor (48kHz, Stereo) for neural network noise reduction
        gCtx->rnnoiseProcessor = std::make_unique<bokbok::RnNoiseProcessor>(48000, 2);
        LOGI("Audio processors initialized: AEC3 + RNNoise");
    } else {
        LOGE("Failed to create audio codec");
        gCtx->expectedTracks = 1; // Fallback to video only
    }
    AMediaFormat_delete(aFormat);

    return JNI_TRUE;
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_getInputSurface(JNIEnv* env, jobject) {
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    if (!gCtx || !gCtx->inputSurface) return nullptr;
    return ANativeWindow_toSurface(env, gCtx->inputSurface);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_start(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    if (!gCtx || !gCtx->videoCodec) return JNI_FALSE;

    AMediaCodec_start(gCtx->videoCodec);
    if (gCtx->audioCodec) AMediaCodec_start(gCtx->audioCodec);

    gCtx->isRecording = true;
    gCtx->videoPollingThread = std::thread(pollVideo, gCtx);
    if (gCtx->audioCodec) gCtx->audioPollingThread = std::thread(pollAudio, gCtx);

    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_writeAudioBuffer(
        JNIEnv* env, jobject, jshortArray data, jint length) {
    if (!gCtx || !gCtx->audioCodec || !gCtx->isRecording.load() || gCtx->isPaused.load()) return JNI_FALSE;

    jshort* samples = env->GetShortArrayElements(data, nullptr);
    size_t sizeInBytes = length * 2;
    
    ssize_t idx = AMediaCodec_dequeueInputBuffer(gCtx->audioCodec, 5000);
    if (idx >= 0) {
        size_t bufSize;
        uint8_t* buf = AMediaCodec_getInputBuffer(gCtx->audioCodec, idx, &bufSize);
        if (buf) {
            size_t copySize = (sizeInBytes < bufSize) ? sizeInBytes : bufSize;
            memcpy(buf, samples, copySize);
            
            // Calculate PTS based on processed sample count but anchored to system uptime
            // to stay in the same domain as the video surface.
            if (gCtx->audioStartTime.load() < 0) {
                gCtx->audioStartTime.store(getCurrentTimeUs());
            }
            
            int64_t currentFrames = gCtx->totalAudioFrames.load();
            int64_t ptsUs = gCtx->audioStartTime.load() + (currentFrames * 1000000) / 48000;
            
            AMediaCodec_queueInputBuffer(gCtx->audioCodec, idx, 0, copySize, ptsUs, 0);
            
            // Increment frame count (length is shorts, 2 shorts per stereo frame)
            gCtx->totalAudioFrames += (length / 2);
        }
    }
    
    env->ReleaseShortArrayElements(data, samples, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_writeAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray micData, jshortArray internalData, jint length) {
    if (!gCtx || !gCtx->audioCodec || !gCtx->isRecording.load() || gCtx->isPaused.load()) return JNI_FALSE;

    jshort* micSamples = env->GetShortArrayElements(micData, nullptr);
    jshort* internalSamples = env->GetShortArrayElements(internalData, nullptr);

    // ============================================================
    // STAGE 1: Echo Cancellation (AEC3)
    // Feed internal audio as reference, then process mic to remove echo
    // ============================================================
    if (gCtx->aecProcessor) {
        gCtx->aecProcessor->AnalyzeInternal(internalSamples, length);
        gCtx->aecProcessor->ProcessMic(micSamples, length);
    }

    // ============================================================
    // STAGE 2: RNNoise Neural Network Noise Reduction
    // This aggressively removes any remaining noise/artifacts from mic
    // Returns VAD (Voice Activity Detection) probability
    // ============================================================
    float vadProbability = 0.0f;
    if (gCtx->rnnoiseProcessor) {
        vadProbability = gCtx->rnnoiseProcessor->Process(micSamples, length);
    }

    // ============================================================
    // STAGE 3: Calculate Audio Levels for UI metering
    // ============================================================
    float micSumSquares = 0.0f;
    float micPeak = 0.0f;
    float intSumSquares = 0.0f;
    float intPeak = 0.0f;

    for (int i = 0; i < length; i++) {
        float micSample = std::abs(static_cast<float>(micSamples[i]));
        float intSample = std::abs(static_cast<float>(internalSamples[i]));
        micSumSquares += micSample * micSample;
        intSumSquares += intSample * intSample;
        if (micSample > micPeak) micPeak = micSample;
        if (intSample > intPeak) intPeak = intSample;
    }

    float micRms = std::sqrt(micSumSquares / length);
    float intRms = std::sqrt(intSumSquares / length);

    gCtx->micRmsLevel.store(micRms);
    gCtx->micPeakLevel.store(micPeak);
    gCtx->internalRmsLevel.store(intRms);
    gCtx->internalPeakLevel.store(intPeak);

    // ============================================================
    // STAGE 4: Aggressive Noise Gate with VAD + Hysteresis
    // Uses both RMS threshold AND VAD probability for decision
    // Completely eliminates speaker feedback when not speaking
    // ============================================================
    
    // Combine RMS threshold with VAD for robust voice detection
    // Voice is detected if: high RMS OR high VAD probability
    bool voiceDetected = (micRms > RecorderContext::NOISE_GATE_THRESHOLD) || (vadProbability > 0.7f);
    
    // Gate state machine with hysteresis
    if (voiceDetected) {
        gCtx->isGateOpen = true;
        gCtx->gateHoldCounter = RecorderContext::GATE_HOLD_FRAMES;
    } else if (gCtx->gateHoldCounter > 0) {
        gCtx->gateHoldCounter--;
        // Gate remains open during hold period
    } else {
        gCtx->isGateOpen = false;
    }
    
    // Smooth gate gain transition (prevents clicks/pops)
    // Attack: fast (1 frame = 10ms), Release: gradual
    const float GATE_ATTACK_RATE = 0.5f;   // Fast open
    const float GATE_RELEASE_RATE = 0.1f;  // Slow close (prevents abrupt cutoff)
    
    float targetGain = gCtx->isGateOpen ? 1.0f : 0.0f;
    if (targetGain > gCtx->gateGain) {
        gCtx->gateGain += GATE_ATTACK_RATE;
        if (gCtx->gateGain > 1.0f) gCtx->gateGain = 1.0f;
    } else {
        gCtx->gateGain -= GATE_RELEASE_RATE;
        if (gCtx->gateGain < 0.0f) gCtx->gateGain = 0.0f;
    }

    // ============================================================
    // STAGE 5: Mix with User-Configurable Ratio
    // Internal audio = RAW (0dB, no processing whatsoever)
    // Mic audio = Processed + Gated + Gained
    // ============================================================
    const float micRatio = gCtx->micAudioRatio.load();
    const float internalRatio = gCtx->internalAudioRatio.load();
    const float micPostGain = RecorderContext::MIC_POST_GAIN;
    const float currentGateGain = gCtx->gateGain;
    
    for (int i = 0; i < length; i++) {
        // Mic: Apply gate gain, then post-gain, then user ratio
        float micValue = micSamples[i] * currentGateGain * micPostGain * micRatio;
        
        // Internal: COMPLETELY RAW - just apply user ratio
        float intValue = internalSamples[i] * internalRatio;
        
        // Mix
        float mixed = micValue + intValue;
        
        // Soft-clip limiter (tanh-like) to prevent harsh digital clipping
        if (mixed > 28000.0f) {
            mixed = 28000.0f + (mixed - 28000.0f) * 0.1f;
        } else if (mixed < -28000.0f) {
            mixed = -28000.0f + (mixed + 28000.0f) * 0.1f;
        }
        
        // Store mixed result (reusing micSamples buffer for output)
        micSamples[i] = static_cast<int16_t>(std::max(-32767.0f, std::min(32767.0f, mixed)));
    }

    // ============================================================
    // STAGE 6: Write to AAC Encoder
    // ============================================================
    ssize_t idx = AMediaCodec_dequeueInputBuffer(gCtx->audioCodec, 10000);
    if (idx >= 0) {
        size_t bufSize;
        uint8_t* buf = AMediaCodec_getInputBuffer(gCtx->audioCodec, idx, &bufSize);
        size_t sizeInBytes = length * sizeof(int16_t);
        if (buf && bufSize >= sizeInBytes) {
            memcpy(buf, micSamples, sizeInBytes);
            
            if (gCtx->audioStartTime.load() < 0) {
                gCtx->audioStartTime.store(getCurrentTimeUs());
            }
            int64_t currentFrames = gCtx->totalAudioFrames.load();
            int64_t ptsUs = gCtx->audioStartTime.load() + (currentFrames * 1000000) / 48000;
            
            AMediaCodec_queueInputBuffer(gCtx->audioCodec, idx, 0, sizeInBytes, ptsUs, 0);
            gCtx->totalAudioFrames += (length / 2); // length is shorts, 2 per stereo frame
        }
    } else {
        LOGW("Audio input buffer dequeue timeout! Potential jitter.");
    }

    env->ReleaseShortArrayElements(micData, micSamples, 0);
    env->ReleaseShortArrayElements(internalData, internalSamples, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_getAudioLevels(JNIEnv* env, jobject) {
    jfloatArray result = env->NewFloatArray(4);
    if (!gCtx) {
        float zeros[4] = {0, 0, 0, 0};
        env->SetFloatArrayRegion(result, 0, 4, zeros);
        return result;
    }
    
    float levels[4] = {
        gCtx->micRmsLevel.load(),
        gCtx->micPeakLevel.load(),
        gCtx->internalRmsLevel.load(),
        gCtx->internalPeakLevel.load()
    };
    env->SetFloatArrayRegion(result, 0, 4, levels);
    return result;
}

// Set the audio mix ratio (0.0 to 1.0 for each channel)
// internalRatio: volume of internal/game audio (1.0 = full volume)
// micRatio: volume of microphone audio after processing (1.0 = full volume)
extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_setMixRatio(
        JNIEnv* env, jobject, jfloat internalRatio, jfloat micRatio) {
    if (!gCtx) return;
    
    // Clamp values to valid range
    float intRatio = std::max(0.0f, std::min(1.0f, internalRatio));
    float mRatio = std::max(0.0f, std::min(1.0f, micRatio));
    
    gCtx->internalAudioRatio.store(intRatio);
    gCtx->micAudioRatio.store(mRatio);
    
    LOGI("Mix ratio updated: Internal=%.2f, Mic=%.2f", intRatio, mRatio);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_pause(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    if (gCtx) { 
        gCtx->isPaused = true;
        gCtx->pauseStartTime = getCurrentTimeUs();
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_resume(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    if (gCtx && gCtx->isPaused) {
        gCtx->totalPauseDuration += (getCurrentTimeUs() - gCtx->pauseStartTime);
        gCtx->isPaused = false;
    }
    return JNI_TRUE;
}

 extern "C" JNIEXPORT jboolean JNICALL
 Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(JNIEnv*, jobject) {
     std::lock_guard<std::mutex> lock(gGlobalMutex);
     if (!gCtx) return JNI_FALSE;
 
     gCtx->isRecording = false;
     
     if (gCtx->videoPollingThread.joinable()) gCtx->videoPollingThread.join();
     if (gCtx->audioPollingThread.joinable()) gCtx->audioPollingThread.join();
 
     // Stop Codecs
     if (gCtx->videoCodec) {
         AMediaCodec_stop(gCtx->videoCodec);
         AMediaCodec_delete(gCtx->videoCodec);
     }
     if (gCtx->audioCodec) {
         AMediaCodec_stop(gCtx->audioCodec);
         AMediaCodec_delete(gCtx->audioCodec);
     }
 
     // Stop Muxer
     if (gCtx->muxer) {
         if (gCtx->muxerStarted) AMediaMuxer_stop(gCtx->muxer);
         AMediaMuxer_delete(gCtx->muxer);
     }
     
     if (gCtx->fd >= 0) {
         close(gCtx->fd);
         gCtx->fd = -1;
     }
 
     if (gCtx->inputSurface) ANativeWindow_release(gCtx->inputSurface);
     
     // Cleanup packet queue (Crucial to prevent leaks if stop called before start)
     std::lock_guard<std::mutex> muxLock(gCtx->muxerMutex);
     while (!gCtx->packetQueue.empty()) {
         Packet* p = gCtx->packetQueue.front();
         gCtx->packetQueue.pop();
         delete[] p->data;
         delete p;
     }

     delete gCtx;
     gCtx = nullptr;
     return JNI_TRUE;
 }
 
 extern "C" JNIEXPORT jboolean JNICALL
 Java_com_mustakim_bokbok_data_service_NativeRecorder_captureScreenshot(JNIEnv*, jobject) {
     return JNI_TRUE;
 }

extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_release(JNIEnv* env, jobject thiz) {
   Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(env, thiz);
}
