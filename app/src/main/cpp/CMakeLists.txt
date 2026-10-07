# libqwen_judge.so — judge_v1 on Qwen3.5-2B through llama.cpp (M6-7). arm64-v8a only (app/build.gradle.kts).
#
# llama.cpp is the unmodified submodule third_party/llama.cpp pinned at b10456 (f275595dd16f7ed3d644d4d8159b14b305960479),
# the exact version of the Android PoC (experiments/android-qwen-poc). Build switches = the PoC build script
# (build_llamacpp_android.sh, BUILD_INFO.txt): CPU only, GGML_NATIVE=OFF, -march=armv8.6-a+dotprod+i8mm, no OpenMP,
# no llamafile, no OpenSSL. Static libraries linked into this one JNI library (no other .so to load / version).
cmake_minimum_required(VERSION 3.22.1)
project(qwen_judge LANGUAGES C CXX)

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp")
if(NOT EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    message(FATAL_ERROR "third_party/llama.cpp is missing: git submodule update --init third_party/llama.cpp")
endif()

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)
set(GGML_CPU_ARM_ARCH      "armv8.6-a+dotprod+i8mm" CACHE STRING "" FORCE)
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL        OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL          OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON     ON  CACHE BOOL "" FORCE)  # chat template (jinja) · json_schema → grammar · sampling = llama-server's code
set(LLAMA_BUILD_TESTS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS      OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES   OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP        OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI         OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE       OFF CACHE BOOL "" FORCE)

add_subdirectory("${LLAMA_CPP_DIR}" llama.cpp EXCLUDE_FROM_ALL)

add_library(qwen_judge SHARED qwen_judge_jni.cpp)
target_compile_features(qwen_judge PRIVATE cxx_std_17)
target_link_libraries(qwen_judge PRIVATE llama-common llama ggml log)
# keep only what the wrapper reaches; do not re-export llama.cpp symbols
target_link_options(qwen_judge PRIVATE "-Wl,--gc-sections" "-Wl,--exclude-libs,ALL")
