#include "RnNoiseProcessor.h"
#include <cmath>
#include <cstring>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "RnNoiseProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace bokbok {

RnNoiseProcessor::RnNoiseProcessor(int sampleRate, int numChannels)
    : mSampleRate(sampleRate)
    , mNumChannels(numChannels)
{
    if (sampleRate != RNNOISE_SAMPLE_RATE) {
        LOGW("RNNoise expects 48000Hz, got %d. Audio quality may be affected.", sampleRate);
    }
    
    // Create one DenoiseState per channel
    mStates.resize(numChannels);
    mResidue.resize(numChannels);
    
    for (int ch = 0; ch < numChannels; ch++) {
        mStates[ch] = rnnoise_create(nullptr);
        if (!mStates[ch]) {
            LOGE("Failed to create RNNoise state for channel %d", ch);
        }
        mResidue[ch].reserve(RNNOISE_FRAME_SIZE);
    }
    
    // Allocate temp buffers for single frame processing
    mInputBuffer.resize(RNNOISE_FRAME_SIZE);
    mOutputBuffer.resize(RNNOISE_FRAME_SIZE);
    
    LOGI("RnNoiseProcessor initialized: %dHz, %d channels", sampleRate, numChannels);
}

RnNoiseProcessor::~RnNoiseProcessor() {
    for (auto* state : mStates) {
        if (state) {
            rnnoise_destroy(state);
        }
    }
    LOGI("RnNoiseProcessor destroyed");
}

float RnNoiseProcessor::Process(int16_t* data, size_t numSamples) {
    if (numSamples == 0 || data == nullptr) return 0.0f;
    
    const int numChannels = mNumChannels;
    const size_t framesPerChannel = numSamples / numChannels;
    
    float totalVad = 0.0f;
    int vadCount = 0;
    
    // Temporary storage for deinterleaved processing (max 2 channels)
    std::vector<float> channelData[2];
    std::vector<float> processedData[2];
    
    for (int ch = 0; ch < numChannels && ch < 2; ch++) {
        channelData[ch].reserve(framesPerChannel + RNNOISE_FRAME_SIZE);
        processedData[ch].reserve(framesPerChannel + RNNOISE_FRAME_SIZE);
        
        // Add residue from previous call
        channelData[ch] = mResidue[ch];
        
        // Deinterleave current samples
        for (size_t i = 0; i < framesPerChannel; i++) {
            channelData[ch].push_back(static_cast<float>(data[i * numChannels + ch]));
        }
        
        // Process complete frames
        const auto& input = channelData[ch];
        auto& output = processedData[ch];
        size_t processedPos = 0;
        
        while (processedPos + RNNOISE_FRAME_SIZE <= input.size()) {
            std::copy(input.begin() + processedPos, 
                     input.begin() + processedPos + RNNOISE_FRAME_SIZE, 
                     mInputBuffer.begin());
            
            float vad = rnnoise_process_frame(mStates[ch], mOutputBuffer.data(), mInputBuffer.data());
            totalVad += vad;
            vadCount++;
            
            output.insert(output.end(), mOutputBuffer.begin(), mOutputBuffer.end());
            processedPos += RNNOISE_FRAME_SIZE;
        }
        
        // Save remaining samples as residue
        mResidue[ch].clear();
        mResidue[ch].insert(mResidue[ch].end(), input.begin() + processedPos, input.end());
    }
    
    // Interleave processed data back to original buffer
    // Only write back samples that correspond to this input batch
    size_t samplesToWrite = std::min(processedData[0].size(), framesPerChannel);
    
    for (size_t i = 0; i < samplesToWrite; i++) {
        for (int ch = 0; ch < numChannels && ch < 2; ch++) {
            if (i < processedData[ch].size()) {
                // Clamp and convert back to int16
                float val = processedData[ch][i];
                val = std::max(-32767.0f, std::min(32767.0f, val));
                data[i * numChannels + ch] = static_cast<int16_t>(val);
            }
        }
    }
    
    mLastVad = (vadCount > 0) ? (totalVad / vadCount) : 0.0f;
    return mLastVad;
}

} // namespace bokbok
