"""Feedback classifier.

Three modes, picked automatically:
  1. MOCK     : env FEEDBACK_MOCK=1 -> fixed output, no model needed (for UI work).
  2. TRAINED  : models/heads.joblib exists -> logistic-regression heads on e5 embeddings.
  3. ZERO-SHOT: otherwise -> cosine similarity to LABEL_DESCRIPTIONS (uncalibrated baseline).

predict(text) -> (probs: {label: float}, severity: "low"|"medium"|"high")
"""
import os
import re
import math
from pathlib import Path

import numpy as np

from labels import LABELS, LABEL_DESCRIPTIONS

MODEL_NAME = "intfloat/multilingual-e5-small"
HEADS_PATH = Path(__file__).parent / "models" / "heads.joblib"

# Crude severity cues for the baseline only (English, Hindi, Roman Hindi).
HIGH_CUES = [
    "terrible", "worst", "awful", "horrible", "disgusting", "rude", "never again",
    "waste of", "scam", "unsafe", "filthy", "hours", "refund",
    "बहुत बुरा", "बेकार", "गंदा", "घटिया", "ठगी", "कभी नहीं", "बकवास",
    "bakwas", "bekaar", "ganda", "ghatiya",
]


def _sigmoid(x):
    return 1.0 / (1.0 + math.exp(-x))


def heuristic_severity(text, probs):
    has_neg = any(p >= 0.55 for lab, p in probs.items() if lab.endswith(":neg"))
    if not has_neg:
        return "low"
    t = text.lower()
    return "high" if any(c in t for c in HIGH_CUES) else "medium"


class FeedbackClassifier:
    def __init__(self):
        self.mock = os.getenv("FEEDBACK_MOCK") == "1"
        self.model = None
        self.heads = None
        self.label_emb = None
        if self.mock:
            self.mode = "mock"
            return
        from sentence_transformers import SentenceTransformer  # lazy import
        self.model = SentenceTransformer(MODEL_NAME)
        if HEADS_PATH.exists():
            import joblib
            self.heads = joblib.load(HEADS_PATH)
            self.mode = "trained"
        else:
            self.label_emb = self.embed([LABEL_DESCRIPTIONS[l] for l in LABELS])
            self.mode = "zero-shot"

    def embed(self, texts):
        # e5 models expect a "query: " prefix
        return self.model.encode(["query: " + t for t in texts], normalize_embeddings=True)

    def predict(self, text):
        text = re.sub(r"\s+", " ", text).strip()
        if self.mock:
            probs = {l: 0.0 for l in LABELS}
            probs.update({"experience_activity:pos": 0.92, "timing_waiting:neg": 0.88,
                          "directions_access:neg": 0.62})
            return probs, "medium"

        v = self.embed([text])[0]

        if self.mode == "trained":
            probs = {}
            for lab in LABELS:
                clf = self.heads["labels"].get(lab)
                probs[lab] = float(clf.predict_proba([v])[0][1]) if clf is not None else 0.0
            sev_clf = self.heads.get("severity")
            severity = str(sev_clf.predict([v])[0]) if sev_clf is not None \
                else heuristic_severity(text, probs)
            return probs, severity

        # zero-shot baseline: z-score similarities across labels, squash to 0..1
        sims = self.label_emb @ v
        z = (sims - sims.mean()) / (sims.std() + 1e-9)
        probs = {lab: _sigmoid(2.0 * (float(zi) - 1.5)) for lab, zi in zip(LABELS, z)}
        return probs, heuristic_severity(text, probs)
