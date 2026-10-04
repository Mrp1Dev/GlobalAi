#!/usr/bin/env python3
"""
Convenience entry point for running the Tier 3 Direct Translation Fallback CLI.
Usage:
  python test_pipeline.py --test            (Runs automated multi-language tests)
  python test_pipeline.py --interactive     (Runs interactive terminal session)
"""

import sys
from cli.pipeline_cli import main

if __name__ == "__main__":
    main()
