#!/usr/bin/env bash
# llama.cpp를 Android arm64용으로 cross-compile (공식 docs/android.md 방식). Mac에서 실행.
#   ANDROID_NDK=~/Library/Android/sdk/ndk/<version> ./build_llamacpp_android.sh
# 환경변수
#   LLAMA_CPP_REF  고정할 llama.cpp tag (기본 b10456 · qwen35 아키텍처 포함)
#   ARM_ARCH       -march 값 (기본 armv8.2-a+dotprod+fp16). `./device.sh info`의 Features에 i8mm가 있으면
#                  armv8.6-a+dotprod+i8mm 권장 (Q4_K matmul이 빨라짐). 결과 기록에 이 값을 남긴다.
# 결과: .build/install-android/{bin,lib}  (git에 넣지 않음)
set -euo pipefail
cd "$(dirname "$0")"
REF="${LLAMA_CPP_REF:-b10456}"
ARCH="${ARM_ARCH:-armv8.2-a+dotprod+fp16}"
NDK="${ANDROID_NDK:-}"
if [[ -z "$NDK" ]]; then
  base="${ANDROID_HOME:-$HOME/Library/Android/sdk}/ndk"
  NDK="$(ls -d "$base"/* 2>/dev/null | sort -V | tail -1 || true)"
fi
[[ -f "$NDK/build/cmake/android.toolchain.cmake" ]] || { echo "Android NDK를 찾을 수 없음. Android Studio > SDK Manager > SDK Tools > NDK 설치 후 ANDROID_NDK 지정"; exit 1; }
command -v cmake >/dev/null || { echo "cmake 필요 (brew install cmake 또는 SDK Manager의 CMake)"; exit 1; }

mkdir -p .build
if [[ ! -d .build/llama.cpp ]]; then
  git clone --depth 1 --branch "$REF" https://github.com/ggml-org/llama.cpp .build/llama.cpp
fi
( cd .build/llama.cpp && echo "llama.cpp $(git describe --tags --always)" )

cmake -S .build/llama.cpp -B .build/build-android \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-28 \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_NATIVE=OFF \
  -DGGML_CPU_ARM_ARCH="$ARCH" \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -DLLAMA_OPENSSL=OFF
cmake --build .build/build-android --config Release -j "$(sysctl -n hw.ncpu 2>/dev/null || nproc)"
cmake --install .build/build-android --prefix .build/install-android --config Release
cat > .build/install-android/BUILD_INFO.txt <<INFO
llama.cpp ref: $REF ($(cd .build/llama.cpp && git rev-parse --short HEAD))
ndk: $NDK
march: $ARCH
flags: GGML_NATIVE=OFF GGML_OPENMP=OFF GGML_LLAMAFILE=OFF LLAMA_OPENSSL=OFF android-28 arm64-v8a (CPU only)
INFO
cat .build/install-android/BUILD_INFO.txt
ls .build/install-android/bin | grep -E 'llama-(server|bench|cli)$'
