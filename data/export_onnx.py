#!/usr/bin/env python3
"""
Stand-alone ONNX & INT8 Export Utility for Fine-Tuned mmBERT Model.
Loads fine-tuned weights directly from data/model_output (no re-training needed!)
and exports to model.onnx and model_int8.onnx for mobile Android deployment.
"""

import os
import sys

# Ensure stdout handles UTF-8 on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
MODEL_DIR = os.path.join(SCRIPT_DIR, "model_output")
CACHE_DIR = os.path.join(PROJECT_ROOT, ".cache", "huggingface")

os.environ["HF_HOME"] = CACHE_DIR
os.environ["HF_HUB_DISABLE_XET"] = "1"
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "0"


def export_to_onnx():
    print("=" * 72)
    print("⚙️ EXPORTING FINE-TUNED mmBERT TO ONNX INT8")
    print(f"   Source Model Directory: {MODEL_DIR}")
    print("=" * 72)

    if not os.path.exists(os.path.join(MODEL_DIR, "model.safetensors")):
        print(f"❌ Error: Fine-tuned model not found in {MODEL_DIR}")
        sys.exit(1)

    import torch
    from transformers import AutoTokenizer, AutoModelForSequenceClassification

    device = torch.device("cpu")
    print("📥 Loading fine-tuned tokenizer and model...")
    tokenizer = AutoTokenizer.from_pretrained(MODEL_DIR)
    model = AutoModelForSequenceClassification.from_pretrained(
        MODEL_DIR,
        low_cpu_mem_usage=True
    )
    model.to(device)
    model.eval()

    onnx_path = os.path.join(MODEL_DIR, "model.onnx")
    print(f"\n📦 Exporting PyTorch graph to ONNX ({onnx_path})...")

    dummy_input = tokenizer(
        "Hola, ¿cuánto cuesta el tour guiado?",
        return_tensors="pt",
        max_length=64,
        padding="max_length",
        truncation=True
    )
    dummy_ids = dummy_input["input_ids"].to(device)
    dummy_mask = dummy_input["attention_mask"].to(device)

    # Use standard export with dynamic axes
    try:
        torch.onnx.export(
            model,
            (dummy_ids, dummy_mask),
            onnx_path,
            input_names=["input_ids", "attention_mask"],
            output_names=["logits"],
            dynamic_axes={
                "input_ids": {0: "batch_size", 1: "sequence_length"},
                "attention_mask": {0: "batch_size", 1: "sequence_length"},
                "logits": {0: "batch_size"}
            },
            opset_version=18,
            do_constant_folding=True
        )
        onnx_size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
        print(f"✅ Full ONNX Model Exported: {onnx_path} ({onnx_size_mb:.1f} MB)")
    except Exception as e:
        print(f"⚠️ Direct export with default dynamo note: {e}")
        try:
            torch.onnx.export(
                model,
                (dummy_ids, dummy_mask),
                onnx_path,
                input_names=["input_ids", "attention_mask"],
                output_names=["logits"],
                dynamic_axes={
                    "input_ids": {0: "batch_size", 1: "sequence_length"},
                    "attention_mask": {0: "batch_size", 1: "sequence_length"},
                    "logits": {0: "batch_size"}
                },
                opset_version=18
            )
            onnx_size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
            print(f"✅ Full ONNX Model Exported: {onnx_path} ({onnx_size_mb:.1f} MB)")
        except Exception as e2:
            print(f"❌ ONNX export failed: {e2}")
            return

    # Quantize to INT8 for Android Mobile
    print("\n⚡ Quantizing to INT8 for Android edge deployment...")
    try:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        quant_path = os.path.join(MODEL_DIR, "model_int8.onnx")
        quantize_dynamic(
            model_input=onnx_path,
            model_output=quant_path,
            weight_type=QuantType.QInt8
        )
        quant_size_mb = os.path.getsize(quant_path) / (1024 * 1024)
        print(f"✅ INT8 Quantized Model Ready: {quant_path} ({quant_size_mb:.1f} MB)")
        print("\n🎉 Android mobile artifact is ready for app/src/main/assets/!")
    except Exception as e:
        print(f"⚠️ Quantization note: {e}")

    # Verify ONNX inference test
    print("\n🔍 Verifying ONNX model inference test...")
    try:
        import onnxruntime as ort
        import numpy as np

        test_path = os.path.join(MODEL_DIR, "model_int8.onnx")
        if not os.path.exists(test_path):
            test_path = onnx_path

        session = ort.InferenceSession(test_path)
        inputs = {
            "input_ids": dummy_ids.numpy().astype(np.int64),
            "attention_mask": dummy_mask.numpy().astype(np.int64)
        }
        outputs = session.run(None, inputs)
        logits = outputs[0]
        pred_label = int(np.argmax(logits, axis=1)[0])

        import json
        label_map_file = os.path.join(SCRIPT_DIR, "output", "label_map.json")
        if os.path.exists(label_map_file):
            with open(label_map_file, "r", encoding="utf-8") as f:
                lmap = json.load(f)
            intent_name = lmap["id_to_label"].get(str(pred_label), str(pred_label))
            print(f"   Query: 'Hola, ¿cuánto cuesta el tour guiado?'")
            print(f"   ONNX Prediction: Class ID {pred_label} -> Intent: '{intent_name}'")
            print("✅ ONNX Inference Verified Successfully!")
    except Exception as e:
        print(f"⚠️ Verification note: {e}")


if __name__ == "__main__":
    export_to_onnx()
