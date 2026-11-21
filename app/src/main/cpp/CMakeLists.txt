cmake_minimum_required(VERSION 3.22.1)
project(tradepulse_native C)

add_library(execshim SHARED native_exec.c)

find_library(log-lib log)

target_link_libraries(execshim ${log-lib})
target_compile_options(execshim PRIVATE -Wall -Wextra)
