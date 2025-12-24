#include <jni.h>
#include <string>
#include <android/log.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaFormat.h>
#include <android/native_window_jni.h>
#include <thread>
#include <atomic>
#include <mutex>
#include <fcntl.h>
#include <unistd.h>
#include <cerrno>
#include <cstring>
#include <vector>
#include <queue>

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

    // Track state for monotonicity
    int64_t lastVideoPts = -1;
    int64_t lastAudioPts = -1;

    // Audio sample tracking
    std::atomic<int64_t> audioStartTime{-1};
    std::atomic<int64_t> totalAudioFrames{0};
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
        adjustedPts = *lastPtsRef + 1; // Minimal increment
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
        JNIEnv* env, jobject, jint width, jint height, jint bitrate, jint fps, jboolean useHevc, jstring outputPath) {
    
    std::lock_guard<std::mutex> lock(gGlobalMutex);
    
    // Cleanup existing logic remains the same (omitted for brevity, assume similar cleanup)
    if (gCtx) {
        Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(env, nullptr); // Reuse stop logic
    }
    
    gCtx = new RecorderContext();
    gCtx->expectedTracks = 2; // Expect Video + Audio now

    const char* path = env->GetStringUTFChars(outputPath, nullptr);
    int fd = open(path, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(outputPath, path);
    
    if (fd < 0) return JNI_FALSE;
    gCtx->muxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    close(fd);
    if (!gCtx->muxer) return JNI_FALSE;

    // --- VIDEO SETUP ---
    const char* mime = useHevc ? "video/hevc" : "video/avc";
    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, mime);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_WIDTH, width);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_HEIGHT, height);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, bitrate);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_FRAME_RATE, fps);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_I_FRAME_INTERVAL, 1);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_COLOR_FORMAT, 0x7F000789);

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
 
     if (gCtx->inputSurface) ANativeWindow_release(gCtx->inputSurface);
     
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
// Stub for audio config (legacy)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_configureAudio(
        JNIEnv*, jobject, jint, jint, jboolean, jboolean) {
    return JNI_TRUE;
}
extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_stopAudio(JNIEnv*, jobject) {}
