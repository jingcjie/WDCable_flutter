cmake_minimum_required(VERSION 3.22.1)

project(wdcable_audio_opus)

add_library(opus SHARED IMPORTED)
set_target_properties(
    opus
    PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libopus.so
)

add_library(wdcable_opus_jni SHARED wdcable_opus_jni.cpp)

target_link_libraries(wdcable_opus_jni PRIVATE opus log)
