#!/usr/bin/env python3
"""
Exports Hugging Face mmBERT tokenizer (tokenizer.json) to an ultra-compact,
high-performance binary format (tokenizer_bpe.bin) for on-device Android BPE tokenization.
"""

import os
import sys
import json
import struct
import time

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets")
TOKENIZER_JSON = os.path.join(ASSETS_DIR, "tokenizer.json")
OUTPUT_BIN = os.path.join(ASSETS_DIR, "tokenizer_bpe.bin")

def export_tokenizer():
    if not os.path.exists(TOKENIZER_JSON):
        # Fallback to model_output
        fallback = os.path.join(SCRIPT_DIR, "model_output", "tokenizer.json")
        if os.path.exists(fallback):
            source_json = fallback
        else:
            print(f"❌ Error: {TOKENIZER_JSON} not found.")
            sys.exit(1)
    else:
        source_json = TOKENIZER_JSON

    print(f"📥 Reading {source_json}...")
    t0 = time.time()
    with open(source_json, "r", encoding="utf-8") as f:
        tok_json = json.load(f)

    vocab = tok_json["model"]["vocab"]
    merges = tok_json["model"]["merges"]

    clean_merges = []
    for rank, m in enumerate(merges):
        if isinstance(m, list) and len(m) == 2:
            clean_merges.append((m[0], m[1], rank))
        elif isinstance(m, str):
            parts = m.split(" ")
            if len(parts) == 2:
                clean_merges.append((parts[0], parts[1], rank))

    print(f"📊 Vocab items: {len(vocab):,}, Merges: {len(clean_merges):,}")

    os.makedirs(ASSETS_DIR, exist_ok=True)
    with open(OUTPUT_BIN, "wb") as f:
        # Header
        f.write(b"MBPE")
        f.write(struct.pack(">I", 1)) # Version 1

        # Vocab entries
        f.write(struct.pack(">I", len(vocab)))
        for token_str, token_id in vocab.items():
            encoded = token_str.encode("utf-8")
            f.write(struct.pack(">H", len(encoded)))
            f.write(encoded)
            f.write(struct.pack(">I", token_id))

        # Merge ranks
        f.write(struct.pack(">I", len(clean_merges)))
        for p1, p2, rank in clean_merges:
            e1 = p1.encode("utf-8")
            e2 = p2.encode("utf-8")
            f.write(struct.pack(">H", len(e1)))
            f.write(e1)
            f.write(struct.pack(">H", len(e2)))
            f.write(e2)
            f.write(struct.pack(">I", rank))

    elapsed = time.time() - t0
    size_mb = os.path.getsize(OUTPUT_BIN) / (1024 * 1024)
    print(f"✅ Generated binary BPE tokenizer ({size_mb:.2f} MB) in {elapsed:.2f}s: {OUTPUT_BIN}")

if __name__ == "__main__":
    export_tokenizer()
