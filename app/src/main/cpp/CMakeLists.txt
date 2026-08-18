cmake_minimum_required(VERSION 3.10.2)
project(patcher)

# ---- HDiffPatch include dirs (adjust if needed after you add submodule) ----
include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/hdiffpatch
        ${CMAKE_CURRENT_SOURCE_DIR}/hdiffpatch/libHDiffPatch
        ${CMAKE_CURRENT_SOURCE_DIR}/hdiffpatch/libHDiffPatch/HPatch
)

# --- Build libpatcher.so ---
add_library(
        patcher
        SHARED
        patcher_jni.cpp

        # ---- HDiffPatch patcher sources (common set) ----
        hdiffpatch/libHDiffPatch/HPatch/patch.c

        # CLI wrapper (hpatchz)
        hdiffpatch/hpatchz.c
        hdiffpatch/file_for_patch.c
)

find_library(log-lib log)

target_link_libraries(
        patcher
        ${log-lib}
)