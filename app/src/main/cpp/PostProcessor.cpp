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
        if (!deepFilter_.init(modelPath, config.sampleRate)) {
            LOGE("Failed to load DFN models from %s", modelPath.c_str());
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
    std::vector<int16_t> intBuf(CHUNK_SAMPLES);
    
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

    // Get file size for progress
    intFile.seekg(0, std::ios::end);
    size_t totalSamples = intFile.tellg() / sizeof(int16_t);
    intFile.seekg(0, std::ios::beg);
    
    // Ensure Mic is at start (remove any previous seeks if any existed)
    micFile.seekg(0, std::ios::beg);

    LOGI("Processing: Streaming Loop (Stable RMS + Dynamic Sync)...");
    // --- STUDIO MASTER CHAIN STATES ---
    float compGain = 1.0f;    // Compressor state
    const float COMP_ATTACK = 0.05f; // Fast attack
    const float COMP_RELEASE = 0.005f; // Slower release for smooth "Broadcast" sound

    // --- STABLE GAIN & SYNC STATES (Restored) ---
    const float ALPHA = 0.01f;
    float movingIntRMS = 0.1f;
    const float TARGET_RMS = 0.1f;
    
    size_t aecLatency = config.enableBleedReduction ? 480 : 0;
    size_t dfnLatency = config.enableNoiseReduction ? 480 : 0;
    size_t totalLatencySamples = aecLatency + dfnLatency;
    
    std::vector<int16_t> internalDelayLine(totalLatencySamples > 0 ? totalLatencySamples : 1, 0);
    size_t delayWriteIdx = 0;
    size_t processedSamples = 0;

    while (!shouldCancel_.load()) {
        // Read Internal (Master Clock)
        intFile.read(reinterpret_cast<char*>(intBuf.data()), CHUNK_SAMPLES * sizeof(int16_t));
        size_t intRead = intFile.gcount() / sizeof(int16_t);
        if (intRead == 0) break;

        // Read Mic (Strictly Aligned)
        micFile.read(reinterpret_cast<char*>(micBuf.data()), CHUNK_SAMPLES * sizeof(int16_t));
        size_t micRead = micFile.gcount() / sizeof(int16_t);
        if (micRead < intRead) {
            std::fill(micBuf.begin() + micRead, micBuf.begin() + intRead, 0);
        }

        size_t samplesThisChunk = intRead;
        std::vector<int16_t> intAecRef(samplesThisChunk);
        std::vector<int16_t> micProcessingBuf(samplesThisChunk);

        // --- STAGE 1: Internal Normalization (Reference only) ---
        float intRefGain = 1.0f;
        if (config.enableStudioMaster) {
            float currentIntRMS = 0;
            for (int16_t s : intBuf) currentIntRMS += std::abs(s);
            currentIntRMS /= (samplesThisChunk + 1);
            movingIntRMS = (1.0f - ALPHA) * movingIntRMS + ALPHA * currentIntRMS;
            intRefGain = TARGET_RMS / std::max(movingIntRMS, 0.001f);
        }
        
        for (size_t i = 0; i < samplesThisChunk; i++) {
            float ivNormalized = (float)intBuf[i] * intRefGain;
            if (ivNormalized > 32767.f) ivNormalized = 32767.f; 
            if (ivNormalized < -32768.f) ivNormalized = -32768.f;
            intAecRef[i] = (int16_t)ivNormalized;
            micProcessingBuf[i] = micBuf[i];
        }

        // --- STAGE 2: AEC3 Bleed Removal (Layer 1) ---
        if (config.enableBleedReduction && aec3_) {
            aec3_->AnalyzeInternal(intAecRef.data(), samplesThisChunk);
            aec3_->ProcessMic(micProcessingBuf.data(), samplesThisChunk);
        }

        // --- STAGE 3: Studio Master Bridge (Loudness Restoration) ---
        if (config.enableStudioMaster) {
            float preDfnRMS = 0;
            for (int16_t s : micProcessingBuf) preDfnRMS += std::abs(s);
            preDfnRMS /= (samplesThisChunk + 1);
            
            float bridgeGain = TARGET_RMS / std::max(preDfnRMS, 0.001f);
            if (bridgeGain > 10.0f) bridgeGain = 10.0f;
            for (size_t i = 0; i < samplesThisChunk; i++) {
                float g = (float)micProcessingBuf[i] * bridgeGain;
                if (g > 32767.f) g = 32767.f; if (g < -32768.f) g = -32768.f;
                micProcessingBuf[i] = (int16_t)g;
            }
        }

        // --- STAGE 4: Clarity EQ (Presence + Anti-Boxy) ---
        if (config.enableStudioMaster) {
            const float b0_a = 0.95f, b1_a = -1.82f, b2_a = 0.88f; // Anti-Boxy
            const float a1_a = -1.82f, a2_a = 0.83f;
            const float b0_p = 1.25f, b1_p = -1.15f, b2_p = 0.45f; // Presence
            const float a1_p = -1.15f, a2_p = 0.45f;
            static float x1_a=0, x2_a=0, y1_a=0, y2_a=0;
            static float x1_p=0, x2_p=0, y1_p=0, y2_p=0;

            for (size_t i = 0; i < samplesThisChunk; i++) {
                float x = (float)micProcessingBuf[i];
                float y_a = b0_a*x + b1_a*x1_a + b2_a*x2_a - a1_a*y1_a - a2_a*y2_a;
                x2_a = x1_a; x1_a = x; y2_a = y1_a; y1_a = y_a;
                float y_p = b0_p*y_a + b1_p*x1_p + b2_p*x2_p - a1_p*y1_p - a2_p*y2_p;
                x2_p = x1_p; x1_p = y_a; y2_p = y1_p; y1_p = y_p;
                if (y_p > 32767.f) y_p = 32767.f; if (y_p < -32768.f) y_p = -32768.f;
                micProcessingBuf[i] = (int16_t)y_p;
            }
        }

        // --- STAGE 5: AI Polishing (DeepFilterNet Layer 2) ---
        deepFilter_.process(
            micProcessingBuf.data(), 
            samplesThisChunk, 
            outChunk.data(), 
            config.enableBleedReduction ? intAecRef.data() : nullptr, 
            config.enableBleedReduction, 
            config.enableNoiseReduction
        );

        // --- STAGE 6: Broadcast Dynamics (Comp + Limit) + Mix ---
        std::vector<int16_t> mixBuf(samplesThisChunk); // New buffer for the final mix
        for (size_t i = 0; i < samplesThisChunk; i++) {
            float processedMic = (float)outChunk[i];

            if (config.enableStudioMaster) {
                // Compressor (Soft-Knee)
                float env = std::abs(processedMic / 32768.0f);
                float targetGain = (env > 0.15f) ? (0.15f + (env - 0.15f) * 0.25f) / env : 1.0f;
                float factor = (targetGain < compGain) ? COMP_ATTACK : COMP_RELEASE;
                compGain = (1.0f - factor) * compGain + factor * targetGain;
                processedMic *= compGain;

                // Safety Limiter
                if (processedMic > 32000.0f) processedMic = 32000.0f;
                if (processedMic < -32000.0f) processedMic = -32000.0f;
            }

            // --- FINAL MIXING ---
            float gameOrig = (float)intBuf[i];
            float gameToMix = gameOrig;
            if (totalLatencySamples > 0) {
                gameToMix = (float)internalDelayLine[delayWriteIdx];
                internalDelayLine[delayWriteIdx] = (int16_t)gameOrig;
                delayWriteIdx = (delayWriteIdx + 1) % totalLatencySamples;
            }

            // Combine Mic (Gained) + Internal (Gained)
            float mixed = (processedMic * config.micGain) + (gameToMix * config.internalGain);
            
            // Final Brickwall to avoid file-level clip
            if (mixed > 32767.0f) mixed = 32767.0f;
            if (mixed < -32768.0f) mixed = -32768.0f;
            mixBuf[i] = (int16_t)mixed;
        }

        // Write to temp file
        mixOut.write(reinterpret_cast<char*>(mixBuf.data()), samplesThisChunk * sizeof(int16_t));
        processedSamples += samplesThisChunk;

        if (onProgress && totalSamples > 0 && (processedSamples % (samplesThisChunk * 10) == 0)) {
            onProgress(0.1f + 0.5f * ((float)processedSamples / totalSamples), "Processing Audio...");
        }
    }

    micFile.close();
    intFile.close();
    mixOut.close();
    LOGI("Processing complete. %zu samples.", processedSamples);

    if (shouldCancel_.load()) return false;

    // 4. Muxing
    if (onProgress) onProgress(0.6f, "Muxing Final Video...");
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
    AMediaFormat_setInt32(audioFormat, AMEDIAFORMAT_KEY_CHANNEL_COUNT, 1); // Mono output to match recording
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
                mixFile.read(reinterpret_cast<char*>(buf), bufSize);
                size_t read = mixFile.gcount();
                if (read > 0) {
                    // PTS Calculation: (Samples / SampleRate) * 1,000,000 us
                    // We must offset audio by the first video PTS to maintain A/V sync
                    int64_t audioOffset = (firstVideoPts > 0) ? firstVideoPts : 0;
                    int64_t pts = audioOffset + ((bytesProcessed / sizeof(int16_t)) * 1000000LL / sampleRate);
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
                 // PTS safety check? 
                 uint8_t* buf = AMediaCodec_getOutputBuffer(audioCodec, idx, nullptr);
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

} // namespace bokbok
