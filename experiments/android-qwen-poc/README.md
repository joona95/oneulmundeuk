# Android on-device Qwen3.5 2B PoC (judge_v1) — **CLOSED (2026-10-05)**

목적: Mac/Ollama에서 쓴 `qwen3.5:2b-q4_K_M` judge_v1이 **실제 Android 폰 안에서** 현실적인 속도·메모리로 도는지 측정한다.
production 앱(`app/`)과 `experiments/related/`는 건드리지 않는다. 모델은 APK에 넣지 않고 개발 기기의 `/data/local/tmp`에만 둔다.

이 PoC는 여기서 종료한다. 추가 모델 · prompt · runtime 최적화는 하지 않는다. 폐기하지 않고, 나중에 완전 로컬 추론을 다시 고를 때의 **feasibility baseline**으로 보존한다.

## 최종 결과

| 항목 | 값 |
| --- | --- |
| 기기 | SM-S948N · SoC SM8850 · Android 16 (RAM 약 11 GB) |
| 모델 | Qwen3.5 2B Q4_K_M · Ollama `qwen3.5:2b-q4_K_M`과 **같은 GGUF 파일** (1,274,396,992 bytes, sha256 `20cb277f0967ace47b0b5d5658e5e494a88937f7378b74b5a266496400938f4c`) |
| runtime | llama.cpp b10456 (`f275595`) · CPU only · `-march=armv8.6-a+dotprod+i8mm` · `llama-server -c 2048 -n 192 --temp 0 --seed 7 -np 1 --jinja` |
| judge | `experiments/related/prompts/judge_v1.txt` (sha256[:16] `553ab6176c0293f9`) · temperature 0 · seed 7 · json_schema · `enable_thinking=false` |
| 모델 로드 | 2.6 s |
| frozen benchmark | **157/157 성공** · crash / OOM 없음 · thinking 출력 0건 |
| 전체 inference | 511.7 s (157쌍 연속, reload 없음) |
| pair latency | median 3.12 s · p90 4.31 s · max 6.75 s (prompt 평균 698 ms · generation 평균 2,527 ms) |
| peak RSS (VmHWM) | 2,737 MB |
| llama-bench (참고) | pp400 89 tok/s · tg50 31 tok/s |

제품 지표 (label 2만 · e5 순서 · 최대 5개, `results-android-judge_v1.md`):

| metric | e5 Top5 | Mac judge_v1 | Android judge_v1 |
| --- | --- | --- | --- |
| Good@5 | .71 | .58 | .64 |
| nDCG@5 | .69 | .63 | .68 |
| Worth | .39 | .39 | .42 |
| Bad | .44 | .26 | .25 |
| Top1=0 | .41 | .38 | .25 |
| label 2 shown | 33 | 27 | 30 |
| label 0 shown | 37 | 17 | 16 |
| F7 zero return | 0/4 | 1/4 | 1/4 |

- Mac/Android label 일치는 **107/157 (68%) · Cohen's κ .44**로 낮다. 같은 모델 파일 · prompt라도 엔진(Ollama 자체 엔진 vs llama.cpp CPU)의 수치 차이로 greedy 출력이 갈라진다.
- 그러나 제품 지표는 유지되거나 일부 개선됐다 (Bad .26 → .25, Good@5 .58 → .64, Top1=0 .38 → .25, F7 1/4 유지). **runtime parity 자체는 blocker가 아니다.**
  판단 기준은 parity 숫자가 아니라 frozen benchmark의 제품 지표(Bad · Good@5 · F7 · failure case)다.
- dataset v1.1은 여러 실험에 쓴 development benchmark라, 위 수치는 일반화 성능이 아니라 runtime 간 비교로만 읽는다.

## 결론

1. **Qwen3.5 2B의 Android 완전 로컬 추론은 기술적으로 가능하다.** (서버 없이 폰 CPU에서 157/157, crash/OOM 없음)
2. **e5 candidate retrieval → LLM resurfacing-value judge → label 2 only → e5 ordering → max 5 구조는 Android runtime에서도 유효하다.** 제품 지표가 Mac 결과와 같은 수준이다.
3. **다만 현재 production integration은 보류한다.** 모델 약 1.27 GB, peak RSS 약 2.74 GB, Top 30 pairwise 판정 약 1분 이상(3.3 s × 30 ≈ 98 s), 지속적인 CPU 부하, 기기별 성능 편차.
4. 완전 로컬은 제품의 필수 가치가 아니므로, 다음 단계에서는 **local storage + local e5 + stateless inference API**를 우선 검토한다.
5. 이 PoC는 폐기하지 않는다. 향후 완전 로컬 inference를 다시 선택할 경우의 feasibility baseline으로 보존한다.

> **후속 결정 (M5-4, 2026-10-05)**: Modal Qwen3.5-4B 서버 실험(`experiments/modal-qwen-4b/`) 결과 server inference가 2B보다 나은 품질을 보이지 않아,
> 위 3 · 4는 대체됐다. production은 **Android local inference (Room + local e5 + local Qwen3.5-2B Q4_K_M)**로 확정했고, 이 PoC 구성을 production에 통합한다.
> 저장을 막지 않는 background 실행 · 사용자 동의 후 모델 다운로드로 위 3의 부담을 다룬다. 자세한 내용: `docs/m5-related-decision.md`.

## 산출물

| 파일 | 내용 |
| --- | --- |
| `build_llamacpp_android.sh` · `device.sh` | llama.cpp Android cross-compile · 기기 push / smoke / bench |
| `poc_judge.py` | 기기의 llama-server로 judge_v1 판정 · 측정 (`session --tasks all` = frozen 157쌍) |
| `android_eval.py` | 157쌍 결과를 Mac judge_v1과 같은 선택 규칙 · 지표로 평가 (모델 호출 없음) |
| `test_poc_judge.py` · `test_android_eval.py` | fake server / fake session 테스트 |
| `results-android-judge_v1.md` | 157쌍 최종 리포트 (성능 · 제품 지표 · failure case · F7 · confusion · Mac/Android 일치) |
| `runs/session-20261005-164627.jsonl` (+ `-server.log`, `-summary.md`) | **157쌍 raw 결과** (호출별 label · reason · latency · prompt/gen timing · 메모리, meta에 기기 · build 정보) |
| `runs/android-judge_v1-pairs.csv` | pair id · case · 정답 · Mac label · Android label · latency · timing |
| `runs/llm-android-qwen3.5-2b-q4_K_M-judge_v1.jsonl` | 같은 결과를 `llm_judge.py` 형식으로 변환한 것 (`compare_judges.py`로도 읽힘) |
| `runs/session-20261005-163430*` · `runs/bench-20261005-163253.md` | 첫 PoC 측정 (1 · 10 · 30쌍 latency, parity 10쌍, llama-bench) |

저장소에 넣지 않는 것 (`.gitignore`): `.build/` (llama.cpp 소스 · Android 빌드 · 바이너리), `*.gguf` (모델 1.27 GB). 모델은 Mac의 Ollama blob을 그대로 쓰므로 `./device.sh find-ollama-blob`으로 찾고 sha256으로 확인한다.
raw `session-*.jsonl`의 meta에는 실행 당시 NDK 경로가 그대로 기록돼 있다 (raw 기록이라 고치지 않음).

재현:
```bash
cd experiments/android-qwen-poc
chmod +x *.sh
ARM_ARCH=armv8.6-a+dotprod+i8mm LLAMA_CPP_REF=b10456 ANDROID_NDK=<Android SDK>/ndk/<version> ./build_llamacpp_android.sh
./device.sh push-bin
./device.sh push-model "$(./device.sh find-ollama-blob | head -1)"   # sha256 20cb277f… 확인
python3 poc_judge.py session --tasks all
python3 android_eval.py --session runs/session-<새 시각>.jsonl --out <새 리포트>.md --csv <새 csv> --converted <새 jsonl>
```
(greedy라도 llama.cpp 버전 · CPU 경로가 다르면 label이 바뀔 수 있다. 기존 결과 파일은 덮어쓰지 않는다.)

## 1. runtime 선택: llama.cpp (CPU, 공식 Android cross-compile)

| 후보 | Qwen3.5 2B | 판단 |
| --- | --- | --- |
| **llama.cpp** | 공식 지원. `src/llama-arch.cpp`에 `qwen35` / `qwen35moe` 아키텍처가 있고 GGUF로 실행 | **선택** |
| LiteRT-LM | 공식 지원 모델 목록에 없음 (Qwen2.5 · Qwen3-0.6B까지). 공식 이슈 #1658에서 maintainer가 "still working on Qwen3.5 support"라고 답함. HF의 Qwen3.5 `.litertlm`은 커뮤니티 변환본 | 제외 |

- 방식: llama.cpp `docs/android.md`의 NDK cross-compile → `llama-server` / `llama-bench` / `llama-simple`을 `adb push` → 기기 안에서 실행.
  Mac은 `adb forward`로 요청만 보낸다. **추론은 전부 폰 CPU에서** 일어난다.
- 앱 안(JNI)이 아니라 shell 프로세스로 돌리는 이유: 가장 단순하고, 앱 빌드(NDK/CMake)를 바꾸지 않고, 같은 llama.cpp 코드라 속도·메모리 측정이 그대로 유효하다.
  앱에 넣는 경로는 공식 `examples/llama.android`(Kotlin + JNI, `GGML_BACKEND_DL` / `GGML_CPU_ALL_VARIANTS`)가 있다 — 측정 결과를 본 뒤의 일이다.
- GPU 제외: Vulkan backend에 Qwen3.5의 SSM 계열 연산이 없다는 공식 이슈(#19957)가 있어 CPU로 고정한다.

## 2. 준비 (Mac)

필요: Android Studio SDK Manager의 **NDK**, `cmake`, `adb`, USB 디버깅을 켠 폰.

```bash
cd experiments/android-qwen-poc
chmod +x *.sh                            # 실행 권한 (처음 한 번)
./device.sh info                         # CPU Features 확인 (i8mm 있으면 아래 ARM_ARCH 권장)
ANDROID_NDK=~/Library/Android/sdk/ndk/<ver> ./build_llamacpp_android.sh
#   i8mm 지원 폰: ARM_ARCH=armv8.6-a+dotprod+i8mm ./build_llamacpp_android.sh
./device.sh push-bin
```

### 모델 파일 (기기: `/data/local/tmp/qwen-poc/models/qwen3.5-2b-q4_K_M.gguf`)

A. **Ollama가 쓴 같은 파일을 먼저 시도** (가중치 · 양자화가 Mac 실험과 완전히 같음)
```bash
./device.sh find-ollama-blob             # ~/.ollama/models/blobs/sha256-… 경로
./device.sh push-model <위 경로>
./device.sh smoke                        # llama.cpp가 이 파일을 로드하는지
```
Ollama의 qwen3.5 GGUF는 llama.cpp와 메타데이터 형식이 다르다는 보고가 있다(ollama#14503: `head_count_kv` 배열 vs 스칼라).
로드에 실패하면 B로 간다.

B. 공식 가중치 `Qwen/Qwen3.5-2B` → llama.cpp 변환 → Q4_K_M (imatrix 없이, Ollama 기본 양자화와 같은 방식)
```bash
python3 -m pip install -r .build/llama.cpp/requirements/requirements-convert_hf_to_gguf.txt
hf download Qwen/Qwen3.5-2B --local-dir .build/hf/Qwen3.5-2B
python3 .build/llama.cpp/convert_hf_to_gguf.py .build/hf/Qwen3.5-2B --outtype f16 --outfile .build/qwen3.5-2b-f16.gguf
cmake -S .build/llama.cpp -B .build/build-mac && cmake --build .build/build-mac --target llama-quantize -j
.build/build-mac/bin/llama-quantize .build/qwen3.5-2b-f16.gguf .build/qwen3.5-2b-q4_K_M.gguf Q4_K_M
./device.sh push-model .build/qwen3.5-2b-q4_K_M.gguf && ./device.sh smoke
```
B를 쓰면 가중치 파일이 Ollama와 다르다. 결과 기록에 A/B 중 무엇을 썼는지 남긴다 (`.build/model.sha256`).

## 3. 측정

```bash
./device.sh bench                                    # 원시 속도: pp400(입력 1건 크기) · tg50(출력 1건 크기), 3회
python3 poc_judge.py dry-run                         # 보낼 요청 · parity pair 확인 (기기 불필요)
python3 poc_judge.py session --tasks one,bench10,bench30,parity
python3 poc_judge.py session --tasks one --current "현재 기록" --past "과거 기록"   # 임의 1쌍
```

`session`은 llama-server를 **한 번** 띄우고, 같은 모델 instance로 task들을 연속 실행한 뒤 종료한다 (reload 없음).

| 항목 | 측정 방법 |
| --- | --- |
| model load | 서버 프로세스 시작 → `/health` 200까지 (Mac 기준 wall time, warmup 포함) |
| 1 pair latency | `one`: 요청~응답 wall time |
| prompt processing / generation | llama-server 응답의 `timings.prompt_ms / prompt_n`, `predicted_ms / predicted_n` |
| 10 / 30 pair 연속 | `bench10`, `bench30`: dataset v1.1의 e5 순서 앞 N쌍 (같은 현재 기록이 연속 → 실제 앱처럼 prefix cache가 쓰임) |
| 메모리 | 매 호출 후 `/proc/<pid>/status`의 VmRSS / **VmHWM(peak RSS)** |
| crash / OOM | 매 호출 후 프로세스 생존 확인, 죽으면 logcat의 lmkd 줄을 기록하고 중단 |
| 결과 일치 | `parity`: 아래 10쌍의 Mac/Ollama judge_v1 label vs Android label |

parity 10쌍은 결과를 보기 전에 규칙으로 고정했다 (case마다 1쌍, 서로 다른 query, candidate id가 가장 작은 것):
q01-g repeat_low_value(0) · q03-e lexical_trap(0) · q02-g unrelated(0) · q04-g ambiguous(1) · q05-b resolve_action(2) ·
q11-a worry_outcome(2) · q08-h implicit_link(2) · q06-d change(2) · q07-a reversal(2) · q09-h recurring(2).
목적은 새 성능 평가가 아니라 runtime이 바뀌어 label이 크게 달라지는지 확인하는 것이다.

결과: `runs/session-<시각>.jsonl`(호출별), `-server.log`, `-summary.md`(요약 · parity 표), `runs/bench-*.md`.

## 4. Ollama와 다른 점 (숨기지 않고 기록)

| 항목 | Mac/Ollama judge_v1 | Android PoC |
| --- | --- | --- |
| 실행 엔진 | Ollama (qwen3.5는 Ollama 자체 엔진) | llama.cpp CPU (arm64, `-march`는 BUILD_INFO.txt) → 같은 greedy라도 수치 차이로 출력이 갈라질 수 있음 |
| 가중치 파일 | Ollama blob | A면 같음 / B면 공식 가중치를 직접 Q4_K_M 양자화 |
| chat template · thinking off | Ollama renderer + `think: false` | GGUF 내장 jinja template + `chat_template_kwargs.enable_thinking=false` (응답에 `reasoning_content`가 오면 경고) |
| JSON 강제 | Ollama `format` (schema) | `response_format.json_schema` → llama.cpp grammar. schema는 같음 (reason → label) |
| temperature / seed | 0 / 7 | 0 / 7 (greedy라 seed는 사실상 무관) |
| context / 출력 길이 | num_ctx 2048 / num_predict 192 | `-c 2048` / `-n 192`, `max_tokens 192` |
| prompt cache | Ollama prefix 재사용 | `cache_prompt` (prefix 재사용). 호출마다 실제 처리 토큰 수(`prompt_n`)를 기록 |
| 실행 위치 | Mac (Metal GPU) | 폰 shell 프로세스 (앱 프로세스 아님 → 앱 메모리 제한 · 백그라운드 제한은 측정 범위 밖) |
| 출력 검증 | `llm_judge.judge_once` | 같은 규칙 (`strong_judge.validate`, judge_once와 동일함을 테스트로 확인) |

## 5. 하지 않은 것 (PoC 범위 밖)

e5 Android · Room embedding cache · RelatedResult table · WorkManager · production finder 연결 · Play 배포/모델 packaging · prompt tuning · 새 benchmark.
