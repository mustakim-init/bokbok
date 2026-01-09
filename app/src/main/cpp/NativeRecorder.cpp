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
#include <cmath>
#include "RawAudioWriter.h"
#include "PostProcessor.h"

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
    AMediaMuxer* muxer = nullptr;
    ANativeWindow* inputSurface = nullptr;
    
    int videoTrackIndex = -1;
    // Audio track removed from muxer - we record raw optional files now
    
    std::atomic<bool> isRecording{false};
    std::atomic<bool> isPaused{false};
    std::atomic<bool> muxerStarted{false};
    
    std::thread videoPollingThread;
    std::mutex contextMutex;
    
    // Muxer start synchronization
    int expectedTracks = 1; // Always 1 (Video) for this stage
    int addedTracks = 0;
    std::mutex muxerMutex;
    std::queue<Packet*> packetQueue; 
    
    // Timestamp adjustment
    std::atomic<int64_t> firstFrameTime{-1};
    std::atomic<int64_t> totalPauseDuration{0};
    int64_t pauseStartTime = 0;
    
    int fd = -1; 

    // Track state for monotonicity
    int64_t lastVideoPts = -1;

    // Raw Audio Writers
    bokbok::RawAudioWriter micWriter;
    bokbok::RawAudioWriter internalWriter;
    
    // Audio level metering (Still needed for UI)
    std::atomic<float> micRmsLevel{0.0f};
    std::atomic<float> micPeakLevel{0.0f};
    std::atomic<float> internalRmsLevel{0.0f};
    std::atomic<float> internalPeakLevel{0.0f};

    std::atomic<bool> audioEnabled{false};
};

static RecorderContext* gCtx = nullptr;
static std::mutex gGlobalMutex;

static int64_t getCurrentTimeUs() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}

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

    int64_t rawPts = info->presentationTimeUs;
    if (ctx->firstFrameTime.load() < 0) {
        ctx->firstFrameTime.store(rawPts);
    }

    int64_t adjustedPts = rawPts - ctx->firstFrameTime.load();

    if (trackIndex == ctx->videoTrackIndex) {
        adjustedPts -= ctx->totalPauseDuration.load();
    }

    // Monotonicity check
    int64_t* lastPtsRef = &ctx->lastVideoPts;
    if (adjustedPts <= *lastPtsRef) {
        adjustedPts = *lastPtsRef + 1000;
    }
    *lastPtsRef = adjustedPts;
    info->presentationTimeUs = adjustedPts;

    if (ctx->muxerStarted) {
        if (!ctx->packetQueue.empty()) {
            flushPacketQueue(ctx);
        }
        AMediaMuxer_writeSampleData(ctx->muxer, trackIndex, buffer, info);
    } else {
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
                    LOGI("Muxer started (Video Only)!");
                    flushPacketQueue(ctx);
                }
                AMediaFormat_delete(newFormat);
            }
        }
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_stringFromJNI(JNIEnv* env, jobject) {
    return env->NewStringUTF("Native Recorder Engine v4.0 (Raw Capture Mode)");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_setup(
        JNIEnv* env, jobject, 
        jint width, jint height, jint bitrate, jint fps, jboolean useHevc,
        jboolean audioEnabled, 
        jstring videoPath, jstring micPath, jstring internalPath,
        jint audioSampleRate, jint audioBitrate) {
    
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    
    if (gCtx) {
        Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(env, nullptr); 
    }
    
    gCtx = new RecorderContext();
    gCtx->expectedTracks = 1; // Video only for main muxer
    gCtx->audioEnabled = audioEnabled;

    // Video Output
    const char* vPath = env->GetStringUTFChars(videoPath, nullptr);
    gCtx->fd = open(vPath, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(videoPath, vPath);
    
    if (gCtx->fd < 0) {
        LOGE("Failed to open video file");
        return JNI_FALSE;
    }

    gCtx->muxer = AMediaMuxer_new(gCtx->fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    if (!gCtx->muxer) {
        close(gCtx->fd);
        gCtx->fd = -1;
        return JNI_FALSE;
    }

    // Audio Outputs (Raw PCM)
    if (audioEnabled) {
        const char* mPath = env->GetStringUTFChars(micPath, nullptr);
        const char* iPath = env->GetStringUTFChars(internalPath, nullptr);
        
        bool mOk = gCtx->micWriter.open(mPath);
        bool iOk = gCtx->internalWriter.open(iPath);
        
        if (mOk) LOGI("Mic raw writer opened: %s", mPath);
        else LOGE("Failed to open mic raw path: %s", mPath);

        if (iOk) LOGI("Internal raw writer opened: %s", iPath);
        else LOGE("Failed to open internal raw path: %s", iPath);

        env->ReleaseStringUTFChars(micPath, mPath);
        env->ReleaseStringUTFChars(internalPath, iPath);
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
    
    if (android_get_device_api_level() >= 28) {
        AMediaFormat_setInt32(format, "bitrate-mode", 2); // CBR
    }
    
    int64_t repeatUs = 1000000LL / fps;
    AMediaFormat_setInt64(format, "repeat-previous-frame-after", repeatUs);

    gCtx->videoCodec = AMediaCodec_createEncoderByType(mime);
    media_status_t status = AMediaCodec_configure(gCtx->videoCodec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    if (status != AMEDIA_OK) {
        LOGE("Failed to configure video codec: %d", status);
        AMediaFormat_delete(format);
        return JNI_FALSE;
    }
    AMediaCodec_createInputSurface(gCtx->videoCodec, &gCtx->inputSurface);
    AMediaFormat_delete(format);

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

    gCtx->isRecording = true;
    gCtx->videoPollingThread = std::thread(pollVideo, gCtx);

    return JNI_TRUE;
}

// Keeping this for compatibility, but it redirects to generic write
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_writeAudioBuffer(
        JNIEnv* env, jobject, jshortArray data, jint length) {
    // Deprecated path - mapped to mic writer for fallback
    if (!gCtx || !gCtx->isRecording.load() || gCtx->isPaused.load()) return JNI_FALSE;

    jshort* samples = env->GetShortArrayElements(data, nullptr);
    if (gCtx->micWriter.isOpen()) {
        gCtx->micWriter.write(samples, length);
    }
    env->ReleaseShortArrayElements(data, samples, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_writeAudioSamples(
        JNIEnv* env, jobject thiz, jshortArray micData, jshortArray internalData, jint length) {
    if (!gCtx || !gCtx->isRecording.load() || gCtx->isPaused.load()) return JNI_FALSE;

    jsize micLen = env->GetArrayLength(micData);
    jsize intLen = env->GetArrayLength(internalData);

    jshort* micSamples = (micLen > 0) ? env->GetShortArrayElements(micData, nullptr) : nullptr;
    jshort* internalSamples = (intLen > 0) ? env->GetShortArrayElements(internalData, nullptr) : nullptr;

    // 1. Write Raw Data (Only if array has enough data)
    if (gCtx->micWriter.isOpen() && micSamples && micLen >= length) {
        gCtx->micWriter.write(micSamples, length);
    }
    if (gCtx->internalWriter.isOpen() && internalSamples && intLen >= length) {
        gCtx->internalWriter.write(internalSamples, length);
    }

    // 2. Calculate Levels for UI
    float micSumSquares = 0.0f;
    float micPeak = 0.0f;
    float intSumSquares = 0.0f;
    float intPeak = 0.0f;

    if (micSamples && micLen >= length) {
        for (int i = 0; i < length; i += 4) {
            float s = std::abs(static_cast<float>(micSamples[i]));
            micSumSquares += s * s;
            if (s > micPeak) micPeak = s;
        }
        gCtx->micRmsLevel.store(std::sqrt(micSumSquares / (length / 4.0f + 1)));
        gCtx->micPeakLevel.store(micPeak);
    }

    if (internalSamples && intLen >= length) {
        for (int i = 0; i < length; i += 4) {
            float s = std::abs(static_cast<float>(internalSamples[i]));
            intSumSquares += s * s;
            if (s > intPeak) intPeak = s;
        }
        gCtx->internalRmsLevel.store(std::sqrt(intSumSquares / (length / 4.0f + 1)));
        gCtx->internalPeakLevel.store(intPeak);
    }

    if (micSamples) env->ReleaseShortArrayElements(micData, micSamples, JNI_ABORT);
    if (internalSamples) env->ReleaseShortArrayElements(internalData, internalSamples, JNI_ABORT);
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

extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_setMixRatio(
        JNIEnv* env, jobject, jfloat internalRatio, jfloat micRatio) {
    // No-op in raw capture mode - mixing happens offline
    // We could store it for metadata, but for now ignoring
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

    // Close writers
    gCtx->micWriter.close();
    gCtx->internalWriter.close();

    // Stop Codecs
    if (gCtx->videoCodec) {
        AMediaCodec_stop(gCtx->videoCodec);
        AMediaCodec_delete(gCtx->videoCodec);
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

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_processRecording(
        JNIEnv* env,
        jobject /* this */,
        jint videoFd,
        jint width,
        jint height,
        jstring jMicPath,
        jstring jInternalPath,
        jstring jOutputPath,
        jstring jModelPath,
        jboolean enableBleedReduction,
        jboolean enableNoiseReduction,
        jfloat micGain,
        jfloat internalGain,
        jboolean exportMicOnly,
        jboolean exportInternalOnly,
        jint audioSampleRate,
        jint audioBitrate
) {
    const char* micPath = env->GetStringUTFChars(jMicPath, nullptr);
    const char* internalPath = env->GetStringUTFChars(jInternalPath, nullptr);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, nullptr);
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    bokbok::PostProcessor::Config config;
    config.micPath = micPath;
    config.internalPath = internalPath;
    config.videoFd = videoFd;
    config.finalOutputPath = outputPath;
    config.modelPath = modelPath;
    config.enableBleedReduction = enableBleedReduction;
    config.enableNoiseReduction = enableNoiseReduction;
    config.micGain = micGain;
    config.internalGain = internalGain;
    config.exportMicOnly = exportMicOnly;
    config.exportInternalOnly = exportInternalOnly;
    config.sampleRate = audioSampleRate;
    config.audioBitrate = audioBitrate;

    bokbok::PostProcessor processor;
    processor.setOnProgress([env](float progress, const std::string& status) {
        // Callback logic to Java could be re-implemented here if needed
    });
    
    // Explicitly set numChannels to 1 for current Mono mode
    bokbok::PostProcessor::Config configWithChannels = config;
    configWithChannels.numChannels = 1;

    bool success = processor.process(configWithChannels);

    env->ReleaseStringUTFChars(jMicPath, micPath);
    env->ReleaseStringUTFChars(jInternalPath, internalPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    
    return success ? JNI_TRUE : JNI_FALSE;
}
