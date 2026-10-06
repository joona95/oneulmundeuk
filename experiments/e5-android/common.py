"""Shared bits for the e5 Android artifact experiment (M6-8, part 1). See README.md."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXP = HERE.parent
OUT = HERE / "out"
MODEL_ID = "dragonkue/multilingual-e5-small-ko-v2"
REVISION = "fcfc26bf355882620c48df58be112275bd756f50"
MAX_SEQ = 512  # sentence_bert_config.json max_seq_length


def load_st(model_dir):
    from sentence_transformers import SentenceTransformer
    m = SentenceTransformer(str(model_dir), device="cpu")
    assert m.max_seq_length == MAX_SEQ, m.max_seq_length
    return m


EDGE_CASES = [
    "오늘은 좀 피곤하다",
    "회의에서 내 의견을 말하는 게 예전보다 덜 무섭다. 오늘도 그냥 말함",
    "React Native로 사이드 프로젝트 시작! 근데 TypeScript 설정이 너무 귀찮다",
    "3시 30분에 PT, 2026-10-07 (수) 예약 완료 — 비용 ₩55,000 / 10회",
    "e-mail: test@example.com, URL https://example.com/a?b=1&c=2 #해시태그 @멘션",
    "ＦＵＬＬ　ｗｉｄｔｈ　１２３ ｶﾀｶﾅ ①②③ ﬁ ㎏ ™",
    "ㄱㅏㄴㅏ 자모만 ㅋㅋㅋㅋ ㅠㅠ",
    "😀 이모지 👍🏽 가족 👨‍👩‍👧 국기 🇰🇷",
    "  앞뒤 공백   여러 칸    있음  ",
    "줄바꿈\n있는\t탭 문장\r\n",
    "<s> 특수 토큰 문자열 </s> <mask>",
    "English only sentence with numbers 12345 and symbols !?.,;:",
    "中文和日本語のまじり 한국어",
    "a",
    "",
    "e\u0301 n\u0303 decomposed accents · café naïve",
    "\u1100\u1161\u11a8 conjoining jamo · ㅤfiller",
    "no\u00a0break\u2009thin\u200bzero-width\u3000ideographic",
    "control\u0007bell\u0000nul\u00adsoft-hyphen",
    "ภาษาไทย العربية हिन्दी Ελληνικά Русский",
    "𠀋𡈽𡌛 rare CJK ext-B · 𝔘𝔫𝔦𝔠𝔬𝔡𝔢 math alnum",
    "‘따옴표’ “쌍따옴표” … — – ・ ~ ％ ＃",
    "\n\n\n",
    "   ",
    "아주 긴 문장 " + "오늘 하루는 생각보다 길었고 일이 많았지만 그래도 저녁에 산책을 하면서 마음이 조금 풀렸다. " * 40,
]


def fixture_texts():
    """(id, text) — edge cases + every M5 related text ("query: ") + S1 search texts (query / passage)."""
    out = [(f"edge{i:02d}", t) for i, t in enumerate(EDGE_CASES)]
    out += [(f"edge{i:02d}-q", "query: " + t) for i, t in enumerate(EDGE_CASES)]
    ds = json.loads((EXP / "related" / "dataset.json").read_text(encoding="utf-8"))
    seen = set()
    for q in ds["queries"]:
        for t in [q["text"]] + [c["text"] for c in q["candidates"]]:
            if t not in seen:
                seen.add(t)
                out.append((f"m5-{len(seen):03d}", "query: " + t))
    s1 = EXP / "search" / "dataset.json"
    if s1.exists():
        s = json.loads(s1.read_text(encoding="utf-8"))
        out += [(f"s1-{r['id']}", "passage: " + r["text"]) for r in s["records"]]
        out += [(f"s1-{q['id']}", "query: " + q["text"]) for q in s["queries"]]
    return out
