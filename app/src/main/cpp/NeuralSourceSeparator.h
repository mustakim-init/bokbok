#ifndef BOKBOK_NEURAL_SOURCE_SEPARATOR_H
#define BOKBOK_NEURAL_SOURCE_SEPARATOR_H

#include <vector>
#include <string>
#include <memory>
#include <cstdint>

// Forward declaration to avoid exposing ONNX headers to everyone
namespace Ort { class Env; class Session; class MemoryInfo; }

namespace bokbok {

class NeuralSourceSeparator {
public:
    NeuralSourceSeparator();
    ~NeuralSourceSeparator();

    /**
     * Initialize the neural network engine.
     * @param modelPath Path to the .onnx model file.
     * @return true if loaded successfully.
     */
    bool init(const std::string& modelPath);

    /**
     * Process audio frame to isolate voice.
     * 
     * @param micInput Raw microphone input (potentially containing bleed)
     * @param internalReference Internal audio reference (the source of the bleed)
     * @param length Number of samples (frames) to process.
     * @param output Buffer to store isolated voice. Must be at least 'length' size.
     */
    void process(const int16_t* micInput, const int16_t* internalReference, size_t length, int16_t* output);

private:
    std::unique_ptr<Ort::Env> env_;
    std::unique_ptr<Ort::Session> session_;
    std::unique_ptr<Ort::MemoryInfo> memoryInfo_;
    
    // Model specific parameters (Dynamic in real usage, hardcoded default for structure)
    static const int64_t BATCH_SIZE = 1;
    static const int64_t NUM_CHANNELS = 1; // Mono processing usually
    
    bool isLoaded_ = false;
};

} // namespace bokbok

#endif // BOKBOK_NEURAL_SOURCE_SEPARATOR_H
