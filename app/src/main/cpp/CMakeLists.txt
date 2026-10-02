cmake_minimum_required(VERSION 3.22.1)

project("qwen_engine")

add_library(qwen_engine SHARED
    native_engine.cpp
)

# Headers do llama.cpp são disponibilizados pelo workflow
# em app/src/main/cpp/llama/include
target_include_directories(qwen_engine PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/llama/include
    ${CMAKE_CURRENT_SOURCE_DIR}/ggml/include
    ${CMAKE_CURRENT_SOURCE_DIR}/common
)

# Bibliotecas nativas do llama.cpp que entram no APK
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so
)

add_library(llama-common SHARED IMPORTED)
set_target_properties(llama-common PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama-common.so
)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so
)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so
)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES
    IMPORTED_LOCATION
    ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so
)

find_library(log-lib log)

target_link_libraries(
    qwen_engine
    llama
    llama-common
    ggml
    ggml-base
    ggml-cpu
    ${log-lib}
)
