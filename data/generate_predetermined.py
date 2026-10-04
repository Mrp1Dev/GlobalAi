#!/usr/bin/env python3
"""
Predetermined Replies & Requests Generator for Edge-Native Farm Assistant.
Delegates directly to the unified, high-quality Featherless AI pipeline generator.
Focused purely on Hindi as Noor's language.
"""

import os
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)

from generate_featherless_pipeline import main

if __name__ == "__main__":
    main()
