cmake_minimum_required(VERSION 3.22.1)
project("geyserlauncher")

add_library(geyserlauncher SHARED geyserlauncher.cpp)

target_compile_features(geyserlauncher PRIVATE cxx_std_17)

target_link_libraries(geyserlauncher
    android
    log
    dl
)
