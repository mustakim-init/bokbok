#define _USE_MATH_DEFINES
#include <cmath>
#include "DeepFilterNet.h"
#include <onnxruntime_cxx_api.h>
#include <android/log.h>
#include <fstream>
#include <cmath>
#include <algorithm>
#include <cstring>

#define LOG_TAG "DeepFilterNet"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace bokbok {

static float freq2erb(float freq) {
    return 21.4f * log10f(1.0f + freq / 229.0f);
}

DeepFilterNet::DeepFilterNet() {
    try {
        env_ = std::make_unique<Ort::Env>(ORT_LOGGING_LEVEL_WARNING, "DeepFilterNet");
        memoryInfo_ = std::make_unique<Ort::MemoryInfo>(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault));
    } catch (const std::exception& e) {
        LOGE("Failed to init ONNX Runtime: %s", e.what());
    }
}

DeepFilterNet::~DeepFilterNet() {
    if (fftCfg_) opus_fft_free(fftCfg_, 0);
    if (ifftCfg_) opus_fft_free(ifftCfg_, 0);
}

bool DeepFilterNet::init(const std::string& modelDir, int sampleRate) {
    if (!env_) return false;
    sampleRate_ = sampleRate;

    try {
        std::string encPath = modelDir + "/enc.onnx";
        std::string erbPath = modelDir + "/erb_dec.onnx";
        std::string dfPath = modelDir + "/df_dec.onnx";

        Ort::SessionOptions sessionOptions;
        sessionOptions.SetIntraOpNumThreads(2);
        sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

        sessionEnc_ = std::make_unique<Ort::Session>(*env_, encPath.c_str(), sessionOptions);
        sessionErbDec_ = std::make_unique<Ort::Session>(*env_, erbPath.c_str(), sessionOptions);
        sessionDfDec_ = std::make_unique<Ort::Session>(*env_, dfPath.c_str(), sessionOptions);

        // Resolve input/output names
        Ort::AllocatorWithDefaultOptions allocator;
        auto resolveNames = [&](Ort::Session* sess, std::vector<std::string>& in, std::vector<std::string>& out) {
            in.clear();
            for (size_t i = 0; i < sess->GetInputCount(); i++)
                in.push_back(sess->GetInputNameAllocated(i, allocator).get());
            out.clear();
            for (size_t i = 0; i < sess->GetOutputCount(); i++)
                out.push_back(sess->GetOutputNameAllocated(i, allocator).get());
        };

        resolveNames(sessionEnc_.get(), encInNames_, encOutNames_);
        resolveNames(sessionErbDec_.get(), erbInNames_, erbOutNames_);
        resolveNames(sessionDfDec_.get(), dfInNames_, dfOutNames_);

        initWindows();
        initErb();
        
        if (fftCfg_) opus_fft_free(fftCfg_, 0);
        if (ifftCfg_) opus_fft_free(ifftCfg_, 0);
        fftCfg_ = opus_fft_alloc(fftSize_, nullptr, nullptr, 0);
        ifftCfg_ = opus_fft_alloc(fftSize_, nullptr, nullptr, 0);

        // Initialize Normalization States (v3)
        normAlpha_ = expf(-(float)hopSize_ / (float)sampleRate_ / 1.0f); // tau = 1.0
        
        meanNormState_.assign(nbErb_, 0.0f);
        for (int i = 0; i < nbErb_; i++) {
            meanNormState_[i] = -60.0f - (float)i * (30.0f / (nbErb_ - 1));
        }
        
        unitNormState_.assign(nbDf_, 0.001f);
        
        // SES States (Layer 1 - Spectral Coherence)
        int numFreqs = fftSize_ / 2 + 1;
        cohMicPower_.assign(numFreqs, 1e-10f);
        cohRefPower_.assign(numFreqs, 1e-10f);
        cohCrossPower_.assign(numFreqs, std::complex<float>(0, 0));
        sesAlpha_ = 0.9f; // Coherence smoothing (avg over ~100ms)

        // Initialize internal state buffers for ONNX recurrent states
        auto initStateBuffer = [&](Ort::Session* sess, const std::vector<std::string>& names, std::vector<std::vector<float>>& bufsOut, std::vector<std::vector<int64_t>>& shapesOut) {
            bufsOut.clear();
            shapesOut.clear();
            for (size_t i = 0; i < names.size(); i++) {
                if (names[i].find("state") != std::string::npos) {
                    auto info = sess->GetInputTypeInfo(i).GetTensorTypeAndShapeInfo();
                    auto shape = info.GetShape();
                    for (auto& d : shape) if (d < 0) d = 1;
                    size_t sz = 1;
                    for (auto d : shape) sz *= d;
                    
                    std::vector<float> buf(sz, 0.0f);
                    bufsOut.push_back(buf);
                    shapesOut.push_back(shape);
                }
            }
        };

        initStateBuffer(sessionEnc_.get(), encInNames_, statesEnc_, shapesEnc_);
        initStateBuffer(sessionErbDec_.get(), erbInNames_, statesErbDec_, shapesErb_);
        initStateBuffer(sessionDfDec_.get(), dfInNames_, statesDfDec_, shapesDf_);

        rollingSpec_.clear();
        isLoaded_ = true;
        LOGI("DeepFilterNet v3 integrated successfully.");
        return true;
    } catch (const std::exception& e) {
        LOGE("Failed to init DeepFilterNet: %s", e.what());
        return false;
    }
}

void DeepFilterNet::initWindows() {
    analysisWindow_.resize(fftSize_);
    synthesisWindow_.resize(fftSize_);
    float pi_2 = (float)M_PI / 2.0f;
    float n_inv = 1.0f / (fftSize_ / 2.0f);
    for (int i = 0; i < fftSize_; i++) {
        float s = sinf(pi_2 * (i + 0.5f) * n_inv);
        float val = sinf(pi_2 * s * s);
        analysisWindow_[i] = val;
        synthesisWindow_[i] = val;
    }
}

void DeepFilterNet::initErb() {
    erbFb_.assign(nbErb_, std::vector<float>(fftSize_ / 2 + 1, 0.0f));
    float maxErb = freq2erb(sampleRate_ / 2.0f);
    for (int k = 0; k <= fftSize_ / 2; k++) {
        float freq = (float)k * sampleRate_ / fftSize_;
        float erb = freq2erb(freq);
        float band = (erb / maxErb) * (nbErb_ - 1);
        int i0 = (int)floorf(band);
        int i1 = std::min(i0 + 1, nbErb_ - 1);
        float alpha = band - (float)i0;
        erbFb_[i0][k] = 1.0f - alpha;
        erbFb_[i1][k] = alpha;
    }
}

void DeepFilterNet::stft(const std::vector<float>& frame, std::vector<std::complex<float>>& spec) {
    if (!fftCfg_) return;
    std::vector<kiss_fft_cpx> fftIn(fftSize_);
    std::vector<kiss_fft_cpx> fftOut(fftSize_);
    for (int i = 0; i < fftSize_; i++) {
        fftIn[i].r = frame[i] * analysisWindow_[i];
        fftIn[i].i = 0.0f;
    }
    opus_fft_c(fftCfg_, fftIn.data(), fftOut.data());
    int numFreqs = fftSize_ / 2 + 1;
    spec.resize(numFreqs);
    for (int k = 0; k < numFreqs; k++) {
        spec[k] = std::complex<float>(fftOut[k].r, fftOut[k].i);
    }
}

void DeepFilterNet::istft(const std::vector<std::complex<float>>& spec, std::vector<float>& frame) {
    if (!ifftCfg_) return;
    int numFreqs = fftSize_ / 2 + 1;
    std::vector<kiss_fft_cpx> fftIn(fftSize_);
    std::vector<kiss_fft_cpx> fftOut(fftSize_);
    for (int k = 0; k < fftSize_; k++) {
        if (k < numFreqs) {
            fftIn[k].r = spec[k].real();
            fftIn[k].i = spec[k].imag();
        } else {
            int ref = fftSize_ - k;
            fftIn[k].r = spec[ref].real();
            fftIn[k].i = -spec[ref].imag();
        }
    }
    opus_ifft_c(ifftCfg_, fftIn.data(), fftOut.data());
    frame.resize(fftSize_);
    float norm = 1.0f / fftSize_;
    for (int i = 0; i < fftSize_; i++) {
        frame[i] = fftOut[i].r * synthesisWindow_[i] * norm;
    }
}

void DeepFilterNet::computeFeats(const std::vector<std::complex<float>>& spec, std::vector<float>& erbOut, std::vector<float>& specOut) {
    int numFreqs = fftSize_ / 2 + 1;
    std::vector<float> erb(nbErb_, 0.0f);
    for (int k = 0; k < numFreqs; k++) {
        float magSq = std::norm(spec[k]);
        for (int i = 0; i < nbErb_; i++) {
            erb[i] += erbFb_[i][k] * magSq;
        }
    }
    erbOut.resize(nbErb_);
    for (int i = 0; i < nbErb_; i++) {
        float logErb = log10f(erb[i] + 1e-10f) * 10.0f;
        meanNormState_[i] = logErb * (1.0f - normAlpha_) + meanNormState_[i] * normAlpha_;
        erbOut[i] = (logErb - meanNormState_[i]) / 40.0f;
    }
    specOut.resize(2 * nbDf_);
    for (int k = 0; k < nbDf_; k++) {
        float magSq = std::norm(spec[k]);
        unitNormState_[k] = magSq * (1.0f - normAlpha_) + unitNormState_[k] * normAlpha_;
        float norm = sqrtf(unitNormState_[k] + 1e-10f);
        specOut[k] = spec[k].real() / norm;
        specOut[nbDf_ + k] = spec[k].imag() / norm;
    }
}

void DeepFilterNet::applyBleedReduction(std::vector<std::complex<float>>& micSpec, const std::vector<std::complex<float>>& refSpec) {
    // Simplified Spectral Subtraction for Bleed Reduction
    // Goal: Reduce peaks in Mic that match peaks in Ref.
    // Gain[k] = (MicMag[k] - Beta * RefMag[k]) / MicMag[k]

    int numFreqs = fftSize_ / 2 + 1;
    const float beta = 1.0f; // Subtraction factor. 1.0 = moderate.
    const float minGain = 0.1f; // Max attenuation floor (-20dB)

    for (int k = 0; k < numFreqs; k++) {
        float micMag = std::abs(micSpec[k]);
        float refMag = std::abs(refSpec[k]);

        if (micMag < 1e-9f) continue;

        // Calculate attenuated magnitude
        // We only subtract if Ref is significant
        float newMag = micMag - (beta * refMag);
        
        // Calculate gain factor
        float gain = 1.0f;
        if (newMag < 0) {
            // Ref is louder than Mic at this freq -> Likely echo or pure game noise.
            // Aggressively attenuate, but hit floor.
            gain = minGain; 
        } else {
            gain = newMag / micMag;
            if (gain < minGain) gain = minGain;
        }

        // Apply gain
        micSpec[k] *= gain;
    }
    
    // Diagnostic Log (throttle to avoid spam)
    // static int logCounter = 0;
    // if (logCounter++ % 100 == 0) {
    //    // LOGI("BleedReduction: Active");
    // }
}

void DeepFilterNet::process(const int16_t* input, size_t count, int16_t* output, 
                            const int16_t* reference, bool enableBleed, bool enableAI) {
    if (!isLoaded_) {
        std::copy(input, input + count, output);
        return;
    }

    for (size_t i = 0; i < count; i++) {
        inputBuffer_.push_back(input[i] / 32768.0f);
        inputBufferRef_.push_back(reference ? reference[i] / 32768.0f : 0.0f);
    }

    if (outAccumulator_.size() < inputBuffer_.size() + fftSize_) {
        outAccumulator_.resize(inputBuffer_.size() + fftSize_, 0.0f);
    }

    size_t processedSamples = 0;
    while (processedSamples + hopSize_ <= inputBuffer_.size()) {
        if (processedSamples + fftSize_ > inputBuffer_.size()) break;

        std::vector<float> frame(inputBuffer_.begin() + processedSamples, inputBuffer_.begin() + processedSamples + fftSize_);
        std::vector<float> refFrame(inputBufferRef_.begin() + processedSamples, inputBufferRef_.begin() + processedSamples + fftSize_);
        
        float rawRms = 0.0f;
        for (auto f : frame) rawRms += f * f;
        rawRms = sqrtf(rawRms / (float)fftSize_);
        if (rawRms > 0.001f) {
            LOGI("DFN Raw Input Level: %.4f", rawRms);
        } else if (processedSamples % (hopSize_ * 10) == 0) { // Log occasionally even if silent
            LOGI("DFN Raw Input Level: SILENT (%.4f)", rawRms);
        }
        
        std::vector<std::complex<float>> micSpec;
        stft(frame, micSpec);
        
        if (enableBleed && reference) {
            std::vector<std::complex<float>> refSpec;
            stft(refFrame, refSpec);
            applyBleedReduction(micSpec, refSpec);
        }

        std::vector<std::complex<float>> enhancedSpec = micSpec;

        if (enableAI) {
            std::vector<float> erbFeat, specFeat;
            computeFeats(micSpec, erbFeat, specFeat);
            
            float featRms = 0.0f;
            for (auto f : erbFeat) featRms += f * f;
            featRms = sqrtf(featRms / (float)(erbFeat.size() + 1e-10f));

            float micMagRms = 0.0f;
            for (const auto& s : micSpec) micMagRms += std::norm(s);
            micMagRms = sqrtf(micMagRms / (float)(micSpec.size() + 1e-10f));

            float refRms = 0.0f;
            for (int i = 0; i < fftSize_; i++) refRms += refFrame[i] * refFrame[i];
            refRms = sqrtf(refRms / (float)(fftSize_ + 1e-10f));

            if (rawRms > 0.001f) {
                 LOGI("DFN Frame: MicRaw=%.4f MicMag=%.4f FeatRMS=%.4f RefRaw=%.4f", rawRms, micMagRms, featRms, refRms);
            }
            
            try {
                // 1. Encoder Task
                std::vector<Ort::Value> encInputs;
                std::vector<int64_t> erbDims = {1, 1, 1, (int64_t)nbErb_};
                std::vector<int64_t> specDims = {1, 2, 1, (int64_t)nbDf_};
                
                for (const auto& inName : encInNames_) {
                    if (inName == "feat_erb") {
                        encInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, erbFeat.data(), erbFeat.size(), erbDims.data(), erbDims.size()));
                    } else if (inName == "feat_spec") {
                        encInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, specFeat.data(), specFeat.size(), specDims.data(), specDims.size()));
                    } else if (inName.find("state") != std::string::npos) {
                        // Find which state index this is (ordered by appearance in input names)
                        size_t stateIdx = 0;
                        for (size_t k = 0; k < encInNames_.size(); k++) {
                            if (encInNames_[k] == inName) break;
                            if (encInNames_[k].find("state") != std::string::npos) stateIdx++;
                        }
                        if (stateIdx < statesEnc_.size()) {
                            encInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, statesEnc_[stateIdx].data(), statesEnc_[stateIdx].size(), shapesEnc_[stateIdx].data(), shapesEnc_[stateIdx].size()));
                        }
                    }
                }

                std::vector<const char*> encInNamePtrs;
                for(const auto& name : encInNames_) encInNamePtrs.push_back(name.c_str());
                std::vector<const char*> encOutNamePtrs;
                for(const auto& name : encOutNames_) encOutNamePtrs.push_back(name.c_str());

                auto encOutputs = sessionEnc_->Run(Ort::RunOptions{nullptr}, encInNamePtrs.data(), encInputs.data(), encInputs.size(), encOutNamePtrs.data(), encOutNamePtrs.size());

                for (size_t i = 0; i < encOutNames_.size(); i++) {
                    if (encOutNames_[i].find("state") != std::string::npos) {
                        size_t stateIdx = 0;
                        for (size_t k = 0; k < i; k++) {
                            if (encOutNames_[k].find("state") != std::string::npos) stateIdx++;
                        }
                        if (stateIdx < statesEnc_.size()) {
                            std::memcpy(statesEnc_[stateIdx].data(), encOutputs[i].GetTensorMutableData<float>(), statesEnc_[stateIdx].size() * sizeof(float));
                        }
                    }
                }

                // 2. ERB Decoder Task
                std::vector<Ort::Value> erbInputs;
                for (const auto& inName : erbInNames_) {
                    if (inName.find("state") != std::string::npos) {
                        size_t stateIdx = 0;
                        for (size_t k = 0; k < erbInNames_.size(); k++) {
                            if (erbInNames_[k] == inName) break;
                            if (erbInNames_[k].find("state") != std::string::npos) stateIdx++;
                        }
                        if (stateIdx < statesErbDec_.size()) {
                            erbInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, statesErbDec_[stateIdx].data(), statesErbDec_[stateIdx].size(), shapesErb_[stateIdx].data(), shapesErb_[stateIdx].size()));
                        }
                    } else {
                        // Inherit from encoder outputs
                        for (size_t j = 0; j < encOutNames_.size(); j++) {
                            if (encOutNames_[j] == inName) {
                                auto info = encOutputs[j].GetTensorTypeAndShapeInfo();
                                erbInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, encOutputs[j].GetTensorMutableData<float>(), info.GetElementCount(), info.GetShape().data(), info.GetShape().size()));
                                break;
                            }
                        }
                    }
                }

                std::vector<const char*> erbInNamePtrs;
                for(const auto& name : erbInNames_) erbInNamePtrs.push_back(name.c_str());
                std::vector<const char*> erbOutNamePtrs;
                for(const auto& name : erbOutNames_) erbOutNamePtrs.push_back(name.c_str());

                auto erbOutputs = sessionErbDec_->Run(Ort::RunOptions{nullptr}, erbInNamePtrs.data(), erbInputs.data(), erbInputs.size(), erbOutNamePtrs.data(), erbOutNamePtrs.size());
                
                for (size_t i = 0; i < erbOutNames_.size(); i++) {
                    if (erbOutNames_[i].find("state") != std::string::npos) {
                        size_t stateIdx = 0;
                        for (size_t k = 0; k < i; k++) {
                            if (erbOutNames_[k].find("state") != std::string::npos) stateIdx++;
                        }
                        if (stateIdx < statesErbDec_.size()) {
                            std::memcpy(statesErbDec_[stateIdx].data(), erbOutputs[i].GetTensorMutableData<float>(), statesErbDec_[stateIdx].size() * sizeof(float));
                        }
                    }
                }

                float* gains = erbOutputs[0].GetTensorMutableData<float>();
                const float MIN_ERB_GAIN = 0.1f; // -20dB floor to prevent complete silence
                for (int k = 0; k < (fftSize_ / 2 + 1); k++) {
                    float gain = 0.0f;
                    for (int b = 0; b < nbErb_; b++) gain += erbFb_[b][k] * gains[b];
                    // Apply minimum gain floor
                    if (gain < MIN_ERB_GAIN) gain = MIN_ERB_GAIN;
                    enhancedSpec[k] *= gain;
                }

                // 3. DF Decoder Task
                std::vector<Ort::Value> dfInputs;
                for (const auto& inName : dfInNames_) {
                    if (inName.find("state") != std::string::npos) {
                        size_t stateIdx = 0;
                        for (size_t k = 0; k < dfInNames_.size(); k++) {
                            if (dfInNames_[k] == inName) break;
                            if (dfInNames_[k].find("state") != std::string::npos) stateIdx++;
                        }
                        if (stateIdx < statesDfDec_.size()) {
                            dfInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, statesDfDec_[stateIdx].data(), statesDfDec_[stateIdx].size(), shapesDf_[stateIdx].data(), shapesDf_[stateIdx].size()));
                        }
                    } else {
                        for (size_t j = 0; j < encOutNames_.size(); j++) {
                            if (encOutNames_[j] == inName) {
                                auto info = encOutputs[j].GetTensorTypeAndShapeInfo();
                                dfInputs.push_back(Ort::Value::CreateTensor<float>(*memoryInfo_, encOutputs[j].GetTensorMutableData<float>(), info.GetElementCount(), info.GetShape().data(), info.GetShape().size()));
                                break;
                            }
                        }
                    }
                }

                std::vector<const char*> dfInNamePtrs;
                for(const auto& name : dfInNames_) dfInNamePtrs.push_back(name.c_str());
                std::vector<const char*> dfOutNamePtrs;
                for(const auto& name : dfOutNames_) dfOutNamePtrs.push_back(name.c_str());

                auto dfOutputs = sessionDfDec_->Run(Ort::RunOptions{nullptr}, dfInNamePtrs.data(), dfInputs.data(), dfInputs.size(), dfOutNamePtrs.data(), dfOutNamePtrs.size());

                for (size_t i = 0; i < dfOutNames_.size(); i++) {
                    if (dfOutNames_[i].find("state") != std::string::npos) {
                        size_t stateIdx = 0;
                        for (size_t k = 0; k < i; k++) {
                            if (dfOutNames_[k].find("state") != std::string::npos) stateIdx++;
                        }
                        if (stateIdx < statesDfDec_.size()) {
                            std::memcpy(statesDfDec_[stateIdx].data(), dfOutputs[i].GetTensorMutableData<float>(), statesDfDec_[stateIdx].size() * sizeof(float));
                        }
                    }
                }

                float* dfCoefs = dfOutputs[0].GetTensorMutableData<float>();
                rollingSpec_.push_front(micSpec);
                if (rollingSpec_.size() > 5) rollingSpec_.pop_back();

                if (rollingSpec_.size() == 5) {
                    for (int k = 0; k < nbDf_; k++) {
                        std::complex<float> sum(0, 0);
                        for (int i = 0; i < 5; i++) {
                            std::complex<float> h(dfCoefs[k * 10 + i * 2], dfCoefs[k * 10 + i * 2 + 1]);
                            sum += rollingSpec_[i][k] * h;
                        }
                        enhancedSpec[k] = sum;
                    }
                }

                // AI Safety Bypass: Ensure we don't zero out completely if the AI is confused
                float outputMagRms = 0.0f;
                for (const auto& s : enhancedSpec) outputMagRms += std::norm(s);
                outputMagRms = sqrtf(outputMagRms / (float)(enhancedSpec.size() + 1e-10f));
                
                // Compare linear output magnitude with linear input magnitude (safety floor)
                // ECHO-AWARE: Only trigger bypass if Reference intensity is is not overwhelming the internal signal
                // Relaxed to 0.8 (80%) to allow voice to be saved even if game audio is present.
                if (outputMagRms < 0.1f * micMagRms && micMagRms > 0.1f) {
                    if (refRms < 0.8f * micMagRms) {
                         float mix = 0.5f; // 50% original mic bypass for safety
                         for (size_t k = 0; k < enhancedSpec.size(); k++) {
                             enhancedSpec[k] = enhancedSpec[k] * (1.0f - mix) + micSpec[k] * mix;
                         }
                         // Recalculate outputMagRms after blend
                         outputMagRms = 0.0f;
                         for (const auto& s : enhancedSpec) outputMagRms += std::norm(s);
                         outputMagRms = sqrtf(outputMagRms / (float)(enhancedSpec.size() + 1e-10f));
                         LOGI("WARNING: DFN AI suppressing voice. Safety bypass applied (Ref=%.4f). New MagOUT=%.4f", refRms, outputMagRms);
                    } else {
                         LOGI("DFN AI suppressing signal (likely ECHO). Bypass skipped (Ref=%.4f Mic=%.4f)", refRms, micMagRms);
                    }
                }

                if (micMagRms > 0.1f) {
                    LOGI("DFN AI Detail: MagIn=%.4f MagOut=%.4f (Gain %.2f)", micMagRms, outputMagRms, outputMagRms / (micMagRms + 1e-10f));
                }
            } catch (const std::exception& e) {
                LOGE("Inference error: %s", e.what());
            }
        }

        std::vector<float> outFrame;
        istft(enhancedSpec, outFrame);
        
        // SAFETY: If istft failed (returned early), use passthrough
        if (outFrame.size() != (size_t)fftSize_) {
            LOGE("istft failed, using passthrough for this frame");
            outFrame.resize(fftSize_);
            for (int i = 0; i < fftSize_; i++) {
                outFrame[i] = frame[i]; // Use original windowed frame
            }
        }
        
        for (int i = 0; i < fftSize_; i++) {
            outAccumulator_[processedSamples + i] += outFrame[i];
        }
        processedSamples += hopSize_;
    }

    // Final output copy with diagnostic RMS
    float finalRms = 0.0f;
    for (size_t i = 0; i < count; i++) {
        float val = outAccumulator_[i];
        finalRms += val * val;
        if (val > 1.0f) val = 1.0f;
        if (val < -1.0f) val = -1.0f;
        output[i] = (int16_t)(val * 32767.0f);
    }
    finalRms = sqrtf(finalRms / (float)(count + 1e-10f));
    
    // NUCLEAR BYPASS: Thresholds lowered. If there is audible input but silence output, FORCE passthrough.
    // ECHO-AWARE: Only bypass if Reference is quiet.
    float inputRmsCheck = 0.0f;
    float refRmsCheck = 0.0f;
    for (size_t i = 0; i < count; i++) {
        float v = input[i] / 32768.0f;
        inputRmsCheck += v * v;
        if (reference) {
            float r = reference[i] / 32768.0f;
            refRmsCheck += r * r;
        }
    }
    inputRmsCheck = sqrtf(inputRmsCheck / (float)(count + 1e-10f));
    refRmsCheck = sqrtf(refRmsCheck / (float)(count + 1e-10f));
    
    if (finalRms < 0.005f && inputRmsCheck > 0.01f) {
        // Relaxed lockout: Allow bypass if Ref is up to 80% of Input (Voice + Bleed)
        if (refRmsCheck < 0.8f * inputRmsCheck) {
             LOGE("NUCLEAR BYPASS ACTIVE: DFN produced silence (Out=%.4f In=%.4f) with Mod Ref (%.4f). Passthrough.", finalRms, inputRmsCheck, refRmsCheck);
             std::copy(input, input + count, output);
        } else {
             LOGI("DFN Output Silent (Likely Echo Removal): In=%.4f Ref=%.4f Out=%.4f", inputRmsCheck, refRmsCheck, finalRms);
        }
    } else if (inputRmsCheck > 0.001f) {
        LOGI("DFN Process Final: TimeIn=%.4f TimeOut=%.4f (Gain %.2f)", inputRmsCheck, finalRms, finalRms / (inputRmsCheck + 1e-10f));
    }

    // 5. Buffer Management: Correct Overlap-Add Shift
    // We processed 'processedSamples' in steps of 'hopSize_'. 
    // BUT we must keep (fftSize - hopSize) for the next overlap.
    if (processedSamples > 0) {
        inputBuffer_.erase(inputBuffer_.begin(), inputBuffer_.begin() + processedSamples);
        inputBufferRef_.erase(inputBufferRef_.begin(), inputBufferRef_.begin() + processedSamples);
        
        // CRITICAL: Also shift the output accumulator to maintain proper overlap-add
        // Keep the tail that overlaps with the next frame
        if (outAccumulator_.size() > processedSamples) {
            std::copy(outAccumulator_.begin() + processedSamples, outAccumulator_.end(), outAccumulator_.begin());
            std::fill(outAccumulator_.begin() + (outAccumulator_.size() - processedSamples), outAccumulator_.end(), 0.0f);
        }
    }
}

} // namespace bokbok
