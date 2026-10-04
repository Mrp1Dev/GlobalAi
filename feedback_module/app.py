"""Run:  FEEDBACK_MOCK=1 uvicorn app:app --reload      (no model needed, for UI work)
        uvicorn app:app --reload                       (real classifier)
Docs:   http://localhost:8000/docs

If the review has no analysable meaning (garbage, too short, or the classifier is not
confident about anything), we do NOT guess advice. We return the plain translation of
what the guest wrote, so Noor can read it herself.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel

from classifier import FeedbackClassifier
from rules import suggest, MID, FALLBACK, looks_meaningless

# Translation hook: your teammate exposes translate(text, src, tgt) in translator.py.
try:
    from translator import translate
except ImportError:
    translate = None

app = FastAPI(title="Noor's Guest Desk - feedback module")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])

clf = FeedbackClassifier()


class Review(BaseModel):
    text: str


@app.get("/health")
def health():
    return {"ok": True, "mode": clf.mode, "translator": translate is not None}


@app.post("/analyze-feedback")
def analyze_feedback(r: Review):
    text = r.text.strip()
    if not text:
        return {"error": "empty", "needs_human": True}

    translation_hi = None
    if translate is not None:
        try:
            translation_hi = translate(text, "en", "hi")   # adjust args to your translator
        except Exception:                                   # never crash the demo
            translation_hi = None

    probs, severity = ({}, "low") if looks_meaningless(text) else clf.predict(text)
    suggestions = suggest(probs, severity)
    needs_human = suggestions[0]["label"] == "_low_confidence"

    detected = sorted(
        [{"label": l, "confidence": round(p, 2)} for l, p in probs.items() if p >= MID],
        key=lambda d: -d["confidence"],
    )

    fallback = None
    if needs_human:
        suggestions = []                                   # never invent advice
        fallback = {**FALLBACK, "translation_hi": translation_hi}

    return {
        "original": text,
        "translation_hi": translation_hi,
        "detected": detected,
        "severity": severity,
        "suggestions": suggestions,
        "needs_human": needs_human,
        "fallback": fallback,
        "model_mode": clf.mode,
        "disclaimer_hi": "यह सुझाव मशीन से बने हैं। अंतिम निर्णय आपका है।",
    }
