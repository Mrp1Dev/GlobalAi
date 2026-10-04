# Multilingual Farm Tourism Dataset & mmBERT Training Pipeline

This directory contains the dataset generation and augmentation pipeline for training the on-device **Multilingual BERT (mmBERT)** intent classifier for the Farm Tourism Assistant.

---

## 1. Intent Taxonomy & Slot Architecture

The classifier categorizes tourist queries across **9 distinct intents**, enabling the 3-Tier Edge Architecture:
- **Tier 1 (Fast-Path)**: Intent classified with confidence $\ge \tau$, database hit $\rightarrow$ immediate template slot fill.
- **Tier 2 (Human-in-the-Loop)**: Intent classified with confidence $\ge \tau$, database miss $\rightarrow$ prompts Noor in Hindi to enter value $\rightarrow$ saves to DB.
- **Tier 3 (Direct Translation)**: Confidence $< \tau$ or `out_of_scope` $\rightarrow$ two-way direct translation.

| Intent Code | Target Slot Key | Description | Noor Prompt Template (Hindi `hi`) |
| :--- | :--- | :--- | :--- |
| `price_tour` | `tour_price_inr` | Tour pricing, ticket cost, group rates, per person rates | *"इस कॉफ़ी फार्म टूर की प्रति व्यक्ति टिकट दर क्या है?"* |
| `price_produce` | `produce_pricing_info` | Cost of fresh roasted coffee beans, honey, farm crops | *"खेत की ताजी भुनी कॉफ़ी, जैविक शहद और फसलों की क्या दरें हैं?"* |
| `visitation_hours` | `opening_hours` | Opening/closing times, days open, seasons | *"खेत पर्यटकों के लिए कब खुलता और बंद होता है?"* |
| `tour_duration_difficulty` | `tour_duration_difficulty_info` | Tour duration, trail terrain, slope difficulty | *"खेत भ्रमण (टूर) में कितना समय लगता है और चढ़ाई का रास्ता कैसा है?"* |
| `farm_location_directions` | `location_directions` | Highland address, mountain road, parking | *"खेत का पता और यहाँ पहुँचने का रास्ता क्या है?"* |
| `activities_available` | `farm_activities` | Coffee picking, wet mill pulping, cupping tasting | *"पर्यटक खेत पर क्या-क्या गतिविधियां और अनुभव ले सकते हैं?"* |
| `amenities_food` | `amenities_food_info` | Farm lunch, coffee/tea, drinking water, restrooms | *"खेत पर भोजन, ताजी कॉफ़ी, पीने के पानी और वॉशरूम की क्या व्यवस्था है?"* |
| `pet_policy` | `pet_policy_rules` | Rules regarding dogs and domestic pets on trails | *"खेत में पालतू जानवरों (जैसे कुत्तों) को लाने के क्या नियम हैं?"* |
| `booking_reservation` | `booking_requirements` | Advance booking vs walk-in entry | *"क्या खेत आने के लिए पहले से बुकिंग जरूरी है या सीधे आ सकते हैं?"* |
| `out_of_scope` | `none` | Weather, small talk, unrelated queries (Triggers Tier 3) | *(Direct fallback to Noor via Tier 3)* |

---

## 2. Supported Languages

Aligned with Google ML Kit Translation and mmBERT tokenization:
- **English (`en`)**
- **Spanish (`es`)**
- **French (`fr`)**
- **German (`de`)**
- **Italian (`it`)**
- **Hindi (`hi`)** *(Farmer Host Language)*
- **Portuguese (`pt`)**
- **Dutch (`nl`)**

---

## 3. Dataset Generation Modes

The generator (`generate_dataset.py`) supports three complementary generation strategies:

### Mode 1: Curated Seed Dataset (Instant, Offline)
Loads verified, native-style phrased tourist inquiries across all 9 intents and 8 languages.
```bash
python data/generate_dataset.py --mode seed
```

### Mode 2: Free Translation & Syntactic Augmentation (Zero Cost, No API Key)
Generates combinatorial syntactic variations and expands them across languages using Google's public translation engine.
```bash
python data/generate_dataset.py --mode augment
```

### Mode 3: Unified Featherless AI Generation (High Quality & Multi-Persona)
Connects to Featherless AI (`deepseek-ai/DeepSeek-V4-Flash-0731` at $0.28 / 1M tokens) to generate:
1. Slotted Hindi questions for Noor.
2. Natural English reply templates with `{slot_key}` placeholders.
3. Multi-persona tourist inquiries across 10 intents and 8 languages.
```bash
python data/generate_featherless_pipeline.py --api-key YOUR_FEATHERLESS_API_KEY
```

---

## 4. Generated Artifacts & Structure

Running the generator produces:
- `data/output/train.jsonl` (80% stratified training split)
- `data/output/val.jsonl` (10% stratified validation split)
- `data/output/test.jsonl` (10% stratified test split)
- `data/output/dataset.csv` (Full dataset in CSV format)
- `data/output/label_map.json` (Bi-directional mapping between intent string and integer ID)
- `data/output/dataset_stats.json` (Class and language distribution metrics)

### Sample JSONL Record:
```json
{
  "intent": "price_tour",
  "lang": "es",
  "text": "¿Cuánto cobran por persona para la visita a la finca?",
  "label": 7
}
```

---

## 5. Model Architecture & Fine-Tuning

- **Base Architecture**: `jhu-clsp/mmBERT-base` (Multilingual ModernBERT with 200k vocabulary)
- **Mobile Deployment Artifact**: `keisuke-miyako/mmBERT-base-gguf-q4_k_m` (~240MB, 4-bit quantized GGUF in `models/mmBERT-Base-Q4_k_m.gguf`) or ONNX INT8 (`model_int8.onnx`, ~120MB)

### Verifying mmBERT Tokenizer & Dataset Compatibility:
```bash
python data/train_classifier.py --dry-run
```

### Running Fine-Tuning & ONNX INT8 Export:
```bash
python data/train_classifier.py --epochs 3 --export-onnx
```

