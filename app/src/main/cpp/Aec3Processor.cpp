#include "Aec3Processor.h"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace bokbok {

Aec3Processor::Aec3Processor(int sampleRate, int numChannels)
    : mSampleRate(sampleRate)
    , mNumChannels(numChannels)
    , mBlockSize(sampleRate / 100) // 10ms block (e.g., 480 for 48kHz)
{
    webrtc::EchoCanceller3Config config = webrtc::EchoCanceller3::CreateDefaultConfig(numChannels, numChannels);
    mAec3 = std::make_unique<webrtc::EchoCanceller3>(config, sampleRate, numChannels, numChannels);
    
    mRenderBuffer = std::make_unique<webrtc::AudioBuffer>(sampleRate, numChannels, sampleRate, numChannels, sampleRate, numChannels);
    mCaptureBuffer = std::make_unique<webrtc::AudioBuffer>(sampleRate, numChannels, sampleRate, numChannels, sampleRate, numChannels);

    mInternalResidue.reserve(mBlockSize * numChannels);
    mMicResidue.reserve(mBlockSize * numChannels);
}

Aec3Processor::~Aec3Processor() = default;

void Aec3Processor::AnalyzeInternal(const int16_t* data, size_t numSamples) {
    if (numSamples == 0) return;
    
    // Append new data to residue
    mInternalResidue.insert(mInternalResidue.end(), data, data + numSamples);
    
    size_t samplesPerBlock = mBlockSize * mNumChannels;
    
    // Process all full 10ms blocks
    while (mInternalResidue.size() >= samplesPerBlock) {
        webrtc::AudioFrame frame;
        frame.UpdateFrame(0, mInternalResidue.data(), mBlockSize, mSampleRate, 
                        webrtc::AudioFrame::kNormalSpeech, webrtc::AudioFrame::kVadUnknown, mNumChannels);
        
        mRenderBuffer->CopyFrom(&frame);
        if (mSampleRate > 16000) {
            mRenderBuffer->SplitIntoFrequencyBands();
        }
        
        mAec3->AnalyzeRender(mRenderBuffer.get());
        
        if (mSampleRate > 16000) {
            mRenderBuffer->MergeFrequencyBands();
        }

        // Remove processed block from residue
        mInternalResidue.erase(mInternalResidue.begin(), mInternalResidue.begin() + samplesPerBlock);
    }
}

void Aec3Processor::ProcessMic(int16_t* data, size_t numSamples) {
    if (numSamples == 0) return;

    // Append new data to residue
    mMicResidue.insert(mMicResidue.end(), data, data + numSamples);
    
    size_t samplesPerBlock = mBlockSize * mNumChannels;
    size_t processedSamples = 0;

    // Process all full 10ms blocks
    while (mMicResidue.size() >= samplesPerBlock) {
        webrtc::AudioFrame frame;
        frame.UpdateFrame(0, mMicResidue.data(), mBlockSize, mSampleRate, 
                        webrtc::AudioFrame::kNormalSpeech, webrtc::AudioFrame::kVadUnknown, mNumChannels);
        
        mCaptureBuffer->CopyFrom(&frame);
        mAec3->AnalyzeCapture(mCaptureBuffer.get());
        
        if (mSampleRate > 16000) {
            mCaptureBuffer->SplitIntoFrequencyBands();
        }

        mAec3->ProcessCapture(mCaptureBuffer.get(), false);

        if (mSampleRate > 16000) {
            mCaptureBuffer->MergeFrequencyBands();
        }
        
        mCaptureBuffer->CopyTo(&frame);
        
        // Copy processed block back to the original data buffer if there's space
        if (processedSamples + samplesPerBlock <= numSamples) {
            std::memcpy(data + processedSamples, frame.data(), samplesPerBlock * sizeof(int16_t));
        }
        
        processedSamples += samplesPerBlock;
        mMicResidue.erase(mMicResidue.begin(), mMicResidue.begin() + samplesPerBlock);
    }

    // Note: If some samples remain in mMicResidue, they are not written back to 'data' yet.
    // This creates a 10ms latency in the mic path which is normal for AEC.
}

} // namespace bokbok
