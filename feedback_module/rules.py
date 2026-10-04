"""Deterministic suggestion engine: labels -> fixed text from recommendations.json.

Nothing here generates text. Every possible output is in the JSON file.
"""
import json
from pathlib import Path

RECS = json.loads((Path(__file__).parent / "recommendations.json").read_text(encoding="utf-8"))

HIGH = 0.80   # show as a confident finding
MID = 0.55    # show as "possible"; below this we say nothing
MAX_SUGGESTIONS = 4


def suggest(probs, severity):
    """probs: {label: probability}. Returns a list of suggestion dicts (never empty)."""
    found = []
    for lab, p in probs.items():
        if p >= MID and lab in RECS:
            found.append((lab, p))

    # negatives first (what didn't work), then highest confidence
    found.sort(key=lambda x: (not x[0].endswith(":neg"), -x[1]))

    out = []
    for lab, p in found[:MAX_SUGGESTIONS]:
        is_neg = lab.endswith(":neg")
        out.append({
            "label": lab,
            "kind": "issue" if is_neg else ("idea" if lab == "wish_or_suggestion"
                                            else "strength" if lab.endswith(":pos")
                                            else "follow_up"),
            "confidence": round(p, 2),
            "possible": p < HIGH,                       # UI: "possible issue / संभावित"
            "urgent": is_neg and severity == "high",    # UI: highlight / "ज़रूरी"
            "hi": RECS[lab]["hi"],
            "en": RECS[lab]["en"],
        })

    if not out:
        out.append({"label": "_low_confidence", "kind": "unsure", "confidence": 0.0,
                    "possible": True, "urgent": False,
                    **RECS["_low_confidence"]})
    return out


# ---- fallback when the review carries no analysable meaning -----------------
import re

FALLBACK = {
    "kind": "translation_only",
    "hi": "इस समीक्षा से साफ़ निष्कर्ष नहीं निकला। मेहमान ने जो लिखा, उसका अनुवाद नीचे है। कृपया खुद पढ़ें या मेहमान से पूछें।",
    "en": "No clear conclusion from this review. Below is a translation of what the guest wrote. "
          "Please read it yourself or ask the guest.",
}


def looks_meaningless(text):
    """True for empty/garbage input: almost no letters, or a single word/emoji."""
    letters = re.findall(r"[^\W\d_]", text)
    return len(letters) < 4 or len(text.split()) < 2
