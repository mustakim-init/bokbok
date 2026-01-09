#ifndef BOKBOK_RAW_AUDIO_WRITER_H
#define BOKBOK_RAW_AUDIO_WRITER_H

#include <cstdio>
#include <string>
#include <atomic>
#include <mutex>

namespace bokbok {

class RawAudioWriter {
public:
    RawAudioWriter() = default;
    ~RawAudioWriter() { close(); }

    // Disable copy
    RawAudioWriter(const RawAudioWriter&) = delete;
    RawAudioWriter& operator=(const RawAudioWriter&) = delete;

    bool open(const char* path) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (file_) {
            fclose(file_);
        }
        file_ = fopen(path, "wb");
        if (file_) {
            samplesWritten_.store(0);
            return true;
        }
        return false;
    }

    void write(const int16_t* samples, size_t count) {
        std::lock_guard<std::mutex> lock(mutex_);
        if (file_ && count > 0) {
            size_t written = fwrite(samples, sizeof(int16_t), count, file_);
            samplesWritten_.fetch_add(written);
        }
    }

    void close() {
        std::lock_guard<std::mutex> lock(mutex_);
        if (file_) {
            fclose(file_);
            file_ = nullptr;
        }
    }

    int64_t getSamplesWritten() const {
        return samplesWritten_.load();
    }

    bool isOpen() const {
        std::lock_guard<std::mutex> lock(mutex_);
        return file_ != nullptr;
    }

private:
    FILE* file_ = nullptr;
    std::atomic<int64_t> samplesWritten_{0};
    mutable std::mutex mutex_;
};

} // namespace bokbok

#endif // BOKBOK_RAW_AUDIO_WRITER_H
