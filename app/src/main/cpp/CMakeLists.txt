cmake_minimum_required(VERSION 3.22.1)

project("native-recorder")

# Add Oboe library
# Note: We'll add this as a proper dependency in build.gradle.kts later, 
# for now we're setting up the structure.
find_package(oboe REQUIRED CONFIG)

add_library(
    native-recorder
    SHARED
    # List C++ source files here
    NativeRecorder.cpp
)

# Search for libraries in the system
find_library(log-lib log)
find_library(android-lib android)
find_library(mediandk-lib mediandk)

target_link_libraries(
    native-recorder
    # List libraries to link here
    ${log-lib}
    ${android-lib}
    ${mediandk-lib}
    oboe::oboe
)
