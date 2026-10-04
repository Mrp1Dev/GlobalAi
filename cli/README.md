# Farm Tourism Assistant - Pipeline CLI Testing Tool

This CLI tool enables rapid development and end-to-end verification of the **Edge-Native Farm Tourism Assistant** conversational pipeline without requiring Android Studio APK builds or device emulators.

---

## What It Tests: Tier 3 Fallback (Worst-Case Scenario)

When the multilingual intent classifier has low confidence or an unstructured conversational inquiry is received:
1. **Tourist Turn**:
   - Tourist sends query in their foreign language (e.g. Spanish, French, German, Italian).
   - Auto-detects input language.
   - Translates into host farmer Noor's native language (Hindi `hi` by default).
2. **Farmer Turn (Noor)**:
   - Displays query in Hindi for Noor to read.
   - Prompts Noor for her reply (presets or free-form text in Hindi/English).
   - Translates Noor's response back to tourist's detected language.
3. **Execution Metrics**:
   - Zero-server cost ($0.00).
   - Turn latencies in milliseconds.
   - Structured JSON history logging.

---

## How to Run

### Automated Multi-Language Verification Suite
Runs test queries across Spanish, French, German, and Italian with round-trip translations:
```bash
python test_pipeline.py --test
# or
python cli/pipeline_cli.py --test
```

### Interactive Simulation
Interactive turn-by-turn conversation:
```bash
python test_pipeline.py --interactive
```

### Options:
- `--farmer-name`: Change farmer's name (default: `Noor`).
- `--farmer-lang`: Change farmer's native tongue code (default: `hi` for Hindi).
- Commands inside interactive mode:
  - `demo`: Injects sample Spanish tour query.
  - `history`: Dumps full conversation session in JSON format.
  - `clear`: Resets conversation state.
  - `exit`: Quits session.
