#include "PostProcessor.h"
#include "DeepFilterNet.h"

#include <fstream>
#include <algorithm>
#include <complex>
#include <cmath>
#include <media/NdkMediaExtractor.h>
#include <media/NdkMediaMuxer.h>
#include <media/NdkMediaCodec.h>
#include <media/NdkMediaFormat.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
#include <cerrno>
#include <android/log.h>

#define LOG_TAG "PostProcessor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace bokbok {

PostProcessor::PostProcessor() {}
PostProcessor::~PostProcessor() {}

void PostProcessor::setOnProgress(ProgressCallback callback) {
    onProgress_ = callback;
}

void PostProcessor::cancel() {
    shouldCancel_.store(true);
}

std::vector<int16_t> PostProcessor::readPcmFile(const std::string& path) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file.is_open()) return {};

    std::streamsize size = file.tellg();
    file.seekg(0, std::ios::beg);

    if (size <= 0) return {};

    std::vector<int16_t> buffer(size / sizeof(int16_t));
    if (file.read(reinterpret_cast<char*>(buffer.data()), size)) {
        return buffer;
    }
    return {};
}

bool PostProcessor::process(const Config& config) {
    if (onProgress_) onProgress_(0.0f, "Starting Post-Processing...");
    
    const std::string& micPath = config.micPath;
    const std::string& internalPath = config.internalPath;
    const std::string& finalOutputPath = config.finalOutputPath;
    const std::string& modelPath = config.modelPath;
    int videoFd = config.videoFd;
    
    // Use member callback if available
    ProgressCallback onProgress = onProgress_;

    LOGI("Starting Post-Processing (DeepFilterNet %s)...", config.exportMicOnly ? "Mic-Only" : "Mix");
    LOGI("Config: micGain=%.2f intGain=%.2f SM=%s Bleed=%s Noise=%s", 
         config.micGain, config.internalGain, 
         config.enableStudioMaster ? "ON" : "OFF",
         config.enableBleedReduction ? "ON" : "OFF",
         config.enableNoiseReduction ? "ON" : "OFF");
    shouldCancel_.store(false);

    // 1. Open Streams (Simplified - No Pre-Alignment)
    std::ifstream micFile(micPath, std::ios::binary);
    std::ifstream intFile(internalPath, std::ios::binary);
    
    if (!micFile.is_open() || !intFile.is_open()) {
        LOGE("Failed to open raw PCM streams");
        return false;
    }

    // Prepare model - only if noise reduction is enabled
    if (config.enableNoiseReduction) {
        deepFilter_ = std::make_unique<DeepFilterNet>();
        if (!deepFilter_->init(modelPath, config.sampleRate)) {
            LOGE("Failed to load DFN models from %s", modelPath.c_str());
            // Optionally disable noise reduction if init fails?
            // For now, we keep it enabled but DeepFilterNet::process has !isLoaded_ check which is good, 
            // but we must be careful not to dereference null later if init completely failed to allocate (unlikely with make_unique).
        } else {
             LOGI("DeepFilterNet models loaded.");
        }
    }
    
    // Initialize AEC3 for bleed reduction (if enabled)
    if (config.enableBleedReduction) {
        aec3_ = std::make_unique<Aec3Processor>(config.sampleRate, config.numChannels);
        LOGI("AEC3 initialized for speaker bleed removal.");
    }
    
    // Chunk size: 1s buffer
    const size_t CHUNK_SAMPLES = config.sampleRate / 2; // 0.5s for responsiveness
    std::vector<int16_t> micBuf(CHUNK_SAMPLES);
    std::vector<int16_t> intBuf(CHUNK_SAMPLES * config.internalChannels);
    std::vector<int16_t> intMono(CHUNK_SAMPLES); // For AI/AEC reference
    
    // Float buffers for processing
    std::vector<float> micFloat(CHUNK_SAMPLES);
    std::vector<float> intFloat(CHUNK_SAMPLES);
    std::vector<int16_t> outChunk(CHUNK_SAMPLES);

    // We need a temp file for the processed mix
    std::string tempMixPath = micPath + ".mix.tmp";
    std::ofstream mixOut(tempMixPath, std::ios::binary);
    if (!mixOut.is_open()) {
        LOGE("Failed to open temp mix: %s", tempMixPath.c_str());
        return false;
    }

    std::ofstream micExportOut;
    if (config.exportMicOnly && !config.micExportPath.empty()) {
        micExportOut.open(config.micExportPath + ".pcm.tmp", std::ios::binary);
    }

    std::ofstream intExportOut;
    if (config.exportInternalOnly && !config.internalExportPath.empty()) {
        intExportOut.open(config.internalExportPath + ".pcm.tmp", std::ios::binary);
    }

    // Get file size for progress
    intFile.seekg(0, std::ios::end);
    size_t totalSamples = intFile.tellg() / sizeof(int16_t);
    intFile.seekg(0, std::ios::beg);
    
    // Ensure Mic is at start (remove any previous seeks if any existed)
    micFile.seekg(0, std::ios::beg);

    LOGI("Processing: Streaming Loop (Stable RMS + Dynamic Sync)...");
    // --- STUDIO MASTER CHAIN STATES ---
    float compGain = 1.0f;    // Compressor state
    float smoothedBridgeGain = 1.0f; // Smoothed mic normalization
    const float COMP_ATTACK = 0.05f; // Fast attack
    const float COMP_RELEASE = 0.005f; // Slower release for smooth "Broadcast" sound

    // EQ Filter States (Reset per recording)
    float x1_a=0, x2_a=0, y1_a=0, y2_a=0;
    float x1_p=0, x2_p=0, y1_p=0, y2_p=0;

    // --- STABLE GAIN & SYNC STATES (Restored) ---
    const float ALPHA = 0.01f;
    float movingIntRMS = 500.0f;    // Initial sensible value for 16-bit PCM
    const float TARGET_RMS = 3276.0f; // Targeting ~10% of 16-bit full scale (+/- 32768)
    
    size_t aecLatency = config.enableBleedReduction ? 480 : 0;
    size_t dfnLatency = config.enableNoiseReduction ? 480 : 0;
    size_t totalLatencySamples = aecLatency + dfnLatency;
    
    std::vector<int16_t> internalDelayLine(totalLatencySamples > 0 ? totalLatencySamples : 1, 0);
    size_t delayWriteIdx = 0;
    size_t processedSamples = 0;

    LOGI("Processing Start: InternalChannels=%d SampleRate=%d", config.internalChannels, config.sampleRate);

    while (!shouldCancel_.load()) {
        // Read Internal (Master Clock)
        size_t intReadSize = CHUNK_SAMPLES * config.internalChannels;
        intFile.read(reinterpret_cast<char*>(intBuf.data()), intReadSize * sizeof(int16_t));
        size_t intReadSamples = intFile.gcount() / sizeof(int16_t);
        size_t framesRead = intReadSamples / config.internalChannels;
        if (framesRead == 0) break;

        // Read Mic (Strictly Aligned)
        micFile.read(reinterpret_cast<char*>(micBuf.data()), framesRead * sizeof(int16_t));
        size_t micRead = micFile.gcount() / sizeof(int16_t);
        if (micRead < framesRead) {
            std::fill(micBuf.begin() + micRead, micBuf.begin() + framesRead, 0);
        }

        size_t samplesThisChunk = framesRead;
        std::vector<int16_t> micProcessingBuf(samplesThisChunk);
        for (size_t i = 0; i < samplesThisChunk; i++) micProcessingBuf[i] = micBuf[i];

        // --- STAGE 1: Reference Preparation (Down-mix if Stereo) ---
        // intMono is used for AEC and DeepFilterNet reference
        if (config.internalChannels == 2) {
            for (size_t i = 0; i < samplesThisChunk; i++) {
                int32_t val = (int32_t)intBuf[i * 2] + (int32_t)intBuf[i * 2 + 1];
                intMono[i] = (int16_t)(val / 2);
            }
        } else {
            std::copy(intBuf.begin(), intBuf.begin() + samplesThisChunk, intMono.begin());
        }

        // --- STAGE 2: AEC3 Bleed Removal (Layer 1) ---
        if (config.enableBleedReduction && aec3_) {
            aec3_->AnalyzeInternal(intMono.data(), samplesThisChunk);
            aec3_->ProcessMic(micProcessingBuf.data(), samplesThisChunk);
        }

        // --- STAGE 3: (REMOVED) ---
        // Pre-AI loudness normalization was removed because it caused harsh, 
        // over-amplified audio. The post-AI compressor + makeup gain handles loudness instead.


        // --- STAGE 5: AI Polishing (DeepFilterNet Layer 2) ---
        if (deepFilter_) {
            deepFilter_->process(
                micProcessingBuf.data(), 
                samplesThisChunk, 
                outChunk.data(), 
                config.enableBleedReduction ? intMono.data() : nullptr, 
                config.enableBleedReduction, 
                config.enableNoiseReduction
            );
        } else {
             // Fallback: Just copy input to output if DFN is not loaded but loop expects outChunk to be filled
             std::copy(micProcessingBuf.begin(), micProcessingBuf.begin() + samplesThisChunk, outChunk.begin());
        }

        // --- STAGE 6: Broadcast Dynamics (Comp + Limit) + Mix ---
        std::vector<int16_t> mixBuf(samplesThisChunk * 2); // Final Mix is ALWAYS Stereo
        for (size_t i = 0; i < samplesThisChunk; i++) {
            float processedMic = (float)outChunk[i];

            if (config.enableStudioMaster) {
                // --- STAGE 5.5: Broadcast EQ (Post-AI) ---
                // Gentle low-shelf boost for warmth, high-shelf for clarity
                // Low-shelf: +3dB @ 150Hz, High-shelf: +2dB @ 4kHz (approx)
                const float ls_b0 = 1.02f, ls_b1 = -1.92f, ls_b2 = 0.91f;
                const float ls_a1 = -1.92f, ls_a2 = 0.93f;
                const float hs_b0 = 1.04f, hs_b1 = -1.85f, hs_b2 = 0.82f;
                const float hs_a1 = -1.85f, hs_a2 = 0.86f;

                float x = processedMic;
                float y_ls = ls_b0*x + ls_b1*x1_a + ls_b2*x2_a - ls_a1*y1_a - ls_a2*y2_a;
                x2_a = x1_a; x1_a = x; y2_a = y1_a; y1_a = y_ls;
                float y_hs = hs_b0*y_ls + hs_b1*x1_p + hs_b2*x2_p - hs_a1*y1_p - hs_a2*y2_p;
                x2_p = x1_p; x1_p = y_ls; y2_p = y1_p; y1_p = y_hs;
                processedMic = y_hs;

                // --- STAGE 6: Broadcast Dynamics (Gentle Compressor + Limiter) ---
                // Soft-Knee Compressor with higher threshold (0.4 = ~-8dB)
                float env = std::abs(processedMic / 32768.0f);
                float threshold = 0.4f;
                float ratio = 3.0f; // 3:1 ratio
                float targetGain = 1.0f;
                if (env > threshold) {
                    targetGain = (threshold + (env - threshold) / ratio) / env;
                }
                float factor = (targetGain < compGain) ? COMP_ATTACK : COMP_RELEASE;
                compGain = (1.0f - factor) * compGain + factor * targetGain;
                processedMic *= compGain;

                // Makeup Gain (+6dB for broadcast loudness)
                processedMic *= 2.0f;

                // Safety Limiter (Soft Clip)
                if (processedMic > 30000.0f) processedMic = 30000.0f + (processedMic - 30000.0f) * 0.1f;
                if (processedMic < -30000.0f) processedMic = -30000.0f + (processedMic + 30000.0f) * 0.1f;
                if (processedMic > 32000.0f) processedMic = 32000.0f;
                if (processedMic < -32000.0f) processedMic = -32000.0f;
            }

            // Combine Mic (Mono processed) + Internal (Original)
            // Final Mix is ALWAYS Stereo (2 channels) for compatibility
            if (config.internalChannels == 2) {
                float gameL = (float)intBuf[i * 2];
                float gameR = (float)intBuf[i * 2 + 1];
                
                // Mix mono processed mic to both channels
                float mixL = (processedMic * config.micGain) + (gameL * config.internalGain);
                float mixR = (processedMic * config.micGain) + (gameR * config.internalGain);
                
                mixBuf[i * 2] = (int16_t)std::clamp(mixL, -32768.0f, 32767.0f);
                mixBuf[i * 2 + 1] = (int16_t)std::clamp(mixR, -32768.0f, 32767.0f);
            } else {
                float gameMono = (float)intBuf[i];
                float mixed = (processedMic * config.micGain) + (gameMono * config.internalGain);
                int16_t val = (int16_t)std::clamp(mixed, -32768.0f, 32767.0f);
                mixBuf[i * 2] = val;
                mixBuf[i * 2 + 1] = val;
            }
        }

        // Write to temp file (Always Stereo)
        mixOut.write(reinterpret_cast<char*>(mixBuf.data()), samplesThisChunk * 2 * sizeof(int16_t));
        
        // --- STAGE 7: Separate Track Exports (INCL. POLISH) ---
        if (config.exportMicOnly && micExportOut.is_open()) {
            // Write processed mono mic
            micExportOut.write(reinterpret_cast<char*>(outChunk.data()), samplesThisChunk * sizeof(int16_t));
        }

        if (config.exportInternalOnly && intExportOut.is_open()) {
            // Write original internal audio (stereo or mono as is)
            intExportOut.write(reinterpret_cast<char*>(intBuf.data()), intReadSamples * sizeof(int16_t));
        }

        processedSamples += samplesThisChunk;

        if (onProgress && totalSamples > 0 && (processedSamples % (samplesThisChunk * 10) == 0)) {
            onProgress(0.1f + 0.5f * ((float)processedSamples / totalSamples), "Processing Audio...");
        }
    }

    micFile.close();
    intFile.close();
    mixOut.close();
    if (micExportOut.is_open()) micExportOut.close();
    if (intExportOut.is_open()) intExportOut.close();
    LOGI("Processing complete. %zu samples.", processedSamples);

    if (shouldCancel_.load()) return false;

    // --- STAGE 8: Separate Track Muxing (M4A) ---
    if (config.exportMicOnly && !config.micExportPath.empty()) {
        std::string tempMicPCM = config.micExportPath + ".pcm.tmp";
        if (onProgress) onProgress(0.85f, "Exporting Mic Track...");
        encodeAudioOnly(tempMicPCM, config.micExportPath, config.sampleRate, config.audioBitrate, 1);
        unlink(tempMicPCM.c_str());
    }

    if (config.exportInternalOnly && !config.internalExportPath.empty()) {
        std::string tempIntPCM = config.internalExportPath + ".pcm.tmp";
        if (onProgress) onProgress(0.9f, "Exporting Internal Track...");
        encodeAudioOnly(tempIntPCM, config.internalExportPath, config.sampleRate, config.audioBitrate, config.internalChannels);
        unlink(tempIntPCM.c_str());
    }

    // 4. Muxing Final Video
    if (onProgress) onProgress(0.95f, "Muxing Final Video...");
    bool success = muxVideoWithAudioFromFd(tempMixPath, videoFd, finalOutputPath, config.sampleRate, config.audioBitrate);
    unlink(tempMixPath.c_str());
    return success;
}



bool PostProcessor::muxVideoWithAudioFromFd(
    const std::string& micMixedPath,
    int videoFd,
    const std::string& outputPath,
    int sampleRate,
    int audioBitrate
) {
    ProgressCallback onProgress = onProgress_;
    float progressStart = 0.6f;
    float progressEnd = 1.0f;

    std::ifstream mixFile(micMixedPath, std::ios::binary | std::ios::ate);
    size_t totalBytes = mixFile.tellg();
    mixFile.seekg(0, std::ios::beg);
    LOGI("Mix file totalBytes: %zu", totalBytes);

    if (totalBytes == 0 || totalBytes == (size_t)-1) {
        LOGE("Mix PCM file is empty or unreadable!");
        return false;
    }

    if (videoFd < 0) {
        LOGE("Invalid Video FD provided!");
        return false;
    }

    // Check Video FD stats
    struct stat st;
    if (fstat(videoFd, &st) != 0) {
        LOGE("Failed to stat video FD: %d (errno %d)", videoFd, errno);
        return false;
    }
    LOGI("Video source FD size: %lld", (long long)st.st_size);

    int fd = open(outputPath.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd < 0) return false;

    AMediaMuxer* muxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    AMediaExtractor* extractor = AMediaExtractor_new();
    
    // Ensure video FD is at the beginning
    lseek(videoFd, 0, SEEK_SET);
    media_status_t err = AMediaExtractor_setDataSourceFd(extractor, videoFd, 0, st.st_size);
    if (err != AMEDIA_OK) {
        LOGE("Failed to set extractor data source FD: %d, error: %d", videoFd, err);
        AMediaExtractor_delete(extractor);
        AMediaMuxer_delete(muxer);
        close(fd);
        return false;
    }
    
    size_t trackCount = AMediaExtractor_getTrackCount(extractor);
    LOGI("Extractor found %zu tracks in source file", trackCount);

    int videoTrackIndex = -1;
    int muxerVideoTrackIdx = -1;
    int muxerAudioTrackIdx = -1;

    for (size_t i = 0; i < AMediaExtractor_getTrackCount(extractor); i++) {
        AMediaFormat* format = AMediaExtractor_getTrackFormat(extractor, i);
        const char* mime;
        AMediaFormat_getString(format, AMEDIAFORMAT_KEY_MIME, &mime);
        LOGI("Found track %zu: %s", i, mime);
        if (strncmp(mime, "video/", 6) == 0) {
            videoTrackIndex = i;
            AMediaExtractor_selectTrack(extractor, i);
            muxerVideoTrackIdx = AMediaMuxer_addTrack(muxer, format);
            LOGI("Video track added to muxer: idx=%d", muxerVideoTrackIdx);
            AMediaFormat_delete(format);
            break; 
        }
        AMediaFormat_delete(format);
    }

    if (muxerVideoTrackIdx < 0) {
        LOGE("No video track found in source file!");
        AMediaExtractor_delete(extractor);
        AMediaMuxer_delete(muxer);
        close(fd);
        return false;
    }

    AMediaCodec* audioCodec = AMediaCodec_createEncoderByType("audio/mp4a-latm");
    AMediaFormat* audioFormat = AMediaFormat_new();
    AMediaFormat_setString(audioFormat, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
    // AMEDIAFORMAT_KEY_AAC_PROFILE constant is for LC profile (2)
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_AAC_PROFILE, 2); 
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_SAMPLE_RATE, sampleRate);
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, 2); // Stereo output for better compatibility
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_BIT_RATE, audioBitrate);
    AMediaCodec_configure(audioCodec, audioFormat, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    media_status_t startStatus = AMediaCodec_start(audioCodec);
    LOGI("Audio Codec start status: %d (AMEDIA_OK=%d)", startStatus, AMEDIA_OK);
    if (startStatus != AMEDIA_OK) {
        LOGE("Audio Codec failed to start!");
        AMediaCodec_delete(audioCodec);
        AMediaFormat_delete(audioFormat);
        AMediaExtractor_delete(extractor);
        AMediaMuxer_delete(muxer);
        close(fd);
        return false;
    }
    
    bool muxerStarted = false;
    std::vector<uint8_t> videoBuffer(2 * 1024 * 1024);
    size_t bytesProcessed = 0;
    bool audioEosInput = false;
    bool audioEosOutput = false;
    bool videoDone = false;
    bool audioEncoderFailed = false;

    // --- Probing First Video PTS ---
    // We need this BEFORE starting to queue audio to the encoder to ensure correct offset
    int64_t firstVideoPts = 0; 
    if (AMediaExtractor_getTrackCount(extractor) > 0) {
        // Assume video is the track we selected
        int64_t probedPts = AMediaExtractor_getSampleTime(extractor);
        if (probedPts >= 0) {
            firstVideoPts = probedPts;
            LOGI("Probed first video PTS: %lld us. Syncing audio baseline.", (long long)firstVideoPts);
        } else {
            LOGI("Could not probe first video PTS (empty?). Defaulting to 0.");
        }
    }

    // Timeout safety
    int loopsWithoutOutput = 0;
    const int MAX_LOOPS_WITHOUT_OUTPUT = 1000; 

    // Main muxing loop - run until both audio and video are done
    while (!shouldCancel_.load()) {
        if (audioEosOutput && videoDone) break;
        
        if (audioEosOutput && !muxerStarted) {
             LOGE("Audio EOS reached but muxer never started. Aborting to prevent hang.");
             audioEncoderFailed = true;
             break;
        }

        bool didSomething = false;

        // 1. Audio Input Loop
        if (!audioEosInput) {
            ssize_t idx = AMediaCodec_dequeueInputBuffer(audioCodec, 0);
            if (idx >= 0) {
                size_t bufSize;
                uint8_t* buf = AMediaCodec_getInputBuffer(audioCodec, idx, &bufSize);
                
                // mixFile is already Stereo (2 channels), so we read directly
                mixFile.read(reinterpret_cast<char*>(buf), bufSize);
                size_t read = mixFile.gcount();
                
                if (read > 0) {
                    // PTS Calculation
                    int64_t audioOffset = (firstVideoPts > 0) ? firstVideoPts : 0;
                    // bytesProcessed is in Stereo bytes
                    int64_t pts = audioOffset + ((bytesProcessed / (2 * sizeof(int16_t))) * 1000000LL / sampleRate);
                    
                    AMediaCodec_queueInputBuffer(audioCodec, idx, 0, read, pts, 0);
                    bytesProcessed += read;
                    didSomething = true;
                } else {
                    AMediaCodec_queueInputBuffer(audioCodec, idx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    audioEosInput = true;
                }
            }
        }
        
        // 2. Audio Output & Muxing Loop
        AMediaCodecBufferInfo info;
        ssize_t idx = AMediaCodec_dequeueOutputBuffer(audioCodec, &info, 0);
        if (idx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* newFmt = AMediaCodec_getOutputFormat(audioCodec);
            // Verify format validity before adding?
            muxerAudioTrackIdx = AMediaMuxer_addTrack(muxer, newFmt);
            AMediaMuxer_start(muxer);
            muxerStarted = true;
            AMediaFormat_delete(newFmt);
            didSomething = true;
        } else if (idx >= 0) {
            if (muxerStarted && info.size > 0) {
                 uint8_t* buf = AMediaCodec_getOutputBuffer(audioCodec, idx, nullptr);
                 // Diagnostic check for non-zero encoded data
                 static int muxLogCounter = 0;
                 if (muxLogCounter++ % 100 == 0) {
                     LOGI("MuxAudio: PTS=%lld size=%d flags=%d", (long long)info.presentationTimeUs, info.size, info.flags);
                 }
                 AMediaMuxer_writeSampleData(muxer, muxerAudioTrackIdx, buf, &info);
            }
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) {
                audioEosOutput = true;
            }
            AMediaCodec_releaseOutputBuffer(audioCodec, idx, false);
            didSomething = true;
        } else if (idx == AMEDIACODEC_INFO_TRY_AGAIN_LATER) {
             // No output yet
        } else {
             // Error
             LOGE("Audio Codec Error: %zd", idx);
        }

        // 3. Video Muxing Loop (Draining in parallel)
        if (muxerStarted && !videoDone) {
            ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, videoBuffer.data(), videoBuffer.size());
            if (sampleSize < 0) { 
                videoDone = true; 
            } else {
                AMediaCodecBufferInfo vInfo;
                vInfo.offset = 0; vInfo.size = sampleSize;
                vInfo.presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
                if (firstVideoPts < 0) {
                    firstVideoPts = vInfo.presentationTimeUs;
                    LOGI("First Video PTS detected: %lld us. Syncing audio start.", (long long)firstVideoPts);
                }
                vInfo.flags = AMediaExtractor_getSampleFlags(extractor);
                AMediaMuxer_writeSampleData(muxer, muxerVideoTrackIdx, videoBuffer.data(), &vInfo);
                AMediaExtractor_advance(extractor);
                didSomething = true;
            }
        }

        if (onProgress && totalBytes > 0) {
            float baseProgress = progressStart + (progressEnd - progressStart) * ((float)bytesProcessed / totalBytes);
            // Cap it slightly before progressEnd to allow for final flush feedback
            float displayProgress = std::min(baseProgress, progressStart + (progressEnd - progressStart) * 0.95f);
            onProgress(displayProgress, "Muxing Final Stream...");
        }

        if (!didSomething) {
            loopsWithoutOutput++;
            // If we've spun for a long time with no output, and we have Audio EOS but no Muxer, we should have broken already.
            // Just sleep.
            usleep(1000); 
        } else {
            loopsWithoutOutput = 0;
        }
    }

    // Final drain for video if it wasn't finished (safety belt)
    while (muxerStarted && !videoDone && !shouldCancel_.load()) {
        ssize_t sampleSize = AMediaExtractor_readSampleData(extractor, videoBuffer.data(), videoBuffer.size());
        if (sampleSize < 0) {
            videoDone = true;
        } else {
            AMediaCodecBufferInfo vInfo;
            vInfo.offset = 0; vInfo.size = sampleSize;
            vInfo.presentationTimeUs = AMediaExtractor_getSampleTime(extractor);
            vInfo.flags = AMediaExtractor_getSampleFlags(extractor);
            AMediaMuxer_writeSampleData(muxer, muxerVideoTrackIdx, videoBuffer.data(), &vInfo);
            AMediaExtractor_advance(extractor);
        }
    }

    if (onProgress) onProgress(progressEnd, "Finishing up...");

    AMediaCodec_stop(audioCodec); AMediaCodec_delete(audioCodec);
    AMediaExtractor_delete(extractor);
    if (muxerStarted) AMediaMuxer_stop(muxer);
    AMediaMuxer_delete(muxer);
    close(fd);

    // Return false if we never started muxing OR audio encoding failed
    if (!muxerStarted || audioEncoderFailed) {
        LOGE("muxVideoWithAudioFromFile: Muxer never started or audio failed. Returning false.");
        return false;
    }
    return true;
}

bool PostProcessor::encodeAudioOnly(
    const std::string& pcmPath,
    const std::string& outputPath,
    int sampleRate,
    int audioBitrate,
    int numChannels
) {
    LOGI("Encoding Audio Only: %s -> %s (%d channels)", pcmPath.c_str(), outputPath.c_str(), numChannels);
    
    std::ifstream pcmFile(pcmPath, std::ios::binary | std::ios::ate);
    size_t totalBytes = pcmFile.tellg();
    pcmFile.seekg(0, std::ios::beg);
    
    if (totalBytes == 0 || totalBytes == (size_t)-1) {
        LOGE("PCM file is empty or missing: %s", pcmPath.c_str());
        return false;
    }

    int fd = open(outputPath.c_str(), O_CREAT | O_WRONLY | O_TRUNC, 0644);
    if (fd < 0) return false;

    AMediaMuxer* muxer = AMediaMuxer_new(fd, AMEDIAMUXER_OUTPUT_FORMAT_MPEG_4);
    AMediaCodec* codec = AMediaCodec_createEncoderByType("audio/mp4a-latm");
    AMediaFormat* format = AMediaFormat_new();
    AMediaFormat_setString(format, AMEDIAFORMAT_KEY_MIME, "audio/mp4a-latm");
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_AAC_PROFILE, 2); 
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_SAMPLE_RATE, sampleRate);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_CHANNEL_COUNT, numChannels);
    AMediaFormat_setInt32(format, AMEDIAFORMAT_KEY_BIT_RATE, audioBitrate);
    
    AMediaCodec_configure(codec, format, nullptr, nullptr, AMEDIACODEC_CONFIGURE_FLAG_ENCODE);
    AMediaCodec_start(codec);

    bool eosInput = false;
    bool eosOutput = false;
    int trackIdx = -1;
    size_t bytesRead = 0;
    std::vector<uint8_t> buffer(64 * 1024);

    while (!eosOutput && !shouldCancel_.load()) {
        if (!eosInput) {
            ssize_t idx = AMediaCodec_dequeueInputBuffer(codec, 0);
            if (idx >= 0) {
                size_t bufSize;
                uint8_t* buf = AMediaCodec_getInputBuffer(codec, idx, &bufSize);
                pcmFile.read(reinterpret_cast<char*>(buf), bufSize);
                size_t read = pcmFile.gcount();
                if (read > 0) {
                    int64_t pts = (bytesRead / (numChannels * sizeof(int16_t))) * 1000000LL / sampleRate;
                    AMediaCodec_queueInputBuffer(codec, idx, 0, read, pts, 0);
                    bytesRead += read;
                } else {
                    AMediaCodec_queueInputBuffer(codec, idx, 0, 0, 0, AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM);
                    eosInput = true;
                }
            }
        }

        AMediaCodecBufferInfo info;
        ssize_t idx = AMediaCodec_dequeueOutputBuffer(codec, &info, 0);
        if (idx == AMEDIACODEC_INFO_OUTPUT_FORMAT_CHANGED) {
            AMediaFormat* newFmt = AMediaCodec_getOutputFormat(codec);
            trackIdx = AMediaMuxer_addTrack(muxer, newFmt);
            AMediaMuxer_start(muxer);
            AMediaFormat_delete(newFmt);
        } else if (idx >= 0) {
            if (trackIdx >= 0 && info.size > 0) {
                uint8_t* outBuf = AMediaCodec_getOutputBuffer(codec, idx, nullptr);
                AMediaMuxer_writeSampleData(muxer, trackIdx, outBuf, &info);
            }
            if (info.flags & AMEDIACODEC_BUFFER_FLAG_END_OF_STREAM) eosOutput = true;
            AMediaCodec_releaseOutputBuffer(codec, idx, false);
        }
    }

    AMediaCodec_stop(codec);
    AMediaCodec_delete(codec);
    AMediaFormat_delete(format);
    if (trackIdx >= 0) AMediaMuxer_stop(muxer);
    AMediaMuxer_delete(muxer);
    close(fd);
    pcmFile.close();
    
    return true;
}

} // namespace bokbok
