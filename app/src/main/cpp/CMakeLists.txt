cmake_minimum_required(VERSION 3.22.1)

project("native-recorder")

# Add Oboe library
find_package(oboe REQUIRED CONFIG)

# WebRTC Definitions & Architecture checks
add_definitions(-DWEBRTC_POSIX)
add_definitions(-DWEBRTC_ANDROID)
add_definitions(-DWEBRTC_HAS_NEON)
add_definitions(-DWEBRTC_APM_DEBUG_DUMP=0)
add_definitions(-DWEBRTC_AUDIO_PROCESSING_ONLY)
add_definitions(-DCOMPILE_OPUS) # Needed for kiss_fft.c

# --- Centralized Architecture & Feature Block ---
if(${ANDROID_ABI} STREQUAL "arm64-v8a")
    add_definitions(-DWEBRTC_ARCH_ARM64 -DBOKBOK_64BIT -DBOKBOK_ENABLE_AI)
    set(ENABLE_ONNX ON)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_definitions(-DWEBRTC_ARCH_ARM)
    set(ENABLE_ONNX OFF)
else()
    # Fallback for x86_64 or others
    set(ENABLE_ONNX OFF)
endif()

# Common WebRTC Definitions
add_definitions(-DWEBRTC_POSIX -DWEBRTC_ANDROID -DWEBRTC_HAS_NEON -DWEBRTC_APM_DEBUG_DUMP=0 -DWEBRTC_AUDIO_PROCESSING_ONLY -DCOMPILE_OPUS)

# Conditionally add ONNX Runtime
if(ENABLE_ONNX)
    set(ONNX_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/onnxruntime-lib")
    include_directories(${ONNX_ROOT}/headers)

    add_library(onnxruntime SHARED IMPORTED)
    set_target_properties(onnxruntime PROPERTIES IMPORTED_LOCATION
        "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so")
endif()

# Set include paths for WebRTC AEC3
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/api
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/audio_processing
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/base
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/base/rtc_base
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/base/system_wrappers/include
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/base/abseil
    ${CMAKE_CURRENT_SOURCE_DIR}/webrtc/common_audio/include
)

# Suppress warnings that break builds on some NDK versions
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

set(NATIVE_RECORDER_SOURCES
    NativeRecorder.cpp
    PostProcessor.cpp
    Aec3Processor.cpp
    NativeAudioMixer.cpp
    rnnoise/kiss_fft.c  # Needed for DeepFilterNet's FFT 
    ${WEBRTC_SOURCES}
)

if(ENABLE_ONNX)
    list(APPEND NATIVE_RECORDER_SOURCES DeepFilterNet.cpp)
endif()

add_library(
    native-recorder
    SHARED
    ${NATIVE_RECORDER_SOURCES}
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

if(ENABLE_ONNX)
    target_link_libraries(native-recorder onnxruntime)
endif()
