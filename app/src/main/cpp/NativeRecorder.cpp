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
    

    std::atomic<bool> audioEnabled{false};
};

// static RecorderContext* gCtx = nullptr; // REMOVED GLOBAL
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
    return env->NewStringUTF("Native Recorder Engine v4.0 (Handle-Based Mode)");
}

extern "C" JNIEXPORT jlong JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_nativeSetup(
        JNIEnv* env, jobject, 
        jint width, jint height, jint bitrate, jint fps, jboolean useHevc,
        jboolean audioEnabled, 
        jstring videoPath, jstring micPath, jstring internalPath,
        jint audioSampleRate, jint audioBitrate) {
    
    // We don't check for global here anymore as each setup creates a new context
    RecorderContext* ctx = new RecorderContext();
    ctx->expectedTracks = 1; 
    ctx->audioEnabled = audioEnabled;

    // Video Output
    const char* vPath = env->GetStringUTFChars(videoPath, nullptr);
    ctx->fd = open(vPath, O_CREAT | O_WRONLY | O_TRUNC, 0644);
    env->ReleaseStringUTFChars(videoPath, vPath);
    
    if (ctx->fd < 0) {
        LOGE("Failed to open video file");
        delete ctx;
        return 0;
    }

    ctx->muxer = AMediaMuxer_new(ctx->fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    if (!ctx->muxer) {
        close(ctx->fd);
        delete ctx;
        return 0;
    }

    // Audio Outputs (Raw PCM)
    if (audioEnabled) {
        const char* mPath = env->GetStringUTFChars(micPath, nullptr);
        const char* iPath = env->GetStringUTFChars(internalPath, nullptr);
        
        bool mOk = ctx->micWriter.open(mPath);
        bool iOk = ctx->internalWriter.open(iPath);
        
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

    ctx->videoCodec = AMediaCodec_createEncoderByType(mime);
    media_status_t status = AMediaCodec_configure(ctx->videoCodec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    if (status != AMEDIA_OK) {
        LOGE("Failed to configure video codec: %d", status);
        AMediaFormat_delete(format);
        AMediaMuxer_delete(ctx->muxer);
        close(ctx->fd);
        delete ctx;
        return 0;
    }
    AMediaCodec_createInputSurface(ctx->videoCodec, &ctx->inputSurface);
    AMediaFormat_delete(format);

    return reinterpret_cast<jlong>(ctx);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_getInputSurface(JNIEnv* env, jobject, jlong handle) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (!ctx || !ctx->inputSurface) return nullptr;
    return ANativeWindow_toSurface(env, ctx->inputSurface);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_start(JNIEnv*, jobject, jlong handle) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (!ctx || !ctx->videoCodec) return JNI_FALSE;

    AMediaCodec_start(ctx->videoCodec);

    ctx->isRecording = true;
    ctx->videoPollingThread = std::thread(pollVideo, ctx);

    return JNI_TRUE;
}


extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_writeAudioBuffer(
        JNIEnv* env, jobject, jlong handle, jshortArray data, jint length) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (!ctx || !ctx->isRecording.load() || ctx->isPaused.load()) return JNI_FALSE;

    jshort* samples = env->GetShortArrayElements(data, nullptr);
    if (ctx->micWriter.isOpen()) {
        ctx->micWriter.write(samples, length);
    }
    env->ReleaseShortArrayElements(data, samples, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_writeAudioSamples(
        JNIEnv* env, jobject thiz, jlong handle, jshortArray micData, jshortArray internalData, jint length) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (!ctx || !ctx->isRecording.load() || ctx->isPaused.load()) return JNI_FALSE;

    jsize micLen = env->GetArrayLength(micData);
    jsize intLen = env->GetArrayLength(internalData);

    jshort* micSamples = (micLen > 0) ? env->GetShortArrayElements(micData, nullptr) : nullptr;
    jshort* internalSamples = (intLen > 0) ? env->GetShortArrayElements(internalData, nullptr) : nullptr;

    bool micWrote = false;
    bool intWrote = false;

    if (ctx->micWriter.isOpen() && micSamples && micLen >= length) {
        ctx->micWriter.write(micSamples, length);
        micWrote = true;
    }
    if (ctx->internalWriter.isOpen() && internalSamples && intLen >= length) {
        ctx->internalWriter.write(internalSamples, length);
        intWrote = true;
    }

    if (micSamples) env->ReleaseShortArrayElements(micData, micSamples, JNI_ABORT);
    if (internalSamples) env->ReleaseShortArrayElements(internalData, internalSamples, JNI_ABORT);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jfloatArray JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_getAudioLevels(JNIEnv* env, jobject, jlong handle) {
    jfloatArray result = env->NewFloatArray(4);
    float zeros[4] = {0, 0, 0, 0};
    env->SetFloatArrayRegion(result, 0, 4, zeros);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_setMixRatio(
        JNIEnv* env, jobject, jlong handle, jfloat internalRatio, jfloat micRatio) {
    // No-op
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_pause(JNIEnv*, jobject, jlong handle) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (ctx) { 
        ctx->isPaused = true;
        ctx->pauseStartTime = getCurrentTimeUs();
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_resume(JNIEnv*, jobject, jlong handle) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (ctx && ctx->isPaused) {
        ctx->totalPauseDuration += (getCurrentTimeUs() - ctx->pauseStartTime);
        ctx->isPaused = false;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(JNIEnv*, jobject, jlong handle) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (!ctx) return JNI_FALSE;

    ctx->isRecording = false;
    
    if (ctx->videoPollingThread.joinable()) ctx->videoPollingThread.join();

    // Close writers
    ctx->micWriter.close();
    ctx->internalWriter.close();

    // Stop Codecs
    if (ctx->videoCodec) {
        AMediaCodec_stop(ctx->videoCodec);
        AMediaCodec_delete(ctx->videoCodec);
        ctx->videoCodec = nullptr;
    }
 
    // Stop Muxer
    if (ctx->muxer) {
        if (ctx->muxerStarted) AMediaMuxer_stop(ctx->muxer);
        AMediaMuxer_delete(ctx->muxer);
        ctx->muxer = nullptr;
    }
    
    if (ctx->fd >= 0) {
        close(ctx->fd);
        ctx->fd = -1;
    }

    if (ctx->inputSurface) {
        ANativeWindow_release(ctx->inputSurface);
        ctx->inputSurface = nullptr;
    }
    
    std::lock_guard<std::mutex> muxLock(ctx->muxerMutex);
    while (!ctx->packetQueue.empty()) {
        Packet* p = ctx->packetQueue.front();
        ctx->packetQueue.pop();
        delete[] p->data;
        delete p;
    }

    delete ctx;
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_captureScreenshot(JNIEnv*, jobject, jlong handle) {
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_nativeRelease(JNIEnv* env, jobject thiz, jlong handle) {
    RecorderContext* ctx = reinterpret_cast<RecorderContext*>(handle);
    if (ctx) {
        // If release is called without stop, we should clean up
        if (ctx->isRecording.load()) {
            Java_com_mustakim_bokbok_data_service_NativeRecorder_stop(env, thiz, handle);
        } else {
            delete ctx;
        }
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_processRecording(
        JNIEnv* env,
        jobject thiz,
        jint videoFd,
        jint width,
        jint height,
        jstring jMicPath,
        jstring jInternalPath,
        jstring jOutputPath,
        jstring jMicExportPath,
        jstring jInternalExportPath,
        jstring jModelPath,
        jboolean enableBleedReduction,
        jboolean enableNoiseReduction,
        jboolean enableStudioMaster,
        jfloat micGain,
        jfloat internalGain,
        jboolean exportMicOnly,
        jboolean exportInternalOnly,
        jboolean isMono,
        jint audioSampleRate,
        jint audioBitrate
) {
    // 1. Prepare JNI for Progress Callback
    jclass clazz = env->GetObjectClass(thiz);
    jmethodID progressMethod = env->GetMethodID(clazz, "onProcessProgress", "(FLjava/lang/String;)V");

    const char* micPath = env->GetStringUTFChars(jMicPath, nullptr);
    const char* internalPath = env->GetStringUTFChars(jInternalPath, nullptr);
    const char* outputPath = env->GetStringUTFChars(jOutputPath, nullptr);
    const char* micExportPath = env->GetStringUTFChars(jMicExportPath, nullptr);
    const char* internalExportPath = env->GetStringUTFChars(jInternalExportPath, nullptr);
    const char* modelPath = env->GetStringUTFChars(jModelPath, nullptr);

    bokbok::PostProcessor::Config config;
    config.micPath = micPath;
    config.internalPath = internalPath;
    config.videoFd = videoFd;
    config.finalOutputPath = outputPath;
    config.micExportPath = micExportPath;
    config.internalExportPath = internalExportPath;
    config.modelPath = modelPath;
    config.enableBleedReduction = enableBleedReduction;
    config.enableNoiseReduction = enableNoiseReduction;
    config.enableStudioMaster = enableStudioMaster;
    config.micGain = micGain;
    config.internalGain = internalGain;
    config.exportMicOnly = exportMicOnly;
    config.exportInternalOnly = exportInternalOnly;
    config.internalChannels = isMono ? 1 : 2;
    config.numChannels = 2; // Stereo output
    config.sampleRate = audioSampleRate;
    config.audioBitrate = audioBitrate;

    bokbok::PostProcessor processor;
    
    // 2. Set the progress callback
    if (progressMethod) {
        processor.setOnProgress([env, thiz, progressMethod](float progress, const std::string& status) {
            jstring msg = env->NewStringUTF(status.c_str());
            env->CallVoidMethod(thiz, progressMethod, (jfloat)progress, msg);
            env->DeleteLocalRef(msg);
        });
    }
    
    bool success = processor.process(config);

    env->ReleaseStringUTFChars(jMicPath, micPath);
    env->ReleaseStringUTFChars(jInternalPath, internalPath);
    env->ReleaseStringUTFChars(jOutputPath, outputPath);
    env->ReleaseStringUTFChars(jMicExportPath, micExportPath);
    env->ReleaseStringUTFChars(jInternalExportPath, internalExportPath);
    env->ReleaseStringUTFChars(jModelPath, modelPath);
    
    return success ? JNI_TRUE : JNI_FALSE;
}
