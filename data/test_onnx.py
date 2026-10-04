#!/usr/bin/env python3
"""
Test Inference with Exported ONNX mmBERT Intent Classifier.
"""

import os
import sys
import json
import numpy as np

# Ensure stdout handles UTF-8 on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(SCRIPT_DIR, "model_output")
LABEL_MAP_FILE = os.path.join(SCRIPT_DIR, "output", "label_map.json")
ONNX_MODEL_FILE = os.path.join(MODEL_DIR, "model.onnx")


def test():
    import onnxruntime as ort
    from transformers import AutoTokenizer

    print("=" * 72)
    print("🧪 ONNX MODEL INFERENCE VERIFICATION")
    print(f"   Model: {ONNX_MODEL_FILE}")
    print("=" * 72)

    with open(LABEL_MAP_FILE, "r", encoding="utf-8") as f:
        lmap = json.load(f)["id_to_label"]

    tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
    session = ort.InferenceSession(ONNX_MODEL_FILE)

    test_queries = [
        ("es", "Hola, ¿cuánto cuesta el tour guiado por la granja?", "price_tour"),
        ("fr", "Quels sont vos horaires d'ouverture demain?", "visitation_hours"),
        ("de", "Darf ich meinen Hund an der Leine mitbringen?", "pet_policy"),
        ("hi", "क्या खेत पर देशी खाना और दोपहर का भोजन मिलता है?", "amenities_food"),
        ("pt", "Vocês vendem mel orgânico e leite fresco de vaca?", "price_produce"),
        ("en", "Do we need to book our tickets in advance or can we walk in?", "booking_reservation"),
        ("it", "Dove si trova esattamente l'agriturismo e come arriviamo?", "farm_location_directions"),
        ("de", "Wie wird das Wetter morgen in der Umgebung sein?", "out_of_scope")
    ]

    for lang, query, expected in test_queries:
        enc = tokenizer(
            query,
            return_tensors="np",
            max_length=64,
            padding="max_length",
            truncation=True
        )
        inputs = {
            "input_ids": enc["input_ids"].astype(np.int64),
            "attention_mask": enc["attention_mask"].astype(np.int64)
        }
        outputs = session.run(None, inputs)
        logits = outputs[0][0]
        # Softmax
        exp_logits = np.exp(logits - np.max(logits))
        probs = exp_logits / np.sum(exp_logits)
        pred_id = int(np.argmax(probs))
        confidence = float(probs[pred_id])
        pred_intent = lmap.get(str(pred_id), str(pred_id))

        status = "✅ MATCH" if pred_intent == expected else f"⚠️ EXPECTED {expected}"
        print(f"[{lang.upper()}] \"{query}\"")
        print(f"     ↳ Predicted Intent : {pred_intent:<25} (Confidence: {confidence * 100:.1f}%) | {status}")
        print()

    print("=" * 72)
    print("🎉 ALL INFERENCE CHECKS COMPLETED ON ONNX RUNTIME!")
    print("=" * 72)


if __name__ == "__main__":
    test()
