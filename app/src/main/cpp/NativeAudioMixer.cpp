#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>
#include <vector>

#define LOG_TAG "NativeAudioMixer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

class AudioMixer : public oboe::AudioStreamDataCallback {
public:
    AudioMixer() = default;

    oboe::DataCallbackResult onAudioReady(oboe::AudioStream *audioStream, void *audioData, int32_t numFrames) override {
        // This is where real-time mixing happens.
        // We will pull from Mic and Internal Audio buffers and mix them here.
        // For now, it's a placeholder for the high-performance pipeline.
        return oboe::DataCallbackResult::Continue;
    }

    void setup(int sampleRate, int channelCount, bool includeMic, bool includeInternal) {
        oboe::AudioStreamBuilder builder;
        builder.setDirection(oboe::Direction::Input)
               ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
               ->setSharingMode(oboe::SharingMode::Exclusive)
               ->setFormat(oboe::AudioFormat::I16)
               ->setChannelCount(channelCount)
               ->setSampleRate(sampleRate)
               ->setDataCallback(this);

        oboe::Result result = builder.openStream(mStream);
        if (result == oboe::Result::OK) {
            mStream->requestStart();
            LOGI("Audio Mixer Stream Started Successfully");
        }
    }

    void stop() {
        if (mStream) {
            mStream->requestStop();
            mStream->close();
            mStream.reset();
        }
    }

private:
    std::shared_ptr<oboe::AudioStream> mStream;
};

static AudioMixer* gMixer = nullptr;

void stopAudioMixer() {
    if (gMixer) {
        gMixer->stop();
        delete gMixer;
        gMixer = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_configureAudio(
        JNIEnv*, jobject, jint sampleRate, jint channelCount, jboolean mic, jboolean internal) {
    stopAudioMixer(); // Cleanup if already exists
    gMixer = new AudioMixer();
    gMixer->setup(sampleRate, channelCount, mic, internal);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_mustakim_bokbok_data_service_NativeRecorder_stopAudio(JNIEnv*, jobject) {
    stopAudioMixer();
}
