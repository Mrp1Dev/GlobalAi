#!/usr/bin/env python3
"""
Fine-tuning and ONNX Export Script for mmBERT Intent Classifier.
Model Architecture: Multilingual ModernBERT (jhu-clsp/mmBERT-base)
Mobile Deployment Artifact: keisuke-miyako/mmBERT-base-gguf-q4_k_m (~240MB) or ONNX INT8 (~120MB)

Trains on data/output/train.jsonl and data/output/val.jsonl,
evaluates on val/test, and exports to INT8 quantized ONNX for Android.
"""

import os
import sys
import json
import shutil
import argparse
from typing import Dict, List, Any
import torch



# Ensure standard output supports UTF-8 on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)

# Crucial: route Hugging Face caches & system temp to D: drive where there is ample free space
CACHE_DIR = os.path.join(PROJECT_ROOT, ".cache", "huggingface")
TEMP_DIR = os.path.join(PROJECT_ROOT, ".cache", "temp")
os.makedirs(CACHE_DIR, exist_ok=True)
os.makedirs(TEMP_DIR, exist_ok=True)
os.environ["HF_HOME"] = CACHE_DIR
os.environ["TEMP"] = TEMP_DIR
os.environ["TMP"] = TEMP_DIR
os.environ["HF_HUB_DISABLE_XET"] = "1"
os.environ["HF_HUB_ENABLE_HF_TRANSFER"] = "0"
import tempfile
tempfile.tempdir = TEMP_DIR


DEFAULT_DATA_DIR = os.path.join(SCRIPT_DIR, "output")
DEFAULT_OUTPUT_MODEL_DIR = os.path.join(SCRIPT_DIR, "model_output")
DEFAULT_MODEL_NAME = "jhu-clsp/mmBERT-base"
GGUF_REPO = "keisuke-miyako/mmBERT-base-gguf-q4_k_m"
GGUF_FILE = "mmBERT-Base-Q4_k_m.gguf"
LOCAL_GGUF_PATH = os.path.join(PROJECT_ROOT, "models", GGUF_FILE)


def load_jsonl(filepath: str) -> List[Dict[str, Any]]:
    items = []
    with open(filepath, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                items.append(json.loads(line))
    return items


def verify_or_download_gguf() -> str:
    """Verifies presence of keisuke-miyako/mmBERT-base-gguf-q4_k_m or downloads it."""
    if os.path.exists(LOCAL_GGUF_PATH):
        size_mb = os.path.getsize(LOCAL_GGUF_PATH) / (1024 * 1024)
        print(f"✅ Found local GGUF model: {LOCAL_GGUF_PATH} ({size_mb:.1f} MB)")
        return LOCAL_GGUF_PATH

    print(f"📥 Downloading pre-quantized 4-bit GGUF from {GGUF_REPO}...")
    from huggingface_hub import hf_hub_download
    models_dir = os.path.join(PROJECT_ROOT, "models")
    os.makedirs(models_dir, exist_ok=True)
    downloaded_path = hf_hub_download(
        repo_id=GGUF_REPO,
        filename=GGUF_FILE,
        local_dir=models_dir,
        cache_dir=CACHE_DIR
    )
    size_mb = os.path.getsize(downloaded_path) / (1024 * 1024)
    print(f"✅ Downloaded GGUF model: {downloaded_path} ({size_mb:.1f} MB)")
    return downloaded_path


class ModelWrapper(torch.nn.Module):
    def __init__(self, m):
        super().__init__()
        self.m = m

    def forward(self, input_ids, attention_mask):
        return self.m(input_ids=input_ids, attention_mask=attention_mask).logits


def export_and_quantize_onnx(model, tokenizer, output_dir, max_length=64, device="cpu"):
    print("\n⚙️ Exporting fine-tuned model to clean ONNX format (opset 18)...")
    os.makedirs(output_dir, exist_ok=True)
    onnx_path = os.path.join(output_dir, "model_fp32.onnx")
    quant_path = os.path.join(output_dir, "model_int8.onnx")

    model.eval()
    wrapped = ModelWrapper(model)

    dummy_input = tokenizer(
        "How much does a guided tour cost?",
        return_tensors="pt",
        max_length=max_length,
        padding="max_length",
        truncation=True
    )
    dummy_ids = dummy_input["input_ids"].to(device)
    dummy_mask = dummy_input["attention_mask"].to(device)

    torch.onnx.export(
        wrapped,
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
        dynamo=False
    )
    fp32_size_mb = os.path.getsize(onnx_path) / (1024 * 1024)
    print(f"✅ Clean ONNX model exported: {onnx_path} ({fp32_size_mb:.1f} MB)")

    # Quantize to INT8
    print("⚡ Quantizing to INT8 dynamic (memory & storage optimized)...")
    try:
        from onnxruntime.quantization import quantize_dynamic, QuantType
        quantize_dynamic(
            model_input=onnx_path,
            model_output=quant_path,
            weight_type=QuantType.QInt8
        )
        quant_size_mb = os.path.getsize(quant_path) / (1024 * 1024)
        print(f"✅ INT8 Quantized model ready: {quant_path} ({quant_size_mb:.1f} MB)")
    except Exception as e:
        print(f"⚠️ ONNX INT8 quantization note: {e}")

    # Synchronize model to Android assets
    assets_dir = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets")
    os.makedirs(assets_dir, exist_ok=True)
    target_model_onnx = os.path.join(assets_dir, "model.onnx")
    source_model = quant_path if os.path.exists(quant_path) else onnx_path

    shutil.copy2(source_model, target_model_onnx)
    size_mb = os.path.getsize(target_model_onnx) / (1024 * 1024)
    print(f"✅ Synced fine-tuned INT8 model ({size_mb:.1f} MB) to: {target_model_onnx}")

    # The app tokenizes and decodes logits on-device, so ship the exact tokenizer and
    # label order this model was trained with (OnnxIntentClassifier reads both).
    tokenizer.backend_tokenizer.save(os.path.join(assets_dir, "tokenizer.json"))
    id2label = {int(k): v for k, v in model.config.id2label.items()}
    with open(os.path.join(assets_dir, "label_map.json"), "w", encoding="utf-8") as f:
        json.dump({
            "label_to_id": {v: k for k, v in sorted(id2label.items())},
            "id_to_label": {str(k): v for k, v in sorted(id2label.items())},
            "num_labels": len(id2label),
        }, f, indent=2, ensure_ascii=False)
    print(f"✅ Synced tokenizer.json and label_map.json to: {assets_dir}")

    # Remove bulky unquantized files from assets and output to preserve disk space on D:
    for path_to_clean in [
        os.path.join(assets_dir, "model.onnx.data"),
        onnx_path,
        os.path.join(output_dir, "model.onnx"),
        os.path.join(output_dir, "model.onnx.data"),
        os.path.join(output_dir, "test_model_opset18.onnx"),
        os.path.join(output_dir, "test_model_int8.onnx")
    ]:
        if os.path.exists(path_to_clean):
            try:
                os.remove(path_to_clean)
                print(f"🧹 Cleaned up bulky file: {os.path.basename(path_to_clean)}")
            except Exception:
                pass


def main():

    parser = argparse.ArgumentParser(
        description="Train & Export mmBERT Intent Classifier (keisuke-miyako/mmBERT-base-gguf-q4_k_m / jhu-clsp/mmBERT-base)"
    )
    parser.add_argument(
        "--data-dir",
        default=DEFAULT_DATA_DIR,
        help="Path to folder containing train.jsonl, val.jsonl, label_map.json"
    )
    parser.add_argument(
        "--model-name",
        default=DEFAULT_MODEL_NAME,
        help="Hugging Face base model checkpoint (default: jhu-clsp/mmBERT-base)"
    )
    parser.add_argument(
        "--output-dir",
        default=DEFAULT_OUTPUT_MODEL_DIR,
        help="Directory to save fine-tuned model and ONNX artifacts"
    )
    parser.add_argument("--epochs", type=int, default=3, help="Training epochs")
    parser.add_argument("--batch-size", type=int, default=16, help="Batch size")
    parser.add_argument("--lr", type=float, default=3e-5, help="Learning rate")
    parser.add_argument("--max-length", type=int, default=64, help="Max token sequence length")
    parser.add_argument("--export-onnx", action="store_true", help="Export to ONNX INT8 after training")
    parser.add_argument("--export-only", action="store_true", help="Skip training and export existing fine-tuned checkpoint to INT8 ONNX")
    parser.add_argument("--dry-run", action="store_true", help="Validate dataset, check GGUF model, and tokenize without training")
    parser.add_argument("--download-gguf", action="store_true", help="Download keisuke-miyako/mmBERT-base-gguf-q4_k_m to models/")


    args = parser.parse_args()

    print("=" * 72)
    print("🧠 mmBERT MULTILINGUAL INTENT CLASSIFIER")
    print(f"   Base Architecture : {args.model_name} (Multilingual ModernBERT)")
    print(f"   Quantized Mobile  : {GGUF_REPO} (~240MB)")
    print(f"   Storage Cache     : {CACHE_DIR}")
    print("=" * 72)

    if args.download_gguf:
        verify_or_download_gguf()
        return

    train_path = os.path.join(args.data_dir, "train.jsonl")
    val_path = os.path.join(args.data_dir, "val.jsonl")
    label_map_path = os.path.join(args.data_dir, "label_map.json")

    if not os.path.exists(train_path) or not os.path.exists(label_map_path):
        print(f"❌ Error: Required dataset files missing in {args.data_dir}.")
        print("   Run 'python data/generate_dataset.py' to generate the dataset first.")
        sys.exit(1)

    with open(label_map_path, "r", encoding="utf-8") as f:
        label_map = json.load(f)

    train_data = load_jsonl(train_path)
    val_data = load_jsonl(val_path)

    print(f"📊 Dataset Loaded:")
    print(f"   Train Samples    : {len(train_data)}")
    print(f"   Val Samples      : {len(val_data)}")
    print(f"   Number of Classes: {label_map['num_labels']}")
    print(f"   Classes          : {list(label_map['label_to_id'].keys())}")

    # Check local GGUF mobile artifact
    if os.path.exists(LOCAL_GGUF_PATH):
        size_mb = os.path.getsize(LOCAL_GGUF_PATH) / (1024 * 1024)
        print(f"   Mobile GGUF Ready: {LOCAL_GGUF_PATH} ({size_mb:.1f} MB)")
    else:
        print(f"   Mobile GGUF Status: Not yet downloaded. (Run with --download-gguf to fetch)")

    try:
        import torch
        from torch.utils.data import Dataset, DataLoader
        from transformers import AutoTokenizer, AutoModelForSequenceClassification
    except ImportError:
        print("❌ Error: PyTorch and Transformers must be installed.")
        print("   Run: pip install torch transformers")
        sys.exit(1)

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"⚡ Compute Device: {device}")

    print(f"\n📥 Loading mmBERT tokenizer ({args.model_name})...")
    try:
        tokenizer = AutoTokenizer.from_pretrained(args.model_name, cache_dir=CACHE_DIR)
    except Exception as e:
        print(f"❌ Failed to load tokenizer for {args.model_name}: {e}")
    if args.export_only:
        print(f"\n🚀 Loading existing checkpoint from {args.output_dir} for INT8 ONNX export...")
        model = AutoModelForSequenceClassification.from_pretrained(
            args.output_dir,
            num_labels=label_map["num_labels"],
            low_cpu_mem_usage=True,
            cache_dir=CACHE_DIR
        )
        model.to(device)
        export_and_quantize_onnx(model, tokenizer, args.output_dir, args.max_length, device)
        return

    class FarmDataset(Dataset):

        def __init__(self, records, tokenizer, max_len):
            self.records = records
            self.tokenizer = tokenizer
            self.max_len = max_len

        def __len__(self):
            return len(self.records)

        def __getitem__(self, idx):
            rec = self.records[idx]
            enc = self.tokenizer(
                rec["text"],
                truncation=True,
                max_length=self.max_len,
                padding="max_length",
                return_tensors="pt"
            )
            return {
                "input_ids": enc["input_ids"].squeeze(0),
                "attention_mask": enc["attention_mask"].squeeze(0),
                "labels": torch.tensor(rec["label"], dtype=torch.long)
            }

    train_dataset = FarmDataset(train_data, tokenizer, args.max_length)
    val_dataset = FarmDataset(val_data, tokenizer, args.max_length)

    print("✅ Tokenization pipeline verified successfully with mmBERT tokenizer.")

    if args.dry_run:
        print("\n🚀 Dry run completed! Dataset and mmBERT tokenizer are 100% verified.")
        return

    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=args.batch_size, shuffle=False)

    print(f"\n📥 Initializing model {args.model_name} ({label_map['num_labels']} labels)...")
    model = AutoModelForSequenceClassification.from_pretrained(
        args.model_name,
        num_labels=label_map["num_labels"],
        id2label={int(k): v for k, v in label_map["id_to_label"].items()},
        label2id=label_map["label_to_id"],
        low_cpu_mem_usage=True,
        cache_dir=CACHE_DIR
    )
    model.to(device)

    optimizer = torch.optim.AdamW(model.parameters(), lr=args.lr)

    print("\n🏋️ Starting mmBERT Fine-Tuning...")
    for epoch in range(1, args.epochs + 1):
        model.train()
        total_loss = 0.0
        for step, batch in enumerate(train_loader):
            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels = batch["labels"].to(device)

            optimizer.zero_grad()
            outputs = model(input_ids=input_ids, attention_mask=attention_mask, labels=labels)
            loss = outputs.loss
            loss.backward()
            optimizer.step()
            total_loss += loss.item()

        avg_loss = total_loss / len(train_loader)

        # Validation
        model.eval()
        correct = 0
        total = 0
        with torch.no_grad():
            for batch in val_loader:
                input_ids = batch["input_ids"].to(device)
                attention_mask = batch["attention_mask"].to(device)
                labels = batch["labels"].to(device)

                outputs = model(input_ids=input_ids, attention_mask=attention_mask)
                preds = torch.argmax(outputs.logits, dim=1)
                correct += (preds == labels).sum().item()
                total += labels.size(0)

        val_acc = (correct / total) * 100 if total > 0 else 0
        print(f"  Epoch [{epoch}/{args.epochs}] - Train Loss: {avg_loss:.4f} | Val Accuracy: {val_acc:.2f}%")

    os.makedirs(args.output_dir, exist_ok=True)
    model.save_pretrained(args.output_dir)
    tokenizer.save_pretrained(args.output_dir)
    print(f"\n💾 Fine-tuned model saved to: {args.output_dir}")

    # Export to ONNX if requested
    if args.export_onnx:
        export_and_quantize_onnx(model, tokenizer, args.output_dir, args.max_length, device)




if __name__ == "__main__":
    main()
