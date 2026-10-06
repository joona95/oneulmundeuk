# M6-8: e5 Android artifact — 확정 · 검증 (production = INT8-embrows)

목표: M5 benchmark의 `dragonkue/multilingual-e5-small-ko-v2` @ `fcfc26bf355882620c48df58be112275bd756f50`과 **같은 embedding**을
Android에서 만들 수 있는 production artifact를 만들고 검증한다. 앱 runtime 연결은 하지 않는다 (다음 단계).
생성물(`out/`)은 git에 넣지 않는다 (`.gitignore`).

## 기준 (M5 / S1 그대로)

| 항목 | 값 | 출처 |
| --- | --- | --- |
| model · revision | `dragonkue/multilingual-e5-small-ko-v2` @ `fcfc26bf3558…` (HF cache blob sha256 = LFS oid 확인) | `related/embed.py`, `runs/*.json` |
| 구조 | sentence-transformers: Transformer(BertModel 12층 · hidden 384) → Pooling(mean) → Normalize | `modules.json`, `1_Pooling/config.json` |
| tokenizer | XLMRobertaTokenizerFast (`tokenizer.json`: Precompiled + Replace · Metaspace · Unigram 250,002 · `<s> A </s>`) | snapshot |
| max_seq_length | 512 (넘으면 앞에서부터 510 + 특수 토큰 2) | `sentence_bert_config.json` |
| dim · 정규화 | 384 · L2 (cosine = 내적) | `embed.py normalize_embeddings=True` |
| prefix | Related(기록 ↔ 기록): 양쪽 `query: ` · Explore(질문 ↔ 기록): `query: ` / `passage: ` | `related/embed.py`, `search/embed_search.py` |

## 선택: ONNX Runtime (Android) + 순수 Kotlin tokenizer

- ONNX Runtime: BERT 계열 encoder를 그대로 export · CPU arm64 · Python `onnxruntime`과 같은 엔진이라 host에서 Android와 같은 graph로 검증 가능.
  TFLite/LiteRT는 변환 단계가 하나 더 있고(BERT 동적 길이 처리 · 연산 지원 확인 필요) 이득이 분명하지 않아 구현하지 않았다.
- tokenizer: HF `tokenizers`(Rust)의 Android 공식 binding이 없고, native 의존성을 늘리지 않기 위해 **Kotlin으로 포팅**
  (`kotlin/XlmrTokenizer.kt` · `kotlin/Graphemes.kt`). tokenizer.json을 앱에서 JSON 파싱하지 않도록 같은 내용을 줄 단위 텍스트로 변환한
  artifact를 쓴다 (`make_tokenizer_artifact.py`, 예상 밖 설정이면 실패).

## FP32 artifact (기준선 · reference — production은 아래 INT8-embrows)

| 파일 | 역할 | bytes | SHA-256 |
| --- | --- | --- | --- |
| `e5-small-ko-v2-fcfc26bf.onnx` | BertModel + mean pooling + L2 normalize. 입력 `input_ids` · `attention_mask` (int64 [batch, seq], 동적), 출력 `sentence_embedding` float32 [batch, 384] | 470,234,464 | `1e794568bb8027ccee153b22423a738866ffc7c8062bcd182e021ef92910a8d2` |
| `e5-small-ko-v2-tokenizer.txt` | Kotlin tokenizer 입력 (vocab · score · charsmap · special token) | 8,080,014 | `6aab11c24ea1ecb59cf74fc8fc0f09f03129e3df249aa2aba47f13ed78cca893` |

- ONNX: opset 17, torch 2.5.1 legacy exporter, `token_type_ids` = 0을 graph 안에서 생성. 같은 환경에서 두 번 export → 같은 SHA-256.
- 불필요한 HF 파일(config.json · tokenizer_config.json · special_tokens_map.json · safetensors 등)은 포함하지 않는다.
- pooling · 정규화는 ONNX graph 안. 앱(Kotlin)이 할 일은 tokenize → `attention_mask`=1 → 실행 → 그대로 cosine(내적).

## 검증 결과 (2026-10-06, Linux aarch64 host: sentence-transformers 5.7.0 · transformers 4.57.6 · torch 2.5.1 · onnxruntime 1.23.2 · JDK 11 + kotlinc 2.1.0)

| 검사 | 결과 | 기준 |
| --- | --- | --- |
| tokenizer: Kotlin vs Python(ST가 모델에 넣는 ids) — fixture 304개 (edge 25 × 2 prefix + M5 146 + S1 80, 512 token 잘림 포함) | **304 / 304 동일** | 전부 동일 |
| tokenizer fuzz (seed 7 · 3,000 / seed 11 · 6,000 — 한글 · 자모 · 조합 jamo · 결합 문자 · ZWJ · 제어 문자 · 이모지 · 국기 · 태국어 · 아랍어 · 전각 · CJK 확장) | **3,000 / 3,000 · 6,000 / 6,000 동일** | 전부 동일 |
| embedding: ONNX vs sentence-transformers (같은 ids, 304 texts) | dim 384 · max \|diff\| **4.6e-7** · min cosine **0.99999988** · norm 0.9999996–1.0000004 | max \|diff\| ≤ 1e-4 · cosine ≥ 0.99999 |
| M5 Related Top-5 (17 queries) ONNX vs 저장된 M5 run | Top-5 순서 17/17 · 전체 순위 17/17 · max score diff 0.0001 (4자리 반올림 1단위) | Top-K 동일 |
| S1 Explore Top-10 (27 queries) ONNX vs 저장된 S1 run | Top-10 순서 27/27 · 전체 순위 27/27 · max score diff 0.0001 | Top-K 동일 |
| 참고: 지금 환경 ST vs 저장된 run | M5 17/17 · S1 27/27 · diff 0.0 | — |

처음 Kotlin 구현(java.text.BreakIterator)은 fuzz 3,000 중 8개가 달랐다 — 공백 + ZWJ · 결합 문자처럼 JDK 11 BreakIterator와 Rust
`unicode-segmentation`의 grapheme 경계가 다른 경우(Precompiled normalizer가 6 byte 미만 grapheme을 통째로 변환). UAX #29 규칙을
`Graphemes.kt`에 직접 구현해 0개가 됐다. 플랫폼 BreakIterator(Android 버전마다 ICU 다름)에 의존하지 않는다.

host 지연 (onnxruntime CPU, 1문장씩, 4 vCPU VM — **Android 수치 아님**): 8 token 2.9 ms · 48 token 11 ms · 128 token 23 ms · 512 token 99 ms.
Kotlin tokenizer: artifact load 약 0.3 s(JVM), 6,000문장 tokenize 0.5 s.

## 재현

```bash
# model dir = HF snapshot of fcfc26bf (config · tokenizer.json · model.safetensors · 1_Pooling …)
pip install "torch==2.5.1" "sentence-transformers>=5.1,<6" "transformers<4.58" onnx onnxruntime numpy
python3 make_tokenizer_artifact.py --model-dir $M
python3 make_reference.py --model-dir $M          # out/reference.json · out/tokens.tsv
python3 make_token_fuzz.py --model-dir $M          # out/tokens_fuzz.tsv
python3 export_onnx.py --model-dir $M              # out/e5-small-ko-v2-fcfc26bf.onnx
python3 parity.py                                  # out/parity.json
kotlinc kotlin/*.kt -include-runtime -d parity.jar  # kotlinc 2.1.0 (npm package kotlin-compiler@2.1.0)
java -jar parity.jar out/e5-small-ko-v2-tokenizer.txt out/tokens.tsv
java -jar parity.jar out/e5-small-ko-v2-tokenizer.txt out/tokens_fuzz.tsv
```

## INT8 비교 (host) — FP32는 그대로 둠

| artifact | 방식 | bytes | min cos vs FP32 ref | max \|diff\| | M5 Top-5 순서 (17) | S1 Top-10 집합 / 순서 (27) |
| --- | --- | --- | --- | --- | --- | --- |
| FP32 `…fcfc26bf.onnx` | — | 470,234,464 | 0.99999988 | 4.6e-7 | 17 | 27 / 27 |
| `int8-matmul` | ORT dynamic, MatMul per-channel | 406,918,588 | 0.97778 | 3.8e-2 | 3 | 7 / 0 |
| `int8-matmul-gather` | ORT dynamic, MatMul + Gather | 118,285,216 | 0.97144 | 4.0e-2 | 6 | 5 / 0 |
| `int8-gather` | ORT dynamic, Gather만 (표 전체 scale 1개) | 181,684,019 | 0.99916 | 6.3e-3 | 16 | 24 / 12 |
| **`int8-embrows`** | word embedding 표만 int8, **행마다 scale**, encoder FP32 | **183,192,536** | **0.99996** | **1.5e-3** | **17** | **27 / 24** |

- ORT dynamic quantization(`quantize_int8.py`, calibration 없는 가장 보수적인 ORT 방식)부터 시도했다. MatMul을 양자화하면
  (활성값 동적 양자화) 이 작은 encoder에서 순위가 크게 바뀐다 → 탈락. Gather만 양자화해도 ORT는 250,037행 표 전체에
  scale 1개(`DequantizeLinear` axis 없음)라 S1 순서가 12/27.
- 크기의 대부분(약 384 MB)이 word embedding 표 → 그 표만 **행(token)마다 scale**로 int8 (`quantize_embedding_rows.py`,
  대칭 `scale = max|row| / 127`). graph: `Gather(W_int8) → Cast → Mul(Gather(row_scale))`, opset 17 기본 op만. 행 복원 오차 max 5.3e-3.
- `int8-embrows`: tokenizer는 그대로(304/304). S1 순서가 바뀐 3개 질문은 모두 점수 차 ≤ 0.0006인 인접 쌍 교환, Top-10 집합 동일.
  S1 DCG@10 합 66.128 → 66.104. M5 전체 순위 17/17. host 지연은 FP32와 같은 수준 (목적은 크기 · 메모리).
- 다른 INT8 후보 파일(`out/*.int8-{matmul,matmul-gather,gather}.onnx`)은 비교 기록용 — 필요 없으면 지워도 된다 (git 밖).

## Android 실기기 결과 (2026-10, Samsung SM-S948N · SM8850 · arm64, ORT Android 1.25.0)

`E5DeviceCheckTest` (`am instrument`, connectedAndroidTest 아님) · 같은 기기 · 각각 cold.

| | FP32 | **INT8-embrows** |
| --- | --- | --- |
| model bytes | 470,234,464 | **183,192,536** |
| cold load (tokenizer + ORT session) | 1,614 ms | **1,117 ms** |
| VmRSS 증가 (load) | +492 MB | **+223 MB** |
| VmRSS 추론 후 | 720 MB | **450 MB** |
| 지연 ~41 token (warm median) | 13.9 ms | 13.0 ms |
| 지연 512 token | 177.9 ms | 175.2 ms |
| tokenizer vs host | 304 / 304 | 304 / 304 |
| embedding vs FP32 host reference | max \|diff\| 4.9e-7 | max \|diff\| 1.51e-3 · min cosine 0.99995585 |
| 비슷한 > 무관한 쌍 | 4 / 4 | 4 / 4 (PASS) |
| M5 Related Top-5 / S1 Explore Top-10 집합 (host 순위 비교) | 17/17 · 27/27 | 17/17 · 27/27 |

**결정: production e5 = `int8-embrows`.** 크기 39 %, RSS 증가 −269 MB(약 −55 %), cold load −31 %, 지연은 같음, 순위 품질 유지.
FP32는 reference · benchmark 용도로만 보존 (`RelatedModels`에는 넣지 않음).

## production artifact (`RelatedModels.E5` · `E5_TOKENIZER`, source = Unconfigured)

| artifact id / version | 파일 | bytes | SHA-256 |
| --- | --- | --- | --- |
| `multilingual-e5-small-ko-v2` / `fcfc26bf-int8-embrows-ab2d3fa7` | `e5-small-ko-v2-fcfc26bf.int8-embrows.onnx` | 183,192,536 | `ab2d3fa70720f6026106572b729606f1ad33257538984abe0fd262c81855e04a` |
| `multilingual-e5-small-ko-v2-tokenizer` / `fcfc26bf-6aab11c2` | `e5-small-ko-v2-tokenizer.txt` | 8,080,014 | `6aab11c24ea1ecb59cf74fc8fc0f09f03129e3df249aa2aba47f13ed78cca893` |

- 하나의 `ModelArtifact` = 파일 하나라서 tokenizer는 별도 artifact(별도 id 디렉터리). 번들 = e5 model + tokenizer + Qwen, 모두 verified일 때 Ready.
- 호스팅 · URL은 아직 없음 → `downloadAvailable` false, 다운로드 시작은 `SOURCE_NOT_CONFIGURED`로 안전하게 거절.
- `E5Embedder.MODEL_ID` = `<id>@<version>` — artifact가 바뀌면 저장된 embedding도 다른 모델로 취급.

## ONNX Runtime 1.25.0 + `mlas.disable_kleidiai=1` (유지)

- 처음 쓴 ORT Android 1.23.2는 SM-S948N(SM8850)에서 `E5DeviceCheckTest` 실행 중 `libonnxruntime.so` **SIGILL**로 앱 프로세스가 죽었다.
- 원인: ORT 1.23.x–1.24.2의 MLAS가 KleidiAI kernel을 쓰는데, SME가 있는 CPU에서 SME2 명령까지 실행한다. 이 기기는
  `/proc/cpuinfo` 기준 **sme=true · sme2=false** → 지원하지 않는 명령 (ORT issue #26377).
- 해결: ORT **1.25.0** + session option **`mlas.disable_kleidiai=1`** (`E5Embedder.load`). 기본 MLAS kernel만 써서 host parity와 같은 계열의 kernel.
  host parity 기준(Linux aarch64 VM의 Python onnxruntime 1.23.2 결과)은 바뀌지 않는다.
- ORT를 올리거나 이 옵션을 빼려면 SME-only 기기에서 `E5DeviceCheckTest`를 다시 통과시켜야 한다 (`E5DeviceCheckTest`가 sme/sme2를 로그로 남김).

## 기기 검증 방법

- app: `implementation(libs.onnxruntime.android)` (1.25.0), production `related/e5/` (`XlmrTokenizer` · `Graphemes` = 이 폴더 `kotlin/`과 동일 · `E5Embedder`).
  앱 pipeline에는 아직 연결하지 않음 (release = NoRelatedRuntime, debug = fake).
- `python3 make_device_fixture.py --model-dir $M` → `out/device_fixture.tsv` (reference 304개 ids + embedding · 비슷한/무관한 문장 쌍 4개)
- 파일은 `adb push` → `/data/local/tmp/e5` → `run-as app.oneulmundeuk.debug`로 앱 내부 `files/e5`에 복사
  (adb shell이 만든 `/sdcard/Android/data/<pkg>` 파일은 앱 uid가 못 읽을 수 있음). 파일이 없으면 skip.
- 기본 = production 파일(INT8-embrows, 허용치 max|diff| ≤ 3e-3 · cosine ≥ 0.9999). FP32 reference: `-e e5model e5-small-ko-v2-fcfc26bf.onnx -e maxabs 0.001`.
- 결과: logcat `E5Device` + 앱 내부 `files/e5/device_report-<model>.txt` (`run-as … cat`으로 회수).

```bash
adb shell am instrument -w -r -e class app.oneulmundeuk.E5DeviceCheckTest \
  app.oneulmundeuk.debug.test/androidx.test.runner.AndroidJUnitRunner
```

## INT8 재현

```bash
python3 quantize_embedding_rows.py   # out/e5-small-ko-v2-fcfc26bf.int8-embrows.onnx (FP32는 읽기만)
python3 parity.py --model out/e5-small-ko-v2-fcfc26bf.int8-embrows.onnx --report out/parity-int8-embrows.json
python3 quantize_int8.py             # (기록용) ORT dynamic 후보 3종
```

## 아직 아님

- 호스팅 · URL (source는 계속 Unconfigured), 다운로드 구현.
- semantic pipeline 연결 (M6-7/M6-9), Qwen runtime.
