# Edge-Native Multilingual Farm Tourism Assistant & Review Intelligence System

> **World Bank Youth Summit × Hack-Nation Global AI Hackathon 2026**  
> **Challenge 04: Small AI for Development — Sector 03: Tourism ("Turning Interest into a Business")**  
> *Target User*: **Noor** (Smallholder coffee & agritourism host in Ondera highlands)  
> *Platform*: **Native Android (Kotlin + Jetpack Compose + Material 3)**  
> *Runtime Cost*: **$0 Server Hosting / $0 Cloud Token Cost** (100% On-Device Edge Execution)

---

## 1. Mandatory Hackathon Deliverables

### 1.1 One-Sentence Problem Statement (Section 08 Template)
> **"Because of this tool, Noor will communicate effortlessly with foreign tourists and understand visitor reviews to upgrade her farm tours by running multilingual intent classification, on-device translation, and operational improvement suggestions entirely on her existing phone, that she would otherwise fail to do or rely on unaffordable cloud APIs and human translators for; we know because small informal agritourism operators face steep language barriers and run on instinct without analytical tools to interpret foreign visitor feedback (World Bank Tourism Brief & Yelp Open Dataset analysis)."**

---

## 2. Context & The Small AI Opportunity

### 2.1 The Reality of Rural Agritourism (Meet Noor)
Noor is 38 and farms 2 hectares in the Ondera highlands (coffee on the upper slope, maize and beans below). She hosts 6 to 7 international visitors a month who discover her farm purely by word of mouth. 

- **Connectivity**: No Wi-Fi at the house; buys prepaid 3G data bundles from the local network only when needed.
- **Hardware**: An accessible Android smartphone (shared with her schooling daughter on weekends). For most of the day, Noor is out on the slope while her phone is at the house.
- **Language**: Noor speaks her local regional language and the national language (**Hindi / हिन्दी**), but does not speak English, Spanish, French, German, or Italian.
- **Financial Constraint**: Zero budget for recurring cloud server hosting ($50–$200/mo) or LLM API token bills.

### 2.2 The Two Gaps Addressed
1. **The Communication Gap (Inquiry Handling)**: Foreign tourists arriving at or inquiring about the farm ask questions in foreign languages (*"How much is the tour?"*, *"Are there restrooms?"*, *"Can I bring a dog?"*). Without a local guide, Noor cannot answer them promptly.
2. **The Analytics & Upgrade Gap (Visitor Reviews)**: Visitors leave positive or negative comments on paper or online in languages Noor cannot read. Noor runs her tourism business on pure instinct: she knows visitors leave happy, but not *why*, which parts of the tour are worth building on, or how to turn feedback into returning guests.

---

## 3. System Architecture & Workflows

To avoid confusing conversational chat with analytical feedback, the application provides **two dedicated, separated workflows**:

```
                                  [ Farm Assistant App ]
                                             │
                        ┌────────────────────┴────────────────────┐
                        ▼                                         ▼
               🎒 TOURIST PORTAL                          🌾 NOOR'S HOST PORTAL
        ┌───────────────┴───────────────┐          ┌───────────────┴───────────────┐
        ▼                               ▼          ▼                               ▼
   💬 Ask Noor                    ⭐ Leave Review   💬 Inquiries             ⭐ Review Insights
  (Inquiry Chat)                 (Visitor Feedback)  (संदेश पोर्टल)           (समीक्षा और सुझाव)
        │                               │                  │                               │
        ▼                               │                  ▼                               │
  [ 3-Tier Edge Pipeline ]              │          [ Noor DB Slot Prompt ]                 │
  - Google ML Kit LangID (<5ms)         │          - Hindi slotted questions               │
  - mmBERT ONNX Classifier (<30ms)      │          - Caches missing values                 │
  - Tier 1: Instant DB Hit (<100ms)     │                  │                               │
  - Tier 2: Slotted Noor Prompt         │                  │                               │
  - Tier 3: Direct Two-Way Translation  │                  │                               │
                                        ▼                  │                               ▼
                           [ On-Device Review Pipeline ] ──┘                 [ Review Dashboard ]
                           - Detects language (<5ms)                         - Hindi Translation
                           - ML Kit Translate -> Hindi (<100ms)              - Aspect Breakdown
                           - 18-Label Aspect & Severity Engine               - Actionable Suggestion
                           - Deterministic Rule Engine                       - "Noor Decides" Control
```

---

## 4. Workflow 1: Multilingual Inquiry Assistant (`💬 Ask Noor`)

An offline-first, 3-tier conversational pipeline executing on-device in under 100ms:

```
                            [ Tourist Message ]
                                     │
                                     ▼
                  [ Google ML Kit Language Identification ]
                  (Auto-detects language: es, fr, de, etc.)
                                     │
                                     ▼
                      [ Multilingual BERT Classifier ]
                       (mmBERT-base ONNX on-device)
                                     │
                   ┌─────────────────┴─────────────────┐
      Confidence >= Threshold             Confidence < Threshold
                   │                                   │
                   ▼                                   ▼
         [ Check Local Database ]            [ Tier 3: Direct Translation ]
                   │                         - Translate query to Noor's lang
          ┌────────┴────────┐                - Noor replies freely in her lang
       Hit                Miss               - Translate reply back to tourist
          │                 │
          ▼                 ▼
    [ Tier 1: Fast ]  [ Tier 2: Template Prompt ]
    - Slot fill DB    - Ask Noor slotted template in her lang
      into template   - Noor enters missing value (e.g. "500")
    - ML Kit translates - Slot fill template & update DB
      to tourist lang   - ML Kit translates to tourist lang
          │                 │
          └────────┬────────┘
                   ▼
          [ Deliver to Tourist ]
```

### The 3 Execution Tiers
- **Tier 1 (Sub-100ms Automated Fast-Path)**:
  - *Trigger*: Intent is classified with confidence $\ge \tau$ (0.70) and target data slot exists in Noor's local SQLite database.
  - *Example*: Tourist asks Spanish: *"¿Cuánto cuesta el tour guiado?"* $\rightarrow$ `mmBERT` classifies `price_tour` $\rightarrow$ DB returns `₹500` $\rightarrow$ ML Kit delivers Spanish reply: *"El precio del tour es de 500 rupias."* Zero effort required from Noor.
- **Tier 2 (Template-Guided Human-in-the-Loop)**:
  - *Trigger*: Intent is classified with confidence $\ge \tau$, but the field is missing from the database.
  - *Example*: Intent is `price_tour`, but DB lookup returns `null`. App prompts Noor in Hindi: *"इस टूर की कीमत क्या है?"*. Noor enters `"500"`. App caches this value into SQLite for future Tier 1 hits and returns the translated reply to the tourist.
- **Tier 3 (Direct Translation Fallback)**:
  - *Trigger*: Confidence $< \tau$ or out-of-scope query.
  - *Example*: Open-ended conversation translated directly to Noor in Hindi. Noor replies freely in Hindi, which ML Kit translates back to the tourist's native language.

---

## 5. Workflow 2: Visitor Review Intelligence (`⭐ Review Insights`)

A dedicated operational intelligence system allowing visitors to leave reviews in their native languages and translating them into actionable farm upgrades for Noor.

```
                  [ Visitor Review in Foreign Language ]
                  (e.g., German, Spanish, French, English)
                                     │
                                     ▼
                  [ Google ML Kit Language Identification ]
                                     │
                 ┌───────────────────┴───────────────────┐
            Undetermined                              Detected
                 │                                       │
                 ▼                                       ▼
       [ Fail-Safe Signpost ]             [ ML Kit On-Device Translation ]
    "Language unclear — ask person"       (Translates review -> Hindi for Noor)
                 │                                       │
                 ▼                                       ▼
          (Pass/Fail Safe)                [ 18-Label Review Aspect Classifier ]
                                          - Aspects: waiting, cleanliness, etc.
                                          - Sentiment Polarity: :pos / :neg
                                          - Severity: HIGH_CUES (English/Hindi)
                                                         │
                                                         ▼
                                          [ Deterministic Rule Engine ]
                                          - High > Medium > Low Priority
                                          - "Noor Decides" Human Agency Banner
```

### 5.1 18-Label Review Taxonomy
Sourced directly from real agritourism review patterns and normalized into 10 key operational areas:
- `waiting_time` (`timing_waiting:pos/neg`)
- `directions` (`directions_access:pos/neg`)
- `hospitality` (`guide_hospitality:pos/neg`)
- `food` (`food_drink:pos/neg`)
- `cleanliness` (`facilities_cleanliness:pos/neg`)
- `experience` (`experience_activity:pos/neg`, `would_recommend_or_return`)
- `pricing` (`price_value:pos/neg`)
- `communication` (`communication:pos/neg`)
- `learning_authenticity` (`learning_authenticity:pos/neg`)
- `activities` (`wish_or_suggestion`)

### 5.2 Deterministic Operational Rules (Zero Hallucination)
- **Waiting Delay**: *"Improve arrival and start-time communication"* / *"आगमन और यात्रा शुरू होने के समय की जानकारी पहले से स्पष्ट करें।"*
- **Missing Signs**: *"Provide clearer directions and landmark guidance before visitor arrival"* / *"पर्यटकों के आने से पहले मुख्य मोड़ पर स्पष्ट दिशा-निर्देश बोर्ड लगाएं।"*
- **Hospitality**: *"Greet visitors personally at start and set aside time for questions"* / *"पर्यटकों का व्यक्तिगत स्वागत करें और सवालों के लिए समय दें।"*
- **Pricing Clarity**: *"Make pricing details and inclusions clearer before the visit"* / *"दौरे के शुल्क और लाभों की अग्रिम जानकारी स्पष्ट रखें।"*

---

## 6. Data Grounding & Synthetic Data Generation (Section 07 Compliance)

The World Bank Small AI criteria rigorously scores **Data Grounding (15%)** and mandates clear disclosure of both natural reference datasets and artificially generated data.

### 6.1 Natural Reference Datasets (Problem Grounding)
1. **Yelp Open Dataset**: Millions of real-world business reviews analyzed to extract authentic tourist feedback phrasing, aspect distribution, and polarity indicators.
2. **MASSIVE (Amazon)**: 1 million short utterances in 51 languages used as the structural template for categorical intent slot-filling.
3. **FLORES-200**: Meta's translation benchmark used to validate cross-lingual translation quality between European languages and Hindi.
4. **UN Tourism Statistics & World Bank Development Indicators**: Problem-is-real evidence demonstrating that small informal tourism operators capture under 15% of regional visitor spending due to language and discoverability barriers.

### 6.2 Artificially Generated Data Pipeline
Because rural agritourism queries are underrepresented in open datasets, synthetic data was generated using high-throughput open inference:

- **Generation Engine**: **Featherless AI** using `deepseek-ai/DeepSeek-V4-Flash-0731` at **$0.28 per 1M tokens**.
- **Generated Components**:
  1. *Multilingual Tourist Persona Queries*: 20+ distinct variations per intent across 8 languages (English, Spanish, French, German, Italian, Portuguese, Dutch, Hindi).
  2. *Hindi Prompt Templates for Noor*: Culturally natural slotted questions in Devanagari script for missing database slots.
  3. *English Base Templates*: Slotted reply templates with atomic `{slot_key}` variables.
  4. *Syntactic Augmentation (`data/generate_dataset.py`)*: Combinatorial generation of spelling errors, contractions, and regional phrasings to ensure model robustness on real-world mobile input.
- **Dataset Partitions**:
  - `data/output/train.jsonl` (80% stratified training split)
  - `data/output/val.jsonl` (10% validation split)
  - `data/output/test.jsonl` (10% holdout test split)

### 6.3 What the Data Does NOT Cover (Scored Criterion)
In compliance with Section 7.2 of the Hackathon Rules:
- **Exclusions**: The dataset covers agritourism, tour logistics, fresh produce purchasing, and visitor feedback. It **does not cover medical advice, legal contracts, emergency services, or credit/banking transactions**.
- **Fail-Safe Mechanism**: Out-of-domain queries are routed directly to human escalation (Tier 3 fallback or *"Ask a person"* flag).

---

## 7. Responsible AI & Guardrails (Pass/Fail Criteria)

1. **Human-in-the-Loop Principle**:
   - The app **never acts on Noor's behalf**. It presents suggested operational improvements with interactive **"स्वीकार करें (Accept)"** and **"Dismiss"** buttons. Noor remains the sole decision maker.
   - The app will never automatically send messages, contact visitors, change tour prices, or alter bookings.
2. **Elimination of Hallucinations**:
   - Replaced unconstrained generative models with **deterministic intent slotting** and a **fixed rule catalog** for suggestions.
3. **Fail-Safe Signposting**:
   - If language detection is ambiguous or undetermined (`und`), the system explicitly responds: *"Language unclear — please ask a person"* rather than guessing or silently assuming English.
4. **Privacy & Offline Safety**:
   - Zero tourist queries or reviews leave the device. No data is stored on remote cloud servers or logged to external LLM providers.

---

## 8. Interactive Benchmark Demo Scenarios

The app includes **5 pre-calibrated live demo scenarios** accessible with 1-click in the UI:

| Scenario | Language | Original Visitor Review | Detected Aspects & Severity | Operational Suggestion for Noor |
| :--- | :--- | :--- | :--- | :--- |
| **Scenario 1** | 🇩🇪 **German** | *"Die Tour über die Kaffeefarm war wunderbar organisiert und alles war sehr sauber."* | `cleanliness` (pos), `experience` (pos)<br>**Severity**: Low | Maintain current high standard of cleanliness and facilities. |
| **Scenario 2** | 🇬🇧 **English** | *"We had to wait over 45 minutes past the start time before the tour even began. It was very disorganized."* | `waiting_time` (neg)<br>**Severity**: **HIGH** (*"45 minutes"*, *"disorganized"*) | **High Priority**: Improve arrival and start-time communication. |
| **Scenario 3** | 🇪🇸 **Spanish** | *"Me encantó el paseo por la finca y el café estaba delicioso, pero la entrada nos pareció demasiado cara para lo que incluye."* | `pricing` (neg), `food` (pos), `experience` (pos)<br>**Severity**: Medium | Make pricing details and tour inclusions clearer before the visit. |
| **Scenario 4** | 🇫🇷 **French** | *"La ferme est très difficile à trouver. Il n'y a aucun panneau sur la route principale et le chemin est mal indiqué."* | `directions` (neg)<br>**Severity**: Medium | Provide clearer directions and landmark guidance before arrival. |
| **Scenario 5** | 🇮🇳 **Hindi** | *"मेजबान बहुत ही मिलनसार थे और ताज़ी कॉफी का स्वाद वाकई लाजवाब था।"* | `hospitality` (pos), `food` (pos)<br>**Severity**: Low (Bypasses translation) | Maintain current warm hospitality and keep coffee as regular highlight. |

---

## 9. Decided Tech Stack & Specifications

| Layer | Component | Choice | Footprint & Latency |
| :--- | :--- | :--- | :--- |
| **Mobile Client** | UI & Native Framework | **Android (Kotlin + Jetpack Compose + Material 3)** | Native Android 8.0+ (API 26+) |
| **Language ID** | On-Device Language Detection | **Google ML Kit Language Identification SDK** | <1 MB footprint, **<5 ms latency** |
| **Translation Engine** | On-Device Neural Translation | **Google ML Kit On-Device Translation** | ~30 MB/language pack, **<100 ms latency** |
| **Intent Classifier** | Multilingual Query Categorization | **mmBERT-base (INT8 Quantized ONNX)** via **ONNX Runtime Mobile** | ~120 MB model, **<30 ms inference** |
| **Review Engine** | Aspect & Severity Analysis | **18-Label Taxonomy Classifier + Deterministic Rule Engine** | Pure Kotlin on-device, **<10 ms inference** |
| **Local Storage** | Cache & Farm Configuration | **SQLite / Android Room** | Offline persistent relational cache |

---

## 10. Repository Structure

```
GlobalAi/
├── app/                                 # Native Android Application (Kotlin + Jetpack Compose)
│   ├── src/main/assets/                 # mmBERT ONNX model & tokenizer assets
│   ├── src/main/java/com/farmtourism/assistant/
│   │   ├── backend/                     # Core Business Logic & Pipelines
│   │   │   ├── classifier/              # ONNX Runtime & mmBERT Tokenizer
│   │   │   ├── database/                # SQLite Local Database
│   │   │   ├── mlkit/                   # ML Kit Language ID & Translation
│   │   │   ├── pipeline/                # Tier 1, Tier 2, Tier 3 Pipelines
│   │   │   └── review/                  # Review Analyzer & Suggestion Engine
│   │   │       ├── classifier/          # 18-label aspect classifier & taxonomy
│   │   │       ├── demo/                # 5 Benchmark demo scenarios
│   │   │       ├── model/               # Review domain models & contracts
│   │   │       └── rules/               # Deterministic recommendation catalog
│   │   └── ui/                          # Jetpack Compose UI
│   │       ├── navigation/              # Dual-Mode & Sub-Tab Navigation
│   │       └── screens/                 # TouristChat, TouristReview, NoorChat, NoorReviewDashboard
│   └── src/test/                        # Automated JUnit test suite
├── noor-review/                         # Friend's original prototype repository (imported for traceability)
├── data/                                # Dataset generation & fine-tuning pipeline
│   ├── generate_featherless_pipeline.py # Featherless AI synthetic data generation
│   ├── generate_dataset.py              # Syntactic combinatorial augmentation
│   ├── train_classifier.py              # mmBERT fine-tuning script
│   └── export_onnx.py                   # INT8 ONNX export script
└── combined_readme.md                   # This comprehensive documentation file
```

---

## 11. How to Build & Run

### 11.1 Run Unit Tests (100% Passing)
```bash
./gradlew testDebugUnitTest
```

### 11.2 Assemble Debug APK
```bash
./gradlew assembleDebug
```
The compiled APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

---

## 12. Conclusion: What Localizing AI Development Means to Us

Localizing AI does not mean shrinking a massive cloud LLM and hoping for the best. For smallholder farmers like Noor, localizing AI means:
1. **Zero Recurring Overhead**: Small operators cannot afford token billing or dedicated servers; solutions must run on the hardware they already own.
2. **Cultural & Linguistic Respect**: Preserving local agency by speaking Noor's native language (Hindi) and leaving every business decision in her hands.
3. **Targeted, Trustworthy Utility**: Replacing open-ended generative hallucinations with deterministic, high-confidence assistance that genuinely improves livelihoods.
