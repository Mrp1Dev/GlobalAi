# Feedback module - handoff

## Run
    python -m venv .venv ; .venv\Scripts\activate        (Windows)
    pip install -r requirements.txt
    uvicorn app:app --reload --port 8000                 (first run downloads a ~470 MB model)
    UI work without the model:  set FEEDBACK_MOCK=1 first (PowerShell: $env:FEEDBACK_MOCK="1")

## API
POST http://127.0.0.1:8000/analyze-feedback    body: {"text": "..."}
GET  http://127.0.0.1:8000/health              -> {"ok":true,"mode":"mock|zero-shot|trained","translator":bool}
CORS is open, so a browser frontend on another port can call it.

## Response
{
  "original": str,
  "translation_hi": str|null,        // Hindi translation of the review (null if translator not plugged in)
  "detected": [{"label","confidence"}],
  "severity": "low|medium|high",
  "suggestions": [{"label","kind","confidence","possible","urgent","hi","en"}],
  "needs_human": bool,
  "fallback": null | {"kind":"translation_only","hi","en","translation_hi"},
  "model_mode": str,
  "disclaimer_hi": str
}
kind = issue | strength | idea | follow_up.   possible=true -> show as "possible" (संभावित).   urgent=true -> highlight (ज़रूरी).
If needs_human is true: suggestions is [] and fallback is set -> show fallback text + translation, no advice.
If the response has an "error" key (empty input) show a gentle message.

## Rules for the UI
Nothing is acted on automatically. Show Accept / Ignore per suggestion. Show disclaimer_hi.
