#!/usr/bin/env python3
"""
Unified Featherless AI Pipeline Generator for Edge Farm Tourism Assistant.
Grounding: World Bank Small AI Hackathon (Challenge 04 - Annex C Tourism)
Persona: Noor, 38, Ondera highlands coffee & agro-tourism farm, 2 hectares,
member of Ondera Coffee Cooperative. Hosts foreign tourists speaking English,
Spanish, French, German, Italian, Portuguese, Dutch.

Generates:
  1. Predetermined Slotted Questions for Noor in Hindi ('hi')
     to fill missing database values on-device (Tier 2).
  2. Predetermined Natural English Replies with {slot_key} placeholders
     for automated on-device slot filling & ML Kit translation (Tier 1 & 2).
  3. High-Quality Multilingual Classification Dataset for mmBERT
     spanning 10 intents across 8 languages with persona-based diversity.
"""

import os
import sys
import re
import json
import time
import random
import shutil
import argparse
from typing import Dict, List, Any, Optional, Set
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed

def clean_and_parse_json(raw: str, expected_type: type = dict) -> Any:
    """
    Cleans markdown wrappers, strips CoT/thinking blocks, handles JSON boundary extraction,
    and provides recovery for truncated output.
    """
    if not raw or not raw.strip():
        raise ValueError("Empty response received from LLM")

    # Strip thinking tags if any leaked into content
    cleaned = re.sub(r'<think>[\s\S]*?</think>', '', raw, flags=re.IGNORECASE).strip()

    # If wrapped in markdown block
    if "```json" in cleaned:
        cleaned = cleaned.split("```json", 1)[1].split("```", 1)[0].strip()
    elif "```" in cleaned:
        cleaned = cleaned.split("```", 1)[1].split("```", 1)[0].strip()

    first_brace = cleaned.find("{")
    first_bracket = cleaned.find("[")

    if first_brace == -1 and first_bracket == -1:
        raise ValueError(f"No JSON structure found: {cleaned[:80]}...")

    if expected_type == dict:
        start_idx = first_brace if first_brace != -1 else 0
        end_idx = cleaned.rfind("}")
        candidate = cleaned[start_idx:end_idx + 1] if end_idx != -1 else cleaned[start_idx:]
    else:
        start_idx = first_bracket if first_bracket != -1 else 0
        end_idx = cleaned.rfind("]")
        candidate = cleaned[start_idx:end_idx + 1] if end_idx != -1 else cleaned[start_idx:]

    try:
        return json.loads(candidate)
    except json.JSONDecodeError:
        last_quote = candidate.rfind('"')
        if last_quote > 0:
            prefix = candidate[:last_quote].rstrip()
            if prefix.endswith(":"):
                cut_idx = candidate.rfind(",", 0, last_quote)
                trimmed = candidate[:cut_idx] if cut_idx != -1 else candidate[:last_quote]
            else:
                trimmed = candidate[:last_quote + 1]
            open_brackets = trimmed.count("[") - trimmed.count("]")
            open_braces = trimmed.count("{") - trimmed.count("}")
            repaired = trimmed + ("]" * max(0, open_brackets)) + ("}" * max(0, open_braces))
            try:
                return json.loads(repaired)
            except Exception:
                pass
        raise

def parse_classification_response(raw: str, lang_codes: List[str]) -> Dict[str, List[str]]:
    """
    Parses { "lang1": ["..."], "lang2": ["..."] } with regex fallback
    to safely recover all complete queries even under partial output.
    """
    cleaned = re.sub(r'<think>[\s\S]*?</think>', '', raw, flags=re.IGNORECASE).strip()
    if "```json" in cleaned:
        cleaned = cleaned.split("```json", 1)[1].split("```", 1)[0].strip()
    elif "```" in cleaned:
        cleaned = cleaned.split("```", 1)[1].split("```", 1)[0].strip()

    first_brace = cleaned.find("{")
    last_brace = cleaned.rfind("}")
    if first_brace != -1 and last_brace != -1:
        candidate = cleaned[first_brace:last_brace + 1]
        try:
            parsed = json.loads(candidate)
            if isinstance(parsed, dict):
                return {k: [str(q).strip() for q in v if str(q).strip()] for k, v in parsed.items()}
        except Exception:
            pass

    # Regex extraction fallback: pull all completed string items for each language code
    extracted: Dict[str, List[str]] = {}
    for code in lang_codes:
        pattern = rf'"{code}"\s*:\s*\[([\s\S]*?)(?:\]|\Z)'
        m = re.search(pattern, cleaned)
        if m:
            items = re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1))
            cleaned_items = [i.strip() for i in items if i.strip()]
            if cleaned_items:
                extracted[code] = cleaned_items

    return extracted


# Ensure UTF-8 console output for Windows Devanagari & Latin accents
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
OUTPUT_DIR = os.path.join(SCRIPT_DIR, "output")
INTENTS_FILE = os.path.join(SCRIPT_DIR, "intents.json")
PREDETERMINED_FILE = os.path.join(SCRIPT_DIR, "predetermined_templates.json")
ASSETS_DIR = os.path.join(PROJECT_ROOT, "app", "src", "main", "assets")

# Automatically load environment variables from .env file (project root or script dir)
try:
    from dotenv import load_dotenv
    load_dotenv(os.path.join(PROJECT_ROOT, ".env"))
    load_dotenv(os.path.join(SCRIPT_DIR, ".env"))
except ImportError:
    for env_path in [os.path.join(PROJECT_ROOT, ".env"), os.path.join(SCRIPT_DIR, ".env")]:
        if os.path.exists(env_path):
            with open(env_path, "r", encoding="utf-8") as f:
                for line in f:
                    line = line.strip()
                    if line and not line.startswith("#") and "=" in line:
                        k, v = line.split("=", 1)
                        os.environ.setdefault(k.strip(), v.strip().strip('"').strip("'"))

try:
    from seed_data import CURATED_SEED_SAMPLES
except ImportError:
    try:
        from data.seed_data import CURATED_SEED_SAMPLES
    except ImportError:
        CURATED_SEED_SAMPLES = []

LANGUAGES = {
    "en": "English",
    "es": "Spanish (Español)",
    "fr": "French (Français)",
    "de": "German (Deutsch)",
    "it": "Italian (Italiano)",
    "hi": "Hindi (हिन्दी)",
    "pt": "Portuguese (Português)",
    "nl": "Dutch (Nederlands)"
}

# The 10 Canonical Intents
INTENT_SPECS = [
    {
        "intent": "price_tour",
        "slot_key": "tour_price_inr",
        "description": "Inquiries regarding tour pricing, ticket costs, student/child discounts, family packages, and per-person rates for the coffee farm walk.",
        "sample_slot_value": "500 rs",
        "default_reply": "The price for a guided coffee farm tour is ₹{tour_price_inr} per person, which includes fresh coffee tasting.",
        "default_hi_prompt": "इस कॉफ़ी फार्म टूर की प्रति व्यक्ति टिकट दर क्या है?",
        "fallback_replies": [
            "A guided tour of our coffee plantation and processing mill is ₹{tour_price_inr} per person.",
            "Our walking farm tour and cupping experience costs ₹{tour_price_inr} per adult guest.",
            "Tickets for the guided coffee walk are ₹{tour_price_inr} each, including an organic coffee tasting session.",
            "The entrance fee and guided plantation tour is ₹{tour_price_inr} per visitor.",
            "We charge ₹{tour_price_inr} per person for the complete highland farm visit."
        ],
        "fallback_hi_prompts": [
            "इस कॉफ़ी फार्म टूर की प्रति व्यक्ति टिकट दर क्या है?",
            "पर्यटकों के लिए फार्म टूर का कितना शुल्क लिया जाता है?",
            "कृपया टूर की प्रति व्यक्ति फीस (रुपये में) बताएं:"
        ]
    },
    {
        "intent": "price_produce",
        "slot_key": "produce_pricing_info",
        "description": "Inquiries regarding purchasing farm-fresh roasted coffee beans, unroasted green parchment, highland forest honey, maize, and organic beans.",
        "sample_slot_value": "Roasted Arabica coffee is ₹450/250g, wild forest honey is ₹350/jar, and organic beans are ₹120/kg",
        "default_reply": "Our farm-fresh produce prices: {produce_pricing_info}.",
        "default_hi_prompt": "खेत की ताजी भुनी कॉफ़ी, जैविक शहद और फसलों की क्या दरें हैं?",
        "fallback_replies": [
            "Our farm-fresh coffee and artisanal produce are available at: {produce_pricing_info}.",
            "You can purchase single-origin coffee and harvest goods directly from our farm: {produce_pricing_info}.",
            "Current rates for our cooperative roasted coffee beans and farm items: {produce_pricing_info}.",
            "We sell fresh harvest goods directly to visitors: {produce_pricing_info}."
        ],
        "fallback_hi_prompts": [
            "खेत की ताजी भुनी कॉफ़ी, जैविक शहद और फसलों की क्या दरें हैं?",
            "यहाँ बिकने वाली कॉफ़ी बीन्स और ताजी उपज की कीमतें बताएं:",
            "पर्यटक उपज की कीमत पूछ रहे हैं, कृपया दर सूची दर्ज करें:"
        ]
    },
    {
        "intent": "visitation_hours",
        "slot_key": "opening_hours",
        "description": "Opening and closing times, operating days of the week, harvest season schedule, and holiday visiting hours.",
        "sample_slot_value": "8:30 AM to 5:30 PM (Tuesday to Sunday, closed Mondays for harvesting)",
        "default_reply": "The farm is open for visitors from {opening_hours}.",
        "default_hi_prompt": "खेत पर्यटकों के लिए खुलने और बंद होने का समय क्या है?",
        "fallback_replies": [
            "We welcome visitors during our regular visiting hours: {opening_hours}.",
            "Our farm gates and walking trails are open to guests {opening_hours}.",
            "You can visit our coffee plantation and facilities {opening_hours}.",
            "Visiting hours on the farm are {opening_hours}."
        ],
        "fallback_hi_prompts": [
            "खेत पर्यटकों के लिए खुलने और बंद होने का समय क्या है?",
            "फार्म विजिट के लिए सप्ताह में कौन से दिन और क्या समय तय है?",
            "कृपया खेत के खुलने और बंद होने के घंटे बताएं:"
        ]
    },
    {
        "intent": "tour_duration_difficulty",
        "slot_key": "tour_duration_difficulty_info",
        "description": "Tour duration in hours, walking trail conditions, hill slope steepness, physical fitness requirements, and accessibility for kids/seniors.",
        "sample_slot_value": "1.5 to 2 hours along gentle highland coffee trails; comfortable walking shoes and light rain jackets are recommended",
        "default_reply": "Our guided farm tour takes about {tour_duration_difficulty_info}.",
        "default_hi_prompt": "खेत भ्रमण (टूर) में कितना समय लगता है और चढ़ाई का रास्ता कैसा है?",
        "fallback_replies": [
            "The guided walking tour typically takes {tour_duration_difficulty_info}.",
            "Visitors should plan for {tour_duration_difficulty_info} along the highland slope paths.",
            "Our coffee plantation walk lasts approximately {tour_duration_difficulty_info}.",
            "The full farm and processing tour requires about {tour_duration_difficulty_info}."
        ],
        "fallback_hi_prompts": [
            "खेत भ्रमण (टूर) में कितना समय लगता है और चढ़ाई का रास्ता कैसा है?",
            "पर्यटक टूर की अवधि और रास्ते की कठिनाई पूछ रहे हैं, कृपया बताएं:",
            "पैदल टूर कितने समय का है और क्या बच्चों/बुजुर्गों के लिए आसान है?"
        ]
    },
    {
        "intent": "farm_location_directions",
        "slot_key": "location_directions",
        "description": "Directions to the highland farm from the district town, mountain road conditions, 4WD recommendations, public bus/taxi access, and on-site parking.",
        "sample_slot_value": "Ondera Highlands, 12 km from district town along Valley Ridge Road; parking is free on site",
        "default_reply": "The farm is located at {location_directions}.",
        "default_hi_prompt": "खेत का पता और जिले के शहर से यहाँ पहुँचने का रास्ता क्या है?",
        "fallback_replies": [
            "You can find our farm at {location_directions}.",
            "Directions to our coffee farm: {location_directions}.",
            "Our location is {location_directions}. Signboards mark the cooperative turnoff.",
            "To reach the farm, please follow {location_directions}."
        ],
        "fallback_hi_prompts": [
            "खेत का पता और जिले के शहर से यहाँ पहुँचने का रास्ता क्या है?",
            "पर्यटक रास्ता पूछ रहे हैं; मुख्य सड़क से खेत पहुँचने का विवरण दर्ज करें:",
            "कृपया खेत का सही पता और पार्किंग की जानकारी बताएं:"
        ]
    },
    {
        "intent": "activities_available",
        "slot_key": "farm_activities",
        "description": "Activities offered: red cherry picking, coffee wet-mill pulping and sun-drying demonstration, sensory cupping session, and highland bird watching.",
        "sample_slot_value": "coffee harvesting walks, pulping mill demonstrations, artisanal cupping sessions, and guided highland nature trails",
        "default_reply": "Activities available during your visit include: {farm_activities}.",
        "default_hi_prompt": "पर्यटक खेत पर क्या-क्या गतिविधियां और अनुभव ले सकते हैं?",
        "fallback_replies": [
            "During your visit, you can take part in: {farm_activities}.",
            "Our farm experiences feature: {farm_activities}.",
            "Visitors are invited to enjoy: {farm_activities}.",
            "Key activities you can experience on the farm: {farm_activities}."
        ],
        "fallback_hi_prompts": [
            "पर्यटक खेत पर क्या-क्या गतिविधियां और अनुभव ले सकते हैं?",
            "फार्म पर उपलब्ध प्रमुख अनुभवों (कॉफ़ी बीन तोड़ना, टेस्टिंग) की सूची दर्ज करें:",
            "कृपया बताएं पर्यटक खेत में क्या-क्या कर सकते हैं:"
        ]
    },
    {
        "intent": "amenities_food",
        "slot_key": "amenities_food_info",
        "description": "On-farm guest amenities: local traditional highland lunch, freshly brewed coffee and tea, clean washrooms, rest shelters, and potable spring water.",
        "sample_slot_value": "freshly brewed single-origin coffee, traditional highland bean lunch, clean western-style restrooms, and spring drinking water",
        "default_reply": "Visitor amenities at the farm include: {amenities_food_info}.",
        "default_hi_prompt": "खेत पर भोजन, ताजी कॉफ़ी, पीने के पानी और वॉशरूम की क्या व्यवस्था है?",
        "fallback_replies": [
            "For guest comfort, our farm provides: {amenities_food_info}.",
            "Our on-site amenities and refreshment offerings include: {amenities_food_info}.",
            "We provide clean facilities for all guests: {amenities_food_info}.",
            "Food, drink, and rest facilities at the farm: {amenities_food_info}."
        ],
        "fallback_hi_prompts": [
            "खेत पर भोजन, ताजी कॉफ़ी, पीने के पानी और वॉशरूम की क्या व्यवस्था है?",
            "पर्यटकों के लिए दोपहर के भोजन, चाय और टॉयलेट की क्या सुविधा है?",
            "कृपया खेत पर उपलब्ध आवश्यक सुविधाओं की जानकारी दें:"
        ]
    },
    {
        "intent": "pet_policy",
        "slot_key": "pet_policy_rules",
        "description": "Policy regarding bringing dogs and companion animals to the farm, leash rules on working agricultural trails, and domestic pet safety.",
        "sample_slot_value": "well-behaved dogs on leashes are welcome on the outdoor trails; pets are restricted from the coffee drying tables",
        "default_reply": "Our pet policy for visitors: {pet_policy_rules}.",
        "default_hi_prompt": "खेत में पालतू जानवरों (जैसे कुत्तों) को लाने के क्या नियम हैं?",
        "fallback_replies": [
            "Regarding domestic pets on our farm: {pet_policy_rules}.",
            "Our pet guidelines for visitors: {pet_policy_rules}.",
            "We ask guests with pets to observe our farm policy: {pet_policy_rules}.",
            "Information for visitors traveling with pets: {pet_policy_rules}."
        ],
        "fallback_hi_prompts": [
            "खेत में पालतू जानवरों (जैसे कुत्तों) को लाने के क्या नियम हैं?",
            "क्या पर्यटक अपने कुत्ते ला सकते हैं? कृपया नियम दर्ज करें:",
            "फार्म की पालतू जानवर नीति क्या है?"
        ]
    },
    {
        "intent": "booking_reservation",
        "slot_key": "booking_requirements",
        "description": "Advance reservation policy vs spontaneous walk-ins, maximum group size (e.g. 8 people), and notice required for tasting and lunch.",
        "sample_slot_value": "advance notice of at least 24 hours is required for guided tours and farm lunch; walk-ins may self-walk outer trails",
        "default_reply": "Our booking policy: {booking_requirements}.",
        "default_hi_prompt": "क्या खेत आने के लिए पहले से बुकिंग जरूरी है या सीधे आ सकते हैं?",
        "fallback_replies": [
            "Regarding tour reservations: {booking_requirements}.",
            "To help us prepare your visit, please note our reservation terms: {booking_requirements}.",
            "Our booking guidelines for guided farm visits: {booking_requirements}.",
            "Information on tour bookings and advance notice: {booking_requirements}."
        ],
        "fallback_hi_prompts": [
            "क्या खेत आने के लिए पहले से बुकिंग जरूरी है या सीधे आ सकते हैं?",
            "फार्म टूर और भोजन के लिए कितने समय पहले बुकिंग करानी होगी?",
            "कृपया बुकिंग के नियम और पूर्व-सूचना की शर्तें बताएं:"
        ]
    },
    {
        "intent": "out_of_scope",
        "slot_key": "none",
        "description": "Queries outside farm scope (weather forecasts, town hotels, banking/ATMs, local politics, flights, personal chitchat) which route directly to Noor via Tier 3 translation.",
        "sample_slot_value": "",
        "default_reply": "I am forwarding your message directly to Noor so she can respond to you personally.",
        "default_hi_prompt": "यह प्रश्न सीधा अनुवाद के जरिए नूर तक पहुँचाया जाएगा।",
        "fallback_replies": [
            "Let me connect you directly with Noor for this specific question.",
            "I will forward your inquiry directly to our farm host, Noor.",
            "Noor will review your question and reply directly in a moment.",
            "Please allow a moment while I share your message with Noor."
        ],
        "fallback_hi_prompts": [
            "यह प्रश्न सीधा अनुवाद के जरिए नूर तक पहुँचाया जाएगा।",
            "इस खुले प्रश्न का अपनी भाषा में उत्तर दें:"
        ]
    }
]


# =============================================================================
# 1. High-Quality Templates & Noor Prompts Generation (Featherless AI)
# =============================================================================

def generate_featherless_templates(
    client: Any,
    model: str,
    dry_run: bool = False
) -> List[Dict[str, Any]]:
    """
    Generates natural English reply templates with {slot_key} and slotted Hindi
    prompts for Noor for each intent, grounded in the Ondera highland coffee farm setting.
    """
    print("\n" + "=" * 76)
    print("📝 GENERATING PREDETERMINED TEMPLATES & NOOR PROMPTS VIA FEATHERLESS AI")
    print("=" * 76)

    updated_intents = []

    for item in INTENT_SPECS:
        intent = item["intent"]
        slot_key = item["slot_key"]
        desc = item["description"]
        print(f"\n🌾 Intent: '{intent}' (Slot: '{slot_key}')")

        if dry_run or client is None:
            print("   ↳ Using curated baseline templates (Dry-Run / Offline Mode)")
            updated_intents.append({
                "intent": intent,
                "slot_key": slot_key,
                "description": desc,
                "noor_prompt_template": {
                    "hi": item["default_hi_prompt"]
                },
                "default_reply_template": item["default_reply"],
                "sample_slot_value": item["sample_slot_value"],
                "reply_template_variations": item["fallback_replies"],
                "noor_prompt_variations": {
                    "hi": item["fallback_hi_prompts"]
                }
            })
            continue

        slot_instruction = (
            f"CRITICAL: Every sentence MUST contain the placeholder '{{{slot_key}}}'."
            if slot_key != "none"
            else "CRITICAL: Since slot_key is 'none', do NOT include any slot placeholder."
        )

        prompt = f"""You are an expert NLP dialogue engineer for an offline farm tourism mobile assistant.
Host Persona: Noor, 38, runs a highland coffee agro-tourism farm in the Ondera highlands (Arabica coffee on upper slopes, beans/maize below, member of Ondera Coffee Cooperative). Foreign tourists visit her farm and ask questions.

Domain Intent: "{intent}"
Slot Key: "{slot_key}"
Intent Scope: {desc}
Sample Slot Value: "{item['sample_slot_value']}"

Instructions:
1. "english_replies": Generate 5 to 6 distinct, natural, hospitable English response sentences.
   - {slot_instruction}
   - Vary the phrasing naturally (warm welcome tone, concise informational tone, practical guidance).
   - Avoid cheesy, identical greetings. Make them sound like an authentic highland eco-farm host.
2. "hindi_prompts": Generate 3 to 4 clear, polite, natural questions in Hindi ('hi') to ask Noor when this slot is missing from her phone's database.
   - The question must prompt Noor to enter this specific missing value in a short, atomic answer.
3. "sample_slot_value": A realistic, specific sample value for this slot (e.g. price, hours, duration, directions) for a highland coffee farm.

Return STRICTLY a JSON object with this exact schema:
{{
  "english_replies": ["...", "..."],
  "hindi_prompts": ["...", "..."],
  "sample_slot_value": "..."
}}
Output ONLY valid JSON. No markdown backticks or commentary.
"""

        try:
            response = client.chat.completions.create(
                model=model,
                temperature=0.7,
                max_tokens=2000,
                messages=[
                    {"role": "system", "content": "You are a specialized multilingual conversational dataset architect. Output strict JSON only."},
                    {"role": "user", "content": prompt}
                ],
                extra_body={"chat_template_kwargs": {"thinking": False}}
            )
            raw = response.choices[0].message.content or ""
            parsed = clean_and_parse_json(raw, expected_type=dict)
            replies = parsed.get("english_replies", [])
            hi_prompts = parsed.get("hindi_prompts", [])
            sample_val = parsed.get("sample_slot_value", item["sample_slot_value"])


            # Validate {slot_key} presence
            if slot_key != "none":
                valid_replies = [r for r in replies if f"{{{slot_key}}}" in r]
                if not valid_replies:
                    valid_replies = item["fallback_replies"]
            else:
                valid_replies = replies if replies else item["fallback_replies"]

            valid_hi = hi_prompts if hi_prompts else item["fallback_hi_prompts"]

            print(f"   ✅ Generated {len(valid_replies)} English templates & {len(valid_hi)} Hindi prompts.")

            updated_intents.append({
                "intent": intent,
                "slot_key": slot_key,
                "description": desc,
                "noor_prompt_template": {
                    "hi": valid_hi[0]
                },
                "default_reply_template": valid_replies[0],
                "sample_slot_value": sample_val,
                "reply_template_variations": valid_replies,
                "noor_prompt_variations": {
                    "hi": valid_hi
                }
            })

        except Exception as e:
            print(f"   ⚠️ API call failed for '{intent}': {e}. Using curated fallback.")
            updated_intents.append({
                "intent": intent,
                "slot_key": slot_key,
                "description": desc,
                "noor_prompt_template": {
                    "hi": item["default_hi_prompt"]
                },
                "default_reply_template": item["default_reply"],
                "sample_slot_value": item["sample_slot_value"],
                "reply_template_variations": item["fallback_replies"],
                "noor_prompt_variations": {
                    "hi": item["fallback_hi_prompts"]
                }
            })

    return updated_intents


# =============================================================================
# 2. High-Quality Multilingual Classification Dataset Generation
# =============================================================================

# Group languages into balanced pairs to optimize prompt token capacity & quality
LANGUAGE_PAIRS = [
    [("en", "English"), ("es", "Spanish")],
    [("fr", "French"), ("de", "German")],
    [("it", "Italian"), ("pt", "Portuguese")],
    [("nl", "Dutch"), ("hi", "Hindi")]
]

PERSONA_GUIDELINES = """
Generate inquiries across these 4 distinct tourist personas:
1. Casual Backpacker / Independent Traveler: Short, direct, informal, asking practical questions ("is the hike steep", "¿cómo llego a la finca?").
2. Family / Group Organizer: Polite, detailed, asking about kids, elderly walking distance, safety, lunch, facilities ("We have two children, can they walk the coffee trail?").
3. Specialty Coffee Connoisseur / Eco-Tourist: Enthusiastic, asking about single-origin Arabica, harvest months, cupping, washing mill, buying beans ("Do you offer cupping sessions of different roasts?").
4. Spoken Voice Transcripts: Unpunctuated, conversational speech fragments ("hi can we visit this afternoon for coffee tasting").
"""

def generate_featherless_classifications(
    client: Any,
    model: str,
    samples_per_lang: int = 15,
    dry_run: bool = False,
    include_seeds: bool = False
) -> List[Dict[str, str]]:
    """
    Generates realistic, persona-driven tourist inquiries across all 10 intents
    and 8 languages using Featherless AI.
    Featherless generation is the pure GROUND TRUTH for training data.
    """
    print("\n" + "=" * 76)
    print(f"🧠 GENERATING CLASSIFICATION DATASET VIA FEATHERLESS AI ({samples_per_lang} samples/lang/intent)")
    print("=" * 76)

    all_samples: List[Dict[str, str]] = []

    # In dry-run mode without API key, use seed samples as offline baseline preview
    if dry_run or client is None:
        print("⚡ Dry-Run / Offline mode: returning seed samples only as development preview.")
        if CURATED_SEED_SAMPLES:
            all_samples.extend(CURATED_SEED_SAMPLES)
        return all_samples

    # Live generation: Featherless is the pure ground truth
    if include_seeds and CURATED_SEED_SAMPLES:
        print(f"📦 Optional: Merging {len(CURATED_SEED_SAMPLES)} seed samples as requested by --include-seeds.")
        all_samples.extend(CURATED_SEED_SAMPLES)
    else:
        print("✨ Pure Synthetic Mode: 100% of training data will be generated by Featherless AI.")

    # Prepare batch tasks: 10 intents * 4 language pairs = 40 tasks
    tasks = []
    for item in INTENT_SPECS:
        intent = item["intent"]
        desc = item["description"]
        for lang_group in LANGUAGE_PAIRS:
            tasks.append((intent, desc, lang_group))

    total_tasks = len(tasks)
    print(f"🚀 Queued {total_tasks} generation tasks across 10 intents and 8 languages (workers=3)...")

    def run_batch(task_tuple):
        intent, desc, lang_group = task_tuple
        group_desc = ", ".join([f"{code} ({name})" for code, name in lang_group])
        lang_codes = [code for code, _ in lang_group]

        prompt = f"""You are generating training queries for a multilingual on-device intent classifier for a rural coffee farm tourism assistant in the Ondera highlands.
Host Persona: Noor, 38, runs a highland coffee agro-tourism farm.
Domain Intent: "{intent}"
Intent Scope: {desc}
Target Languages:
{chr(10).join([f"- {code} ({name})" for code, name in lang_group])}
Queries required per language: {samples_per_lang}

{PERSONA_GUIDELINES}

Rules:
- Generate genuine, natural phrases as actually spoken/typed by travelers in these native languages (NOT literal translations).
- Mix sentence lengths: some short 3-word inquiries, some full multi-clause queries.
- If intent is "out_of_scope", generate realistic questions tourists might send to a farm number that are NOT about the farm (e.g. weather forecast, town hotels, ATM locations, football scores, doctor/hospital).
- Return STRICTLY a JSON object mapping language code to an array of query strings:
{{
  "{lang_codes[0]}": ["query 1", "query 2"],
  "{lang_codes[1]}": ["query 1", "query 2"]
}}
Output ONLY valid JSON.
"""
        for attempt in range(1, 4):
            try:
                response = client.chat.completions.create(
                    model=model,
                    temperature=0.75,
                    max_tokens=2500,
                    messages=[
                        {"role": "system", "content": "You are a native multilingual dataset creator. Output strict JSON object mapping language codes to lists of query strings."},
                        {"role": "user", "content": prompt}
                    ],
                    extra_body={"chat_template_kwargs": {"thinking": False}}
                )
                raw = response.choices[0].message.content or ""
                parsed = parse_classification_response(raw, lang_codes)

                batch_samples = []
                for code in lang_codes:
                    queries = parsed.get(code, [])
                    for q in queries:
                        if len(q.strip()) >= 3:
                            batch_samples.append({
                                "intent": intent,
                                "lang": code,
                                "text": q.strip()
                            })
                if batch_samples:
                    return intent, group_desc, batch_samples, None
                else:
                    raise ValueError(f"No valid queries parsed from keys {lang_codes}")
            except Exception as e:
                if attempt == 3:
                    return intent, group_desc, [], str(e)
                time.sleep(1.5 * attempt)

    completed_count = 0
    with ThreadPoolExecutor(max_workers=3) as executor:
        future_to_task = {executor.submit(run_batch, t): t for t in tasks}
        for future in as_completed(future_to_task):
            completed_count += 1
            intent, group_desc, batch_samples, err = future.result()
            if err:
                print(f"   ❌ Batch failed ({completed_count}/{total_tasks}) for {intent} [{group_desc}]: {err}")
            else:
                all_samples.extend(batch_samples)
                print(f"   ↳ Added {len(batch_samples)} queries for '{intent}' [{group_desc}] ({completed_count}/{total_tasks})")

    return all_samples



# =============================================================================
# 3. Post-Processing: Deduplication, Stratification & Asset Synchronization
# =============================================================================

def process_and_save_dataset(samples: List[Dict[str, str]]) -> Dict[str, Any]:
    """
    Deduplicates, validates schema, builds label map, stratifies 80/10/10,
    and synchronizes artifacts to Android assets.
    """
    print("\n" + "=" * 76)
    print("📊 DEDUPLICATING, STRATIFYING & SYNCHRONIZING ARTIFACTS")
    print("=" * 76)

    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(ASSETS_DIR, exist_ok=True)

    # 1. Deduplicate by (intent, lang, normalized text)
    seen = set()
    cleaned_samples = []
    for s in samples:
        text = s.get("text", "").strip()
        intent = s.get("intent", "").strip()
        lang = s.get("lang", "en").strip().lower()

        if len(text) < 3 or not intent:
            continue

        norm = " ".join(text.lower().split())
        key = f"{intent}::{lang}::{norm}"
        if key not in seen:
            seen.add(key)
            cleaned_samples.append({
                "intent": intent,
                "lang": lang,
                "text": text
            })

    print(f"✅ Total unique samples after deduplication: {len(cleaned_samples)}")

    # 2. Build Canonical Label Map (10 intents sorted alphabetically)
    intents_list = sorted(list(set(s["intent"] for s in cleaned_samples)))
    label_to_id = {name: idx for idx, name in enumerate(intents_list)}
    id_to_label = {str(idx): name for idx, name in enumerate(intents_list)}

    label_map_data = {
        "label_to_id": label_to_id,
        "id_to_label": id_to_label,
        "num_labels": len(intents_list)
    }

    label_map_file = os.path.join(OUTPUT_DIR, "label_map.json")
    with open(label_map_file, "w", encoding="utf-8") as f:
        json.dump(label_map_data, f, indent=2, ensure_ascii=False)

    # Copy label_map.json to Android assets
    assets_label_map = os.path.join(ASSETS_DIR, "label_map.json")
    with open(assets_label_map, "w", encoding="utf-8") as f:
        json.dump(label_map_data, f, indent=2, ensure_ascii=False)
    print(f"✅ Saved & synced label_map.json ({len(intents_list)} classes) to assets!")

    # 3. Add integer labels to samples
    for s in cleaned_samples:
        s["label"] = label_to_id[s["intent"]]

    # 4. Stratified Split (80% Train, 10% Val, 10% Test)
    grouped = defaultdict(list)
    for s in cleaned_samples:
        grouped[(s["intent"], s["lang"])].append(s)

    train_set, val_set, test_set = [], [], []
    random.seed(42)

    for _, group in grouped.items():
        random.shuffle(group)
        n = len(group)
        if n == 1:
            train_set.append(group[0])
        elif n == 2:
            train_set.append(group[0])
            val_set.append(group[1])
        else:
            n_train = max(1, int(0.8 * n))
            n_val = max(1, int(0.1 * n))
            train_set.extend(group[:n_train])
            val_set.extend(group[n_train:n_train + n_val])
            test_set.extend(group[n_train + n_val:])

    # Shuffle splits
    random.shuffle(train_set)
    random.shuffle(val_set)
    random.shuffle(test_set)

    def write_jsonl(path: str, data: List[Dict[str, Any]]):
        with open(path, "w", encoding="utf-8") as f:
            for item in data:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")

    train_path = os.path.join(OUTPUT_DIR, "train.jsonl")
    val_path = os.path.join(OUTPUT_DIR, "val.jsonl")
    test_path = os.path.join(OUTPUT_DIR, "test.jsonl")
    csv_path = os.path.join(OUTPUT_DIR, "dataset.csv")

    write_jsonl(train_path, train_set)
    write_jsonl(val_path, val_set)
    write_jsonl(test_path, test_set)

    # Write full CSV
    with open(csv_path, "w", encoding="utf-8") as f:
        f.write("intent,lang,label,text\n")
        for s in cleaned_samples:
            escaped_text = '"' + s["text"].replace('"', '""') + '"'
            f.write(f"{s['intent']},{s['lang']},{s['label']},{escaped_text}\n")

    # 5. Compile Statistics
    intent_counts = Counter(s["intent"] for s in cleaned_samples)
    lang_counts = Counter(s["lang"] for s in cleaned_samples)

    stats = {
        "total_samples": len(cleaned_samples),
        "train_samples": len(train_set),
        "val_samples": len(val_set),
        "test_samples": len(test_set),
        "intents_distribution": dict(intent_counts),
        "languages_distribution": dict(lang_counts)
    }

    stats_file = os.path.join(OUTPUT_DIR, "dataset_stats.json")
    with open(stats_file, "w", encoding="utf-8") as f:
        json.dump(stats, f, indent=2, ensure_ascii=False)

    print(f"📦 Train set: {len(train_set)} samples ({train_path})")
    print(f"📦 Val set:   {len(val_set)} samples ({val_path})")
    print(f"📦 Test set:  {len(test_set)} samples ({test_path})")
    print(f"📊 Dataset stats saved to {stats_file}")

    return stats


# =============================================================================
# 4. Main Entrypoint & Command Orchestration
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Unified Featherless AI Pipeline Generator (Predetermined Templates & mmBERT Dataset)"
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("FEATHERLESS_API_KEY", os.environ.get("OPENAI_API_KEY", "")),
        help="Featherless AI / OpenAI API Key"
    )
    parser.add_argument(
        "--base-url",
        default=os.environ.get("FEATHERLESS_BASE_URL", "https://api.featherless.ai/v1"),
        help="Base URL (default: https://api.featherless.ai/v1)"
    )
    parser.add_argument(
        "--model",
        default=os.environ.get("FEATHERLESS_MODEL", "deepseek-ai/DeepSeek-V4-Flash-0731"),
        help="Model ID (default: deepseek-ai/DeepSeek-V4-Flash-0731 or FEATHERLESS_MODEL env)"
    )
    parser.add_argument(
        "--samples-per-lang",
        type=int,
        default=15,
        help="Number of queries per language per intent (default: 15)"
    )
    parser.add_argument(
        "--include-seeds",
        action="store_true",
        help="Include offline seed samples in the dataset (default: False, Featherless is 100%% pure ground truth)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Run without calling external API, using curated baseline data"
    )

    args = parser.parse_args()

    api_key = args.api_key.strip()
    client = None

    if not api_key and not args.dry_run:
        print("⚠️ Warning: No FEATHERLESS_API_KEY provided.")
        print("   Running in curated baseline mode (--dry-run).")
        print("   To generate via Featherless AI, provide --api-key <YOUR_KEY> or set FEATHERLESS_API_KEY.")
        args.dry_run = True

    if not args.dry_run:
        try:
            from openai import OpenAI
            client = OpenAI(base_url=args.base_url, api_key=api_key, timeout=60.0)
            print(f"🔑 Initialized Featherless Client with model: '{args.model}'")

        except Exception as e:
            print(f"❌ Could not initialize OpenAI client: {e}")
            sys.exit(1)

    # 1. Generate Predetermined Templates & Prompts
    updated_intents = generate_featherless_templates(client, args.model, dry_run=args.dry_run)

    intents_payload = {
        "version": "2.0.0",
        "domain": "farm_tourism_highland_coffee",
        "supported_languages": LANGUAGES,
        "intents": updated_intents
    }

    # Save to data/intents.json & data/predetermined_templates.json
    with open(INTENTS_FILE, "w", encoding="utf-8") as f:
        json.dump(intents_payload, f, indent=2, ensure_ascii=False)
    with open(PREDETERMINED_FILE, "w", encoding="utf-8") as f:
        json.dump(intents_payload, f, indent=2, ensure_ascii=False)

    # Sync to Android assets/intents.json
    assets_intents_file = os.path.join(ASSETS_DIR, "intents.json")
    with open(assets_intents_file, "w", encoding="utf-8") as f:
        json.dump(intents_payload, f, indent=2, ensure_ascii=False)
    print(f"✅ Synchronized intents.json to Android assets: {assets_intents_file}")

    # 2. Generate Classification Dataset
    samples = generate_featherless_classifications(
        client=client,
        model=args.model,
        samples_per_lang=args.samples_per_lang,
        dry_run=args.dry_run,
        include_seeds=args.include_seeds
    )

    # 3. Post-Process & Stratify
    stats = process_and_save_dataset(samples)

    print("\n" + "=" * 76)
    print("🎉 ALL PIPELINE ARTIFACTS GENERATED SUCCESSFULLY!")
    print("=" * 76)
    print(f"  • Intents & Templates: {INTENTS_FILE}")
    print(f"  • Android Asset Sync:  {assets_intents_file}")
    print(f"  • Training Splits:     {OUTPUT_DIR}/train.jsonl, val.jsonl, test.jsonl")
    print(f"  • Total Class Dataset: {stats['total_samples']} samples across 10 intents and 8 languages")
    print("\nNext step to fine-tune mmBERT:")
    print("  python data/train_classifier.py --epochs 3 --export-onnx")


if __name__ == "__main__":
    main()
