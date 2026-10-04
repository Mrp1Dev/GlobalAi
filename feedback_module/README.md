# Feedback module (Noor's Guest Desk)

## Run now (no model download, for UI work)
    pip install fastapi uvicorn pydantic
    FEEDBACK_MOCK=1 uvicorn app:app --reload
    POST http://localhost:8000/analyze-feedback  {"text": "..."}

## Real classifier
    pip install -r requirements.txt
    uvicorn app:app --reload          # zero-shot baseline until heads exist
    python train.py data/train.csv    # writes models/heads.joblib, then restart
    python evaluate.py data/test.csv  # numbers for the writeup (keep test.csv OUT of training)

## Files
labels.py (taxonomy) | classifier.py (mock / zero-shot / trained) | rules.py (fixed suggestions)
recommendations.json (Hindi + English text) | app.py (API) | train.py | evaluate.py

data/train.csv and data/test.csv currently hold 3 example rows only. Replace them.
Translation: drop a translator.py exposing translate(text, src, tgt) next to app.py.
