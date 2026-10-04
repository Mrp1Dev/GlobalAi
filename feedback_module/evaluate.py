"""Evaluate on a held-out test CSV (never used for training).

    python evaluate.py data/test.csv

Prints per-label precision/recall, severity accuracy, and how often the tool
said "not enough information". Paste these numbers into your data table/video.
"""
import sys

import pandas as pd
from sklearn.metrics import precision_recall_fscore_support

from classifier import FeedbackClassifier
from labels import LABELS
from rules import MID, suggest

path = sys.argv[1] if len(sys.argv) > 1 else "data/test.csv"
df = pd.read_csv(path).dropna(subset=["text"])
df["labels"] = df["labels"].fillna("")

clf = FeedbackClassifier()
print("mode:", clf.mode)

y_true, y_pred, sev_ok, unsure, unsure_but_had_labels = [], [], [], 0, 0
for _, row in df.iterrows():
    probs, sev = clf.predict(row["text"])
    truth = set(str(row["labels"]).split(";")) - {""}
    y_true.append([int(l in truth) for l in LABELS])
    y_pred.append([int(probs[l] >= MID) for l in LABELS])
    sev_ok.append(sev == row.get("severity", sev))
    if suggest(probs, sev)[0]["label"] == "_low_confidence":
        unsure += 1
        unsure_but_had_labels += int(len(truth) > 0)

print(f"\n{'label':32s} {'support':>7s} {'prec':>6s} {'rec':>6s}")
for j, lab in enumerate(LABELS):
    t = [r[j] for r in y_true]
    p = [r[j] for r in y_pred]
    pr, rc, _, _ = precision_recall_fscore_support(t, p, average="binary", zero_division=0)
    print(f"{lab:32s} {sum(t):7d} {pr:6.2f} {rc:6.2f}")

print(f"\nreviews: {len(df)}")
print(f"severity accuracy: {sum(sev_ok) / len(sev_ok):.2f}")
print(f"flagged 'not enough information': {unsure} "
      f"({unsure_but_had_labels} of those actually had a labelled issue)")
print("\nNote: say plainly in your writeup if training data was synthetic.")
