cmake_minimum_required(VERSION 3.22.1)

project("native-recorder")

# Add Oboe library
find_package(oboe REQUIRED CONFIG)

# Set include paths for WebRTC, Aec3Processor, and RNNoise
include_directories(
    .
    ./webrtc
    ./webrtc/api
    ./webrtc/audio_processing
    ./webrtc/base
    ./webrtc/base/rtc_base
    ./webrtc/base/system_wrappers/include
    ./webrtc/base/abseil
    ./webrtc/base/jsoncpp/include
    ./webrtc/common_audio/include
    ./rnnoise
)

# WebRTC Definitions for ARM64
add_definitions(-DWEBRTC_POSIX)
add_definitions(-DWEBRTC_ANDROID)
add_definitions(-DWEBRTC_ARCH_ARM64)
add_definitions(-DWEBRTC_HAS_NEON)
# Let arch.h determine endianness to avoid redefinition warnings
# add_definitions(-DWEBRTC_ARCH_LITTLE_ENDIAN)
add_definitions(-DWEBRTC_APM_DEBUG_DUMP=0)
add_definitions(-DWEBRTC_AUDIO_PROCESSING_ONLY)

# RNNoise definitions - use fixed point for better mobile performance
add_definitions(-DRNNOISE_BUILD)
add_definitions(-DCOMPILE_OPUS)
add_definitions(-DTRAINING=0)

# Suppress warnings that break builds on some NDK versions (Abseil/WebRTC legacy code)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fno-aligned-new -Wno-deprecated-builtins -Wno-error=unused-variable -Wno-error=format -Wno-unused-parameter")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -Wno-error=format -Wno-unused-parameter -Wno-null-dereference")

# Gather all necessary webrtc source files
file(GLOB_RECURSE WEBRTC_SOURCES
    "webrtc/api/*.cc"
    "webrtc/audio_processing/*.cc"
    "webrtc/audio_processing/*.c"
    "webrtc/base/*.cc"
    "webrtc/base/*.c"
)

# Gather RNNoise source files
set(RNNOISE_SOURCES
    rnnoise/denoise.c
    rnnoise/kiss_fft.c
    rnnoise/celt_lpc.c
    rnnoise/pitch.c
    rnnoise/rnn.c
    rnnoise/rnn_data.c
)

# Aggressively remove test files, benchmarks, mocks, stubs and architecture-specific files
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Tt]est.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Uu]nittest.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Bb]enchmark.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Mm]ock.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Ff]ake.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Ss]tub.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Dd]ummy.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*nonprod.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Pp]ffft.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Cc]pu_features_android.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Cc]pu_features_linux.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Ww]avreader.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Ww]avwriter.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Ww]av_file.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Ww]av_header.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/test/.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/testing/.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*[Jj]son\\.cc$")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/jsoncpp/.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/rnnoise/.*")

# Exclude standalone utility programs that contain 'main' or are for statistical tests
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/print_hash_of\\.cc$")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/gaussian_distribution_gentables\\.cc$")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*seed_salting_sequence_generator.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/simple\\.cc$")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/chi_square\\.cc$")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/distribution_test_util\\.cc$")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*/nanobenchmark\\.cc$")

# Exclude architecture-specific files that don't match ARM64
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_sse2?.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_avx2?.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_mips?.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_mmi.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_msvc.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_ios.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_mac.*")
list(FILTER WEBRTC_SOURCES EXCLUDE REGEX ".*_win.*")

add_library(
    native-recorder
    SHARED
    NativeRecorder.cpp
    Aec3Processor.cpp
    NativeAudioMixer.cpp
    RnNoiseProcessor.cpp
    ${RNNOISE_SOURCES}
    ${WEBRTC_SOURCES}
)

# Search for libraries in the system
find_library(log-lib log)
find_library(android-lib android)
find_library(mediandk-lib mediandk)

target_link_libraries(
    native-recorder
    ${log-lib}
    ${android-lib}
    ${mediandk-lib}
    oboe::oboe
)

