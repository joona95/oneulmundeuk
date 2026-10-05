#!/usr/bin/env bash
# 개발 기기 준비 / 상태 확인 (adb). Mac에서 실행. 모델은 앱(APK)에 넣지 않고 /data/local/tmp에만 둔다.
#   ./device.sh info                    # 기기 · CPU features · RAM
#   ./device.sh find-ollama-blob        # Ollama qwen3.5:2b-q4_K_M의 GGUF blob 경로 출력
#   ./device.sh push-bin                # .build/install-android/{bin,lib} → 기기
#   ./device.sh push-model <gguf>       # 모델 → 기기 (models/qwen3.5-2b-q4_K_M.gguf)
#   ./device.sh smoke                   # llama-simple로 모델 로드만 확인 (아키텍처 지원 여부)
#   ./device.sh bench                   # llama-bench: prompt processing / generation 원시 속도
#   ./device.sh clean                   # 기기에서 PoC 파일 삭제
set -euo pipefail
cd "$(dirname "$0")"
DIR=/data/local/tmp/qwen-poc
MODEL=$DIR/models/qwen3.5-2b-q4_K_M.gguf
OLLAMA_TAG="${OLLAMA_TAG:-qwen3.5:2b-q4_K_M}"
cmd="${1:-}"; shift || true

case "$cmd" in
  info)
    adb shell getprop ro.product.model
    adb shell getprop ro.soc.model 2>/dev/null || true
    adb shell getprop ro.build.version.release
    adb shell "grep -m1 Features /proc/cpuinfo; grep -c ^processor /proc/cpuinfo; grep -E 'MemTotal|MemAvailable' /proc/meminfo"
    echo "→ Features에 i8mm이 있으면 ARM_ARCH=armv8.6-a+dotprod+i8mm, 없고 asimddp만 있으면 기본값(armv8.2-a+dotprod+fp16)으로 빌드"
    ;;
  find-ollama-blob)
    from=$(ollama show --modelfile "$OLLAMA_TAG" | awk '/^FROM /{print $2; exit}')
    echo "$from"; ls -l "$from"
    ;;
  push-bin)
    adb shell mkdir -p $DIR/models
    adb push .build/install-android/bin $DIR/
    adb push .build/install-android/lib $DIR/
    adb push .build/install-android/BUILD_INFO.txt $DIR/
    adb shell "chmod 755 $DIR/bin/*"
    ;;
  push-model)
    src="${1:?GGUF 경로}"
    adb shell mkdir -p $DIR/models
    adb push "$src" $MODEL
    shasum -a 256 "$src" | tee .build/model.sha256
    adb shell ls -l $MODEL
    ;;
  smoke)
    # 공식 docs/android.md 예시와 같은 llama-simple: 모델이 이 llama.cpp에서 로드되는지만 본다
    adb shell "cd $DIR && LD_LIBRARY_PATH=lib ./bin/llama-simple -m $MODEL -n 8 'hello' 2>&1 | tail -40"
    ;;
  bench)
    # pp400 ≈ judge_v1 입력 1건, tg50 ≈ 출력 1건 (Mac 실험 평균 404 / 51 토큰)
    adb shell "cd $DIR && LD_LIBRARY_PATH=lib ./bin/llama-bench -m $MODEL -p 400 -n 50 -r 3 -o md 2>&1" | tee "runs/bench-$(date +%Y%m%d-%H%M%S).md"
    ;;
  clean)
    adb shell rm -rf $DIR
    ;;
  *)
    sed -n '2,10p' "$0"; exit 1 ;;
esac
