#ifndef BOKBOK_DEEP_FILTER_NET_H
#define BOKBOK_DEEP_FILTER_NET_H

#include <vector>
#include <string>
#include <memory>
#include <complex>

#include <deque>

#include "rnnoise/kiss_fft.h"

// Forward declaration
namespace Ort { class Env; class Session; class MemoryInfo; }

namespace bokbok {

class DeepFilterNet {
public:
    DeepFilterNet();
    ~DeepFilterNet();

    bool init(const std::string& modelDir, int sampleRate = 48000);

    /**
     * Process chunk of audio.
     * @param input Raw float audio input.
     * @param count Number of samples.
     * @param output Output buffer (must be at least count size).
     * @param reference Optional internal audio reference for bleed reduction.
     * @param enableBleed Enable spectral bleed reduction layer.
     * @param enableAI Enable DeepFilterNet AI enhancement layer.
     */
    void process(const int16_t* input, size_t count, int16_t* output, 
                 const int16_t* reference = nullptr, bool enableBleed = true, bool enableAI = true);

private:
    std::unique_ptr<Ort::Env> env_;
    std::unique_ptr<Ort::Session> sessionEnc_;
    std::unique_ptr<Ort::Session> sessionErbDec_;
    std::unique_ptr<Ort::Session> sessionDfDec_;
    std::unique_ptr<Ort::MemoryInfo> memoryInfo_;

    bool isLoaded_ = false;

    // Config Parameters
    int sampleRate_ = 48000;
    int fftSize_ = 960;
    int hopSize_ = 480;
    int nbErb_ = 32;
    int nbDf_ = 96;
    
    // Buffers & States
    std::vector<std::vector<float>> erbFb_; // ERB Filter Bank [nbErb x (fftSize/2 + 1)]
    
    std::vector<float> inputBuffer_;
    std::vector<float> inputBufferRef_;
    std::vector<float> outAccumulator_;
    std::vector<float> analysisWindow_;
    std::vector<float> synthesisWindow_;
    
    // ONNX Tensors (State Buffers)
    std::vector<std::vector<float>> statesEnc_;
    std::vector<std::vector<float>> statesErbDec_; 
    std::vector<std::vector<float>> statesDfDec_;
    std::vector<std::vector<int64_t>> shapesEnc_;
    std::vector<std::vector<int64_t>> shapesErb_;
    std::vector<std::vector<int64_t>> shapesDf_;
    
    // DeepFilterNet v2 Intermediate Tensors
    // Encoder Outputs / Decoder Inputs
    // We store them as vectors of float
    std::vector<float> featErb_;
    std::vector<float> featSpec_;
    std::vector<float> tensor_e0_;
    std::vector<float> tensor_e1_;
    std::vector<float> tensor_e2_;
    std::vector<float> tensor_e3_;
    std::vector<float> tensor_emb_;
    std::vector<float> tensor_c0_;
    std::vector<float> tensor_lsnr_; // Unused but output by encoder

    // Run Options
    bool isV2Model_ = true;

    // Dynamic Model Names
    std::vector<std::string> encInNames_;
    std::vector<std::string> encOutNames_;
    std::vector<std::string> erbInNames_;
    std::vector<std::string> erbOutNames_;
    std::vector<std::string> dfInNames_;
    std::vector<std::string> dfOutNames_;
    
    // Inference Helpers
    kiss_fft_state* fftCfg_ = nullptr;
    kiss_fft_state* ifftCfg_ = nullptr;
    
    // v3 Buffers & States
    std::deque<std::vector<std::complex<float>>> rollingSpec_; // DF history [df_order]
    std::vector<float> meanNormState_; // [nb_erb]
    std::vector<float> unitNormState_; // [nb_df]
    float normAlpha_ = 0.99f;

    void initErb();
    void computeFeats(const std::vector<std::complex<float>>& spec, std::vector<float>& erbOut, std::vector<float>& specOut);
    
    void initWindows();
    void stft(const std::vector<float>& frame, std::vector<std::complex<float>>& spec);
    void istft(const std::vector<std::complex<float>>& spec, std::vector<float>& frame);
    
    // Spectral Bleed Reduction (Layer 1 SES - Spectral Coherence)
    std::vector<float> cohMicPower_;   // E[|Mic|^2]
    std::vector<float> cohRefPower_;   // E[|Ref|^2]
    std::vector<std::complex<float>> cohCrossPower_; // E[Mic * Ref*]
    float sesAlpha_ = 0.9f;            // Coherence smoothing factor (0.9 = 100ms avg)
    
    void applyBleedReduction(std::vector<std::complex<float>>& micSpec, const std::vector<std::complex<float>>& refSpec);
};

} // namespace bokbok

#endif // BOKBOK_DEEP_FILTER_NET_H
