#include "NeuralSourceSeparator.h"
#include <onnxruntime_cxx_api.h>
#include <android/log.h>
#include <vector>
#include <algorithm>
#include <cmath>

#define LOG_TAG "NeuralSeparator"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

namespace bokbok {

NeuralSourceSeparator::NeuralSourceSeparator() {
    try {
        env_ = std::make_unique<Ort::Env>(ORT_LOGGING_LEVEL_WARNING, "BokBokNeural");
        memoryInfo_ = std::make_unique<Ort::MemoryInfo>(Ort::MemoryInfo::CreateCpu(OrtArenaAllocator, OrtMemTypeDefault));
    } catch (const std::exception& e) {
        LOGE("Failed to create ONNX Environment: %s", e.what());
    }
}

NeuralSourceSeparator::~NeuralSourceSeparator() {
    // Unique ptrs handle cleanup
}

bool NeuralSourceSeparator::init(const std::string& modelPath) {
    if (!env_) return false;
    
    try {
        Ort::SessionOptions sessionOptions;
        sessionOptions.SetIntraOpNumThreads(2); // Mobile optimization
        sessionOptions.SetGraphOptimizationLevel(GraphOptimizationLevel::ORT_ENABLE_ALL);

        session_ = std::make_unique<Ort::Session>(*env_, modelPath.c_str(), sessionOptions);
        isLoaded_ = true;
        LOGI("Model loaded successfully from %s", modelPath.c_str());
        return true;
    } catch (const std::exception& e) {
        LOGE("Failed to load model: %s", e.what());
        isLoaded_ = false;
        return false;
    }
}

void NeuralSourceSeparator::process(const int16_t* micInput, const int16_t* internalReference, size_t length, int16_t* output) {
    // If no model loaded, fallback to pass-through
    if (!isLoaded_ || !session_) {
        std::copy(micInput, micInput + length, output);
        return;
    }

    // Assumptions for this generic implementation:
    // Model takes 2 Inputs: 'mic' and 'ref' [Batch, 1, Length]
    // Model gives 1 Output: 'voice' [Batch, 1, Length]
    // Input/Output type is Float32.
    
    // 1. Pre-process (Int16 -> Float32)
    std::vector<float> micFloat(length);
    std::vector<float> refFloat(length);
    const float SCALE = 1.0f / 32768.0f;
    
    for (size_t i = 0; i < length; i++) {
        micFloat[i] = micInput[i] * SCALE;
        refFloat[i] = internalReference[i] * SCALE;
    }

    // 2. Prepare Tensors
    std::vector<int64_t> inputShape = {1, 1, static_cast<int64_t>(length)};
    size_t inputTensorSize = length; // 1*1*Length

    std::vector<const char*> inputNames = {"mic", "ref"}; // Example names
    std::vector<Ort::Value> inputTensors;
    
    try {
        inputTensors.push_back(Ort::Value::CreateTensor<float>(
            *memoryInfo_, micFloat.data(), inputTensorSize, inputShape.data(), inputShape.size()));
            
        inputTensors.push_back(Ort::Value::CreateTensor<float>(
            *memoryInfo_, refFloat.data(), inputTensorSize, inputShape.data(), inputShape.size()));

        // 3. Run Inference
        const char* outputNames[] = {"voice"};
        auto outputTensors = session_->Run(
            Ort::RunOptions{nullptr}, 
            inputNames.data(), 
            inputTensors.data(), 
            inputTensors.size(), 
            outputNames, 
            1
        );

        // 4. Post-process (Float32 -> Int16)
        float* outData = outputTensors[0].GetTensorMutableData<float>();
        for (size_t i = 0; i < length; i++) {
            float val = outData[i] * 32767.0f;
            if (val > 32767.0f) val = 32767.0f;
            if (val < -32767.0f) val = -32767.0f;
            output[i] = static_cast<int16_t>(val);
        }

    } catch (const std::exception& e) {
        LOGE("Inference error: %s", e.what());
        // Fallback
        std::copy(micInput, micInput + length, output);
    }
}

} // namespace bokbok
