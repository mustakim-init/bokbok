#ifndef AEC3_PROCESSOR_H
#define AEC3_PROCESSOR_H

#include <memory>
#include <vector>
#include <cstdint>
#include "webrtc/audio_processing/aec3/echo_canceller3.h"
#include "webrtc/audio_processing/audio_buffer.h"

namespace bokbok {

/**
 * Wrapper for WebRTC AEC3 (Echo Canceller 3).
 * Handles near-end (mic) and far-end (internal audio) streams to perform echo cancellation.
 */
class Aec3Processor {
public:
    Aec3Processor(int sampleRate, int numChannels);
    ~Aec3Processor();

    /**
     * Buffer internal audio (far-end) as reference signal for AEC.
     * Should be called before ProcessMic.
     */
    void AnalyzeInternal(const int16_t* data, size_t numSamples);

    /**
     * Process microphone audio (near-end) to remove echo.
     * numSamples must match internal buffer size.
     */
    void ProcessMic(int16_t* data, size_t numSamples);

private:
    int mSampleRate;
    int mNumChannels;
    size_t mBlockSize; // 10ms block size

    std::unique_ptr<webrtc::EchoCanceller3> mAec3;
    
    // WebRTC internal buffers
    std::unique_ptr<webrtc::AudioBuffer> mRenderBuffer;
    std::unique_ptr<webrtc::AudioBuffer> mCaptureBuffer;

    // Residue buffers for handling variable JNI chunk sizes
    std::vector<int16_t> mInternalResidue;
    std::vector<int16_t> mMicResidue;
    
    void ProcessBlock(int16_t* micData, const int16_t* internalData);
};

} // namespace bokbok

#endif // AEC3_PROCESSOR_H
