#!/usr/bin/env python3
"""
Inspects generated dataset splits, label distributions, and displays sample rows per intent.
"""

import os
import sys
import json
from collections import Counter

# Ensure stdout handles UTF-8 for Devanagari and Latin accents on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output")


def inspect():
    stats_file = os.path.join(OUTPUT_DIR, "dataset_stats.json")
    label_map_file = os.path.join(OUTPUT_DIR, "label_map.json")
    train_file = os.path.join(OUTPUT_DIR, "train.jsonl")

    if not os.path.exists(stats_file) or not os.path.exists(train_file):
        print(f"❌ Output directory not found or empty at: {OUTPUT_DIR}")
        print("   Please run 'python data/generate_dataset.py' first.")
        return

    with open(stats_file, "r", encoding="utf-8") as f:
        stats = json.load(f)

    with open(label_map_file, "r", encoding="utf-8") as f:
        label_map = json.load(f)

    print("=" * 72)
    print("📊 DATASET INSPECTION & SUMMARY")
    print("=" * 72)
    print(f"Total Unique Samples: {stats['total_samples']}")
    print(f"Split Breakdown:")
    for split_name, count in stats['split_counts'].items():
        pct = (count / stats['total_samples']) * 100 if stats['total_samples'] > 0 else 0
        print(f"  - {split_name.upper():<6}: {count:>4} samples ({pct:.1f}%)")

    print("\n🌍 Language Coverage:")
    for lang, count in sorted(stats['language_distribution'].items(), key=lambda x: -x[1]):
        print(f"  - {lang.upper():<4}: {count:>4} queries")

    print("\n🎯 Intent Distribution:")
    for intent, count in sorted(stats['intent_distribution'].items(), key=lambda x: -x[1]):
        label_id = label_map["label_to_id"].get(intent, "?")
        print(f"  - [ID {label_id}] {intent:<25}: {count:>4} samples")

    print("\n🔍 Sample Records from Training Split:")
    samples_per_intent = {}
    with open(train_file, "r", encoding="utf-8") as f:
        for line in f:
            if not line.strip():
                continue
            item = json.loads(line)
            intent = item["intent"]
            if intent not in samples_per_intent:
                samples_per_intent[intent] = []
            if len(samples_per_intent[intent]) < 2:
                samples_per_intent[intent].append(item)

    for intent, items in sorted(samples_per_intent.items()):
        print(f"\n Intent: {intent}")
        for it in items:
            print(f"   [{it['lang'].upper()}] \"{it['text']}\"")

    print("=" * 72)


if __name__ == "__main__":
    inspect()
