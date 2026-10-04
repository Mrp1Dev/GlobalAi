"""Type reviews and see what the classifier does.   python try.py
Blank line to quit. Shows the top raw scores so you can see WHY it was unsure."""
import sys
sys.stdout.reconfigure(encoding="utf-8")   # so Hindi prints on Windows

from classifier import FeedbackClassifier
from rules import suggest, looks_meaningless

clf = FeedbackClassifier()
print("mode:", clf.mode, "| type a review, blank line to quit")
while True:
    t = input("\nReview> ").strip()
    if not t:
        break
    if looks_meaningless(t):
        print("-> meaningless/too short: would show plain translation only")
        continue
    probs, sev = clf.predict(t)
    top = sorted(probs.items(), key=lambda x: -x[1])[:5]
    print("top scores:", [(l, round(p, 2)) for l, p in top])
    print("severity:", sev)
    s = suggest(probs, sev)
    if s[0]["label"] == "_low_confidence":
        print("-> not confident: would show plain translation only")
        continue
    for x in s:
        tags = ("urgent " if x["urgent"] else "") + ("possible" if x["possible"] else "")
        print(f" - [{x['kind']} {tags}] {x['en']}\n   {x['hi']}")
