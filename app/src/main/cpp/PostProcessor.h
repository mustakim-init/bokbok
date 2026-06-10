#ifndef BOKBOK_POST_PROCESSOR_H
#define BOKBOK_POST_PROCESSOR_H

#include <string>
#include <functional>
#include <vector>
#include <atomic>
#ifdef BOKBOK_ENABLE_AI
#include "DeepFilterNet.h"
#endif
#include "Aec3Processor.h"

namespace bokbok {

class PostProcessor {
public:
    struct Config {
        std::string micPath;
        std::string internalPath;
        std::string finalOutputPath;
        std::string modelPath;
        int videoFd = -1;
        
        bool enableBleedReduction = false;
        bool enableNoiseReduction = false;
        bool enableStudioMaster = false;
        float micGain = 1.0f;
        float internalGain = 1.0f;
        int qualityMode = 1; 
        bool exportMicOnly = false;
        bool exportInternalOnly = false;
        std::string micExportPath;
        std::string internalExportPath;
        int numChannels = 1;
        int internalChannels = 1; // 1 for mono, 2 for stereo
        int sampleRate = 48000;
        int audioBitrate = 128000;
    };

    // Callback for progress updates (0.0 to 1.0)
    using ProgressCallback = std::function<void(float, const std::string&)>;

    PostProcessor();
    ~PostProcessor();

    void setOnProgress(ProgressCallback callback);

    bool process(const Config& config);

    std::vector<int16_t> readPcmFile(const std::string& path);

    void cancel();

private:
    std::atomic<bool> shouldCancel_{false};
#ifdef BOKBOK_ENABLE_AI
    std::unique_ptr<DeepFilterNet> deepFilter_;
#endif
    std::unique_ptr<Aec3Processor> aec3_;  // AEC3 for bleed reduction
    ProgressCallback onProgress_;

    std::vector<int16_t> readPcmWindow(const std::string& path, size_t start, size_t count);
    int64_t calculateAlignment(const std::vector<int16_t>& mic, const std::vector<int16_t>& internal, int sampleRate);
    bool muxVideoWithAudioFromFd(
        const std::string& micMixedPath,
        int videoFd,
        const std::string& outputPath,
        int sampleRate,
        int audioBitrate
    );
    bool encodeAudioOnly(
        const std::string& pcmPath,
        const std::string& outputPath,
        int sampleRate,
        int audioBitrate,
        int numChannels
    );
};

} // namespace bokbok

#endif // BOKBOK_POST_PROCESSOR_H
