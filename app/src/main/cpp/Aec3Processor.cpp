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
    webrtc::EchoCanceller3Config config;
    
    // --- STABILITY FIRST CONFIGURATION ---
    
    // 1. FILTER LENGTHS (Must be consistent to avoid crashes)
    config.filter.main.length_blocks = 32; 
    config.filter.shadow.length_blocks = 32;
    config.filter.main_initial.length_blocks = 32; 
    config.filter.shadow_initial.length_blocks = 32;
    
    // 2. DELAY (Force 0-sync for post-processing alignment)
    config.delay.default_delay = 0;
    config.delay.use_external_delay_estimator = false; 
    config.delay.delay_selection_thresholds.initial = 5;

    // 3. ECHO MODEL (Minimize windows to prevent startup underruns)
    config.echo_model.render_pre_window_size = 1;
    config.echo_model.render_post_window_size = 1;
    config.echo_model.min_noise_floor_power = 100.f; 
    
    // 4. SUPPRESSOR (Transparency over raw export)
    // High thresholds ensure zero gating of the user's voice.
    config.suppressor.normal_tuning.mask_lf.enr_transparent = 2.0f; 
    config.suppressor.normal_tuning.mask_lf.enr_suppress = 3.0f;   
    
    // 5. ECHO REMOVAL CONTROL
    config.echo_removal_control.linear_and_stable_echo_path = true;
    config.echo_removal_control.has_clock_drift = false;

    // 6. PROCESSING MODE
    config.filter.use_linear_filter = true;
    config.filter.export_linear_aec_output = false; // Reverted for stability

    config.ep_strength.default_gain = 1.0f;
    config.ep_strength.echo_can_saturate = false; 

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

        // Standard ProcessCapture. With our "Huge Threshold" config, 
        // this output will be the pure full-bandwidth linear subtraction.
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
