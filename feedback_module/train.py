"""Train the heads.   python train.py data/train.csv

CSV columns: text,language,labels,severity,source
  labels   = semicolon-separated, e.g. "experience_activity:pos;timing_waiting:neg"
  severity = low|medium|high
Saves models/heads.joblib. Prints validation precision/recall per label.
"""
import sys
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sentence_transformers import SentenceTransformer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import precision_recall_fscore_support
from sklearn.model_selection import train_test_split

from classifier import MODEL_NAME, HEADS_PATH
from labels import LABELS

path = sys.argv[1] if len(sys.argv) > 1 else "data/train.csv"
df = pd.read_csv(path).dropna(subset=["text"])
df["labels"] = df["labels"].fillna("")

model = SentenceTransformer(MODEL_NAME)
X = model.encode(["query: " + t for t in df["text"]], normalize_embeddings=True, show_progress_bar=True)

Y = np.array([[int(l in str(s).split(";")) for l in LABELS] for s in df["labels"]])
sev = df["severity"].fillna("low").values

idx = np.arange(len(df))
tr, va = train_test_split(idx, test_size=0.2, random_state=42)

heads = {"labels": {}, "severity": None}
print(f"\n{'label':32s} {'n_pos':>5s} {'prec':>6s} {'rec':>6s}")
for j, lab in enumerate(LABELS):
    if Y[tr, j].sum() < 3 or (1 - Y[tr, j]).sum() < 3:
        print(f"{lab:32s} {int(Y[:, j].sum()):5d}   skipped (need >=3 positive and negative examples)")
        heads["labels"][lab] = None
        continue
    clf = LogisticRegression(max_iter=2000, class_weight="balanced").fit(X[tr], Y[tr, j])
    heads["labels"][lab] = clf
    pred = (clf.predict_proba(X[va])[:, 1] >= 0.55).astype(int)
    p, r, _, _ = precision_recall_fscore_support(Y[va, j], pred, average="binary", zero_division=0)
    print(f"{lab:32s} {int(Y[:, j].sum()):5d} {p:6.2f} {r:6.2f}")

sev_clf = LogisticRegression(max_iter=2000, class_weight="balanced").fit(X[tr], sev[tr])
print("\nseverity accuracy (val):", round(float((sev_clf.predict(X[va]) == sev[va]).mean()), 3))
heads["severity"] = sev_clf

Path(HEADS_PATH).parent.mkdir(exist_ok=True)
joblib.dump(heads, HEADS_PATH)
print("saved", HEADS_PATH)
