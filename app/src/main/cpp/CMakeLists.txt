cmake_minimum_required(VERSION 3.22.1)

project("qwen_engine")

add_library(qwen_engine SHARED
    native_engine.cpp
)

find_library(log-lib log)

target_link_libraries(
    qwen_engine
    ${log-lib}
)
