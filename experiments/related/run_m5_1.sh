#!/usr/bin/env bash
# M5-1: 두 모델로 text-only cosine baseline을 돌리고 results.md를 만든다.
# 인터넷(Hugging Face)이 되는 PC에서 실행. 처음 한 번 모델 다운로드: e5-small-ko ≈0.5GB, KURE-v1 ≈2.3GB.
set -euo pipefail
cd "$(dirname "$0")"
if [ ! -d .venv ]; then python3 -m venv .venv; fi
. .venv/bin/activate
pip install -q --upgrade pip
pip install -q -r requirements.txt
python eval.py --check > /dev/null
python embed.py --model e5-small-ko
python embed.py --model kure
python report.py --small runs/e5-small-ko.json --big runs/kure.json --out results.md
echo "done: runs/e5-small-ko.json runs/kure.json results.md"
