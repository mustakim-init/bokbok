#ifndef RNNOISE_PROCESSOR_H
#define RNNOISE_PROCESSOR_H

#include <memory>
#include <vector>
#include <cstdint>

// Forward declare the RNNoise structure to avoid header conflicts
extern "C" {
    struct DenoiseState;
    DenoiseState *rnnoise_create(void *model);
    void rnnoise_destroy(DenoiseState *st);
    float rnnoise_process_frame(DenoiseState *st, float *out, const float *in);
}

namespace bokbok {

/**
 * C++ wrapper for RNNoise neural network-based noise reduction.
 * 
 * RNNoise operates on 10ms frames at 48kHz (480 samples MONO).
 * For stereo audio, we process each channel independently.
 */
class RnNoiseProcessor {
public:
    /**
     * @param sampleRate Must be 48000Hz (RNNoise only supports this)
     * @param numChannels 1 for mono, 2 for stereo
     */
    RnNoiseProcessor(int sampleRate, int numChannels);
    ~RnNoiseProcessor();
    
    // Non-copyable
    RnNoiseProcessor(const RnNoiseProcessor&) = delete;
    RnNoiseProcessor& operator=(const RnNoiseProcessor&) = delete;

    /**
     * Process audio through RNNoise.
     * 
     * @param data       PCM 16-bit samples (interleaved if stereo)
     * @param numSamples Total number of samples (for stereo, this is frames * 2)
     * @return           VAD probability (0.0 = silence/noise, 1.0 = voice)
     *                   Returns average across all processed frames
     */
    float Process(int16_t* data, size_t numSamples);
    
    /**
     * Get the last VAD (Voice Activity Detection) probability.
     * Useful for downstream noise gating decisions.
     */
    float GetLastVadProbability() const { return mLastVad; }

private:
    static constexpr int RNNOISE_SAMPLE_RATE = 48000;
    static constexpr int RNNOISE_FRAME_SIZE = 480;  // 10ms @ 48kHz
    
    int mSampleRate;
    int mNumChannels;
    float mLastVad = 0.0f;
    
    // One DenoiseState per channel (RNNoise is mono-only internally)
    std::vector<DenoiseState*> mStates;
    
    // Residue buffers for incomplete frames (per channel)
    std::vector<std::vector<float>> mResidue;
    
    // Temporary buffers for float conversion
    std::vector<float> mInputBuffer;
    std::vector<float> mOutputBuffer;
};

} // namespace bokbok

#endif // RNNOISE_PROCESSOR_H
