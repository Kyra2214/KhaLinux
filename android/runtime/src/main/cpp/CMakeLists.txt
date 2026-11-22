cmake_minimum_required(VERSION 3.22.1)
project(khalinux_pty C)

add_library(khalinux_pty SHARED native_pty.c)
target_compile_features(khalinux_pty PRIVATE c_std_11)
target_compile_options(khalinux_pty PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)

if(NOT ANDROID)
    target_link_libraries(khalinux_pty PRIVATE util)
endif()
