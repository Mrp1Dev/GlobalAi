#!/usr/bin/env python3
"""
Multilingual Dataset Generator & Augmentor for Edge mmBERT Classifier.
Supports:
  1. Instant offline generation (combinatorial syntactic templates + curated seeds)
  2. Multi-threaded translation augmentation (via public Google Translate endpoint)
  3. LLM-based synthetic generation (via Featherless AI / DeepSeek-V4 or any OpenAI-compatible API)
  4. Stratified Train / Validation / Test splits with label maps and CSV/JSONL exports.
"""

import os
import sys
import json
import time
import random
import argparse
import urllib.request
import urllib.parse
from concurrent.futures import ThreadPoolExecutor, as_completed
from typing import List, Dict, Any, Tuple, Optional, Set
from collections import Counter, defaultdict

# Ensure stdout handles UTF-8 for Devanagari and Latin accents on Windows
if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")
if hasattr(sys.stderr, "reconfigure"):
    sys.stderr.reconfigure(encoding="utf-8")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(SCRIPT_DIR)
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)
if PROJECT_ROOT not in sys.path:
    sys.path.insert(0, PROJECT_ROOT)

try:
    from data.seed_data import CURATED_SEED_SAMPLES
except ImportError:
    from seed_data import CURATED_SEED_SAMPLES

INTENTS_FILE = os.path.join(SCRIPT_DIR, "intents.json")

LANGUAGES = {
    "en": "English",
    "es": "Spanish",
    "fr": "French",
    "de": "German",
    "it": "Italian",
    "hi": "Hindi",
    "pt": "Portuguese",
    "nl": "Dutch"
}

# =============================================================================
# 1. Comprehensive Syntactic Templates & Entities across ALL 9 Intents
# =============================================================================

SYNTACTIC_TEMPLATES = {
    "price_tour": {
        "en": [
            "{greeting} how much does {entity} cost{punct}",
            "{greeting} what is the ticket price for {entity}{punct}",
            "Can you tell me the rates for {entity}{punct}",
            "How much per person for {entity}{punct}",
            "Is {entity} free or how much is admission{punct}"
        ],
        "es": [
            "{greeting} ¿cuánto cuesta {entity}{punct}",
            "{greeting} ¿cuál es el precio de {entity}{punct}",
            "¿Cuánto cobran por persona para {entity}{punct}",
            "¿Tienen tarifas para {entity}{punct}",
            "¿Cuánto sale la entrada a {entity}{punct}"
        ],
        "fr": [
            "{greeting} combien coûte {entity}{punct}",
            "{greeting} quel est le tarif de {entity}{punct}",
            "Combien coûte le billet pour {entity}{punct}",
            "Quel est le prix par personne pour {entity}{punct}"
        ],
        "de": [
            "{greeting} wie viel kostet {entity}{punct}",
            "{greeting} was kostet der Eintritt für {entity}{punct}",
            "Wie hoch ist der Preis pro Person für {entity}{punct}",
            "Was verlangen Sie für {entity}{punct}"
        ],
        "it": [
            "{greeting} quanto costa {entity}{punct}",
            "{greeting} qual è il prezzo per {entity}{punct}",
            "Quanto si paga a persona per {entity}{punct}",
            "Quali sono le tariffe di {entity}{punct}"
        ],
        "hi": [
            "{greeting} {entity} की टिकट कितने की है{punct}",
            "{greeting} {entity} का प्रति व्यक्ति किराया कितना है{punct}",
            "{entity} देखने का कुल कितना खर्चा आएगा{punct}"
        ],
        "pt": [
            "{greeting} quanto custa {entity}{punct}",
            "{greeting} qual o valor da entrada para {entity}{punct}",
            "Quanto cobram por pessoa para {entity}{punct}"
        ],
        "nl": [
            "{greeting} hoeveel kost {entity}{punct}",
            "{greeting} wat is de toegangsprijs voor {entity}{punct}",
            "Wat zijn de tarieven per persoon voor {entity}{punct}"
        ]
    },
    "price_produce": {
        "en": [
            "{greeting} how much do you charge for {entity}{punct}",
            "{greeting} what is the price of {entity}{punct}",
            "Can we purchase {entity} directly from the farm{punct}",
            "How much per kilo for {entity}{punct}"
        ],
        "es": [
            "{greeting} ¿a qué precio venden {entity}{punct}",
            "{greeting} ¿cuánto cuesta {entity} por kilo{punct}",
            "¿Podemos comprar {entity} en la granja{punct}"
        ],
        "fr": [
            "{greeting} quel est le prix de {entity}{punct}",
            "{greeting} combien vendez-vous {entity}{punct}",
            "Peut-on acheter {entity} sur place{punct}"
        ],
        "de": [
            "{greeting} was kostet {entity} bei Ihnen{punct}",
            "{greeting} wie teuer ist {entity} im Hofladen{punct}",
            "Kann man frisches {entity} kaufen{punct}"
        ],
        "it": [
            "{greeting} quanto costa {entity}{punct}",
            "{greeting} a quanto vendete {entity}{punct}",
            "Possiamo comprare {entity} direttamente da voi{punct}"
        ],
        "hi": [
            "{greeting} खेत का {entity} कितने रुपये का है{punct}",
            "{greeting} {entity} का क्या भाव है{punct}",
            "क्या हम यहाँ से ताजा {entity} खरीद सकते हैं{punct}"
        ],
        "pt": [
            "{greeting} quanto custa {entity} da fazenda{punct}",
            "{greeting} qual o preço de {entity}{punct}",
            "Vocês vendem {entity} aqui{punct}"
        ],
        "nl": [
            "{greeting} wat kost {entity}{punct}",
            "{greeting} hoeveel kost {entity} per kilo{punct}",
            "Verkopen jullie ook {entity}{punct}"
        ]
    },
    "visitation_hours": {
        "en": [
            "{greeting} what are your {entity}{punct}",
            "{greeting} what time does the farm {entity}{punct}",
            "Are you open on {entity}{punct}",
            "Until what hour can visitors {entity}{punct}"
        ],
        "es": [
            "{greeting} ¿cuáles son sus {entity}{punct}",
            "{greeting} ¿a qué hora {entity} la granja{punct}",
            "¿Están abiertos en {entity}{punct}"
        ],
        "fr": [
            "{greeting} quels sont vos {entity}{punct}",
            "{greeting} à quelle heure {entity} la ferme{punct}",
            "La ferme est-elle ouverte le {entity}{punct}"
        ],
        "de": [
            "{greeting} wie sind die {entity}{punct}",
            "{greeting} wann {entity} der Hof{punct}",
            "Haben Sie am {entity} geöffnet{punct}"
        ],
        "it": [
            "{greeting} quali sono i vostri {entity}{punct}",
            "{greeting} a che ora {entity} la fattoria{punct}",
            "Siete aperti la {entity}{punct}"
        ],
        "hi": [
            "{greeting} खेत के {entity} क्या हैं{punct}",
            "{greeting} फार्म पर्यटकों के लिए कब {entity} है{punct}",
            "क्या फार्म {entity} खुला रहता है{punct}"
        ],
        "pt": [
            "{greeting} quais são os {entity}{punct}",
            "{greeting} a que horas {entity} a propriedade{punct}"
        ],
        "nl": [
            "{greeting} wat zijn de {entity}{punct}",
            "{greeting} hoe laat {entity} de boerderij{punct}"
        ]
    },
    "farm_location_directions": {
        "en": [
            "{greeting} where is the farm located and how do we {entity}{punct}",
            "{greeting} can we reach the farm by {entity}{punct}",
            "Is there {entity} available for visitors{punct}",
            "Can you provide directions or {entity}{punct}"
        ],
        "es": [
            "{greeting} ¿dónde queda la granja y cómo podemos {entity}{punct}",
            "{greeting} ¿se puede llegar en {entity}{punct}",
            "¿Tienen {entity} para los turistas{punct}"
        ],
        "fr": [
            "{greeting} où se trouve la ferme et comment {entity}{punct}",
            "{greeting} peut-on venir en {entity}{punct}",
            "Y a-t-il {entity} sur place{punct}"
        ],
        "de": [
            "{greeting} wo liegt der Bauernhof und wie können wir {entity}{punct}",
            "{greeting} kann man mit {entity} anreisen{punct}",
            "Gibt es vor Ort {entity}{punct}"
        ],
        "it": [
            "{greeting} dove si trova la fattoria e come si {entity}{punct}",
            "{greeting} è possibile arrivare con {entity}{punct}",
            "C'è {entity} per le auto{punct}"
        ],
        "hi": [
            "{greeting} खेत का पता क्या है और हम कैसे {entity}{punct}",
            "{greeting} क्या यहाँ {entity} से आया जा सकता है{punct}",
            "क्या खेत पर {entity} की सुविधा है{punct}"
        ],
        "pt": [
            "{greeting} onde fica a fazenda e como {entity}{punct}",
            "{greeting} tem {entity} no local{punct}"
        ],
        "nl": [
            "{greeting} waar ligt de boerderij en hoe kunnen we er {entity}{punct}",
            "{greeting} is er {entity} aanwezig{punct}"
        ]
    },
    "activities_available": {
        "en": [
            "{greeting} what {entity} can visitors experience at the farm{punct}",
            "{greeting} can our children try {entity}{punct}",
            "Do you offer {entity} during the tour{punct}",
            "Are there hands-on {entity} available{punct}"
        ],
        "es": [
            "{greeting} ¿qué {entity} se pueden hacer en la granja{punct}",
            "{greeting} ¿los niños pueden probar {entity}{punct}",
            "¿Tienen {entity} para turistas{punct}"
        ],
        "fr": [
            "{greeting} quelles {entity} proposez-vous aux visiteurs{punct}",
            "{greeting} les enfants peuvent-ils faire {entity}{punct}",
            "Proposez-vous {entity} sur place{punct}"
        ],
        "de": [
            "{greeting} welche {entity} kann man auf dem Hof machen{punct}",
            "{greeting} dürfen Kinder {entity} ausprobieren{punct}",
            "Gibt es {entity} für Besucher{punct}"
        ],
        "it": [
            "{greeting} quali {entity} si possono svolgere in agriturismo{punct}",
            "{greeting} i bambini possono fare {entity}{punct}",
            "Offrite {entity} per gli ospiti{punct}"
        ],
        "hi": [
            "{greeting} खेत पर पर्यटक कौन-सी {entity} कर सकते हैं{punct}",
            "{greeting} क्या बच्चे {entity} सीख सकते हैं{punct}",
            "क्या यहाँ {entity} की सुविधा उपलब्ध है{punct}"
        ],
        "pt": [
            "{greeting} quais {entity} estão disponíveis para os visitantes{punct}",
            "{greeting} as crianças podem participar de {entity}{punct}"
        ],
        "nl": [
            "{greeting} welke {entity} kunnen gasten doen op de boerderij{punct}",
            "{greeting} mogen kinderen {entity} proberen{punct}"
        ]
    },
    "amenities_food": {
        "en": [
            "{greeting} do you serve {entity} at the farm{punct}",
            "{greeting} are there clean {entity} for guests{punct}",
            "Is there {entity} available on the property{punct}"
        ],
        "es": [
            "{greeting} ¿ofrecen {entity} a los visitantes{punct}",
            "{greeting} ¿tienen {entity} disponibles{punct}",
            "¿Hay {entity} en la finca{punct}"
        ],
        "fr": [
            "{greeting} servez-vous {entity} à la ferme{punct}",
            "{greeting} y a-t-il des {entity} propres pour les visiteurs{punct}",
            "Avez-vous {entity} sur place{punct}"
        ],
        "de": [
            "{greeting} gibt es auf dem Hof {entity}{punct}",
            "{greeting} sind saubere {entity} vorhanden{punct}"
        ],
        "it": [
            "{greeting} servite {entity} in agriturismo{punct}",
            "{greeting} ci sono {entity} per i visitatori{punct}"
        ],
        "hi": [
            "{greeting} क्या खेत पर {entity} मिलता है{punct}",
            "{greeting} क्या पर्यटकों के लिए साफ {entity} है{punct}"
        ],
        "pt": [
            "{greeting} vocês servem {entity} na propriedade{punct}",
            "{greeting} tem {entity} limpos para os turistas{punct}"
        ],
        "nl": [
            "{greeting} serveren jullie {entity} op de boerderij{punct}",
            "{greeting} zijn er nette {entity} aanwezig{punct}"
        ]
    },
    "pet_policy": {
        "en": [
            "{greeting} are {entity} allowed on the farm{punct}",
            "{greeting} can I bring my {entity} with me{punct}",
            "What is your policy regarding {entity}{punct}"
        ],
        "es": [
            "{greeting} ¿se permiten {entity} en la granja{punct}",
            "{greeting} ¿puedo llevar a mi {entity} al recorrido{punct}",
            "¿Cuál es la política sobre {entity}{punct}"
        ],
        "fr": [
            "{greeting} les {entity} sont-ils acceptés à la ferme{punct}",
            "{greeting} puis-je emmener mon {entity} avec moi{punct}"
        ],
        "de": [
            "{greeting} sind {entity} auf dem Hof erlaubt{punct}",
            "{greeting} darf ich meinen {entity} mitbringen{punct}"
        ],
        "it": [
            "{greeting} i {entity} sono ammessi durante la visita{punct}",
            "{greeting} posso portare il mio {entity}{punct}"
        ],
        "hi": [
            "{greeting} क्या खेत में {entity} लाने की अनुमति है{punct}",
            "{greeting} क्या हम अपना {entity} साथ ला सकते हैं{punct}"
        ],
        "pt": [
            "{greeting} {entity} são permitidos na fazenda{punct}",
            "{greeting} posso levar meu {entity} comigo{punct}"
        ],
        "nl": [
            "{greeting} zijn {entity} toegestaan op het terrein{punct}",
            "{greeting} mag ik mijn {entity} meenemen{punct}"
        ]
    },
    "booking_reservation": {
        "en": [
            "{greeting} do we need to {entity} before visiting{punct}",
            "{greeting} can we just walk in or is {entity} required{punct}",
            "How do I make {entity} for a group{punct}"
        ],
        "es": [
            "{greeting} ¿es necesario {entity} con antelación{punct}",
            "{greeting} ¿podemos llegar directamente o se requiere {entity}{punct}",
            "¿Cómo puedo hacer {entity} para varias personas{punct}"
        ],
        "fr": [
            "{greeting} faut-il {entity} à l'avance{punct}",
            "{greeting} peut-on venir sans {entity} préalable{punct}"
        ],
        "de": [
            "{greeting} muss man im Voraus {entity}{punct}",
            "{greeting} ist eine {entity} unbedingt erforderlich{punct}"
        ],
        "it": [
            "{greeting} è necessario {entity} prima di venire{punct}",
            "{greeting} possiamo venire senza {entity}{punct}"
        ],
        "hi": [
            "{greeting} क्या आने से पहले {entity} कराना जरूरी है{punct}",
            "{greeting} क्या हम बिना {entity} सीधे आ सकते हैं{punct}"
        ],
        "pt": [
            "{greeting} é preciso {entity} com antecedência{punct}",
            "{greeting} podemos ir sem {entity}{punct}"
        ],
        "nl": [
            "{greeting} moeten we vooraf {entity}{punct}",
            "{greeting} is {entity} noodzakelijk voor het bezoek{punct}"
        ]
    },
    "out_of_scope": {
        "en": [
            "{greeting} what is the {entity} going to be like tomorrow{punct}",
            "{greeting} can you tell me where the nearest {entity} is located{punct}",
            "Do you know who won {entity}{punct}",
            "Can you help me configure {entity}{punct}"
        ],
        "es": [
            "{greeting} ¿cómo estará {entity} mañana{punct}",
            "{greeting} ¿dónde queda el más cercano {entity}{punct}",
            "¿Sabes quién ganó {entity}{punct}"
        ],
        "fr": [
            "{greeting} quel temps fera-t-il {entity}{punct}",
            "{greeting} où se trouve le plus proche {entity}{punct}"
        ],
        "de": [
            "{greeting} wie wird das {entity} morgen{punct}",
            "{greeting} wo ist die nächste {entity}{punct}"
        ],
        "it": [
            "{greeting} che tempo farà {entity}{punct}",
            "{greeting} dov'è la più vicina {entity}{punct}"
        ],
        "hi": [
            "{greeting} कल यहाँ का {entity} कैसा रहेगा{punct}",
            "{greeting} क्या आप जानते हैं कि सबसे नजदीकी {entity} कहाँ है{punct}"
        ],
        "pt": [
            "{greeting} como estará {entity} amanhã{punct}",
            "{greeting} onde fica o {entity} mais próximo{punct}"
        ],
        "nl": [
            "{greeting} wat voor {entity} wordt het morgen{punct}",
            "{greeting} waar is de dichtstbijzijnde {entity}{punct}"
        ]
    }
}

SYNTACTIC_ENTITIES = {
    "price_tour": {
        "en": {"greeting": ["Hello,", "Hi,", "Good morning,", ""], "entity": ["the guided tour", "a farm tour", "the orchard walk", "admission"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", "Buenos días,", ""], "entity": ["el tour guiado", "la visita a la finca", "la entrada general"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", "Salut,", ""], "entity": ["la visite guidée", "l'entrée à la ferme", "le billet adulte"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", "Guten Tag,", ""], "entity": ["die Hofführung", "der Eintritt zum Bauernhof", "die Besichtigung"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", "Buongiorno,", ""], "entity": ["il tour guidato", "la visita alla fattoria", "il biglietto d'ingresso"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", "नमस्कार,", ""], "entity": ["फार्म टूर", "खेत के भ्रमण", "गाइडेड टूर"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", "Bom dia,", ""], "entity": ["o passeio guiado", "a visita à fazenda", "a entrada"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", "Goedemorgen,", ""], "entity": ["de rondleiding", "het boerderijbezoek", "de entree"], "punct": ["?", "?"]}
    },
    "price_produce": {
        "en": {"greeting": ["Hello,", "Hi,", ""], "entity": ["organic honey", "fresh cow milk", "seasonal fruits", "farm eggs", "fresh vegetables"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", "Buenas tardes,", ""], "entity": ["la miel orgánica", "la leche fresca", "las frutas de temporada", "las verduras ecológicas"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["le miel bio", "le lait frais", "les fruits de saison", "les légumes frais"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["der Bio-Honig", "die frische Rohmilch", "das saisonale Obst", "die Hof-Eier"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["il miele biologico", "il latte fresco", "la frutta di stagione", "le verdure fresche"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["ताजा शहद", "गाय का दूध", "जैविक फल", "ताजी सब्जियां"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["o mel orgânico", "o leite fresco", "as frutas da época", "os ovos caipiras"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["de biologische honing", "de verse melk", "het seizoensfruit", "de verse eieren"], "punct": ["?", "?"]}
    },
    "visitation_hours": {
        "en": {"greeting": ["Hello,", "Hi,", ""], "entity": ["visiting hours", "open and close", "Sundays", "enter in the evening"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["horarios de visita", "abre y cierra", "los domingos"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["horaires d'ouverture", "ouvre le matin", "dimanche"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["Öffnungszeiten", "öffnet morgens", "Wochenende"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["orari di apertura", "apre e chiude", "domenica"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["खुलने का समय", "खुलता और बंद होता", "रविवार को"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["horários de visitação", "abre para o público"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["openingstijden", "opent voor bezoekers"], "punct": ["?", "?"]}
    },
    "farm_location_directions": {
        "en": {"greeting": ["Hello,", ""], "entity": ["reach there by car", "public transport or bus", "free parking", "GPS coordinates"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["llegar en coche", "autobús o tren", "aparcamiento gratuito"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["venir en voiture", "train ou bus", "un parking gratuit"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["mit dem Auto anreisen", "Bus und Bahn", "kostenlose Parkplätze"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["arrivare in macchina", "i mezzi pubblici", "un parcheggio"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["रास्ता खोजें", "बस या ट्रेन", "पार्किंग"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["chegar de carro", "estacionamento gratuito"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["komen met de auto", "parkeergelegenheid"], "punct": ["?", "?"]}
    },
    "activities_available": {
        "en": {"greeting": ["Hello,", ""], "entity": ["activities", "milking cows", "fruit picking", "tractor rides", "farming workshops"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["actividades", "ordeñar vacas", "cosechar frutas", "paseos en tractor"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["activités", "traire les vaches", "cueillir des fruits", "balades en tracteur"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["Aktivitäten", "Kühe melken", "Obst pflücken", "Traktorfahrten"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["attività", "mungere le mucche", "raccogliere frutta", "giri in trattore"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["गतिविधियां", "दूध दुहना", "फल तोड़ना", "ट्रैक्टर की सवारी"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["atividades", "tirar leite de vaca", "colheita de frutas"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["activiteiten", "koeien melken", "fruit plukken"], "punct": ["?", "?"]}
    },
    "amenities_food": {
        "en": {"greeting": ["Hello,", ""], "entity": ["farm lunch or tea", "restrooms", "drinking water", "vegetarian food"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["almuerzo campestre", "baños limpios", "agua potable", "comida vegetariana"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["le déjeuner traditionnel", "toilettes", "eau potable"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["Mittagessen oder Tee", "Toiletten", "Trinkwasser"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["pranzo tipico", "servizi igienici", "acqua potabile"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["देशी दोपहर का खाना या चाय", "शौचालय", "पीने का साफ पानी"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["almoço caseiro", "banheiros", "água potável"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["lunch of thee", "schone toiletten", "drinkwater"], "punct": ["?", "?"]}
    },
    "pet_policy": {
        "en": {"greeting": ["Hello,", ""], "entity": ["dogs and pets", "pet dog on a leash", "domestic animals"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["perros y mascotas", "perro con correa", "animales de compañía"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["chiens et animaux", "chien en laisse"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["Hunde und Haustiere", "angeleinter Hund"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["cani e animali domestici", "cane al guinzaglio"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["कुत्ते और पालतू जानवर", "पालतू कुत्ता"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["cachorros e animais de estimação", "cachorro com coleira"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["honden en huisdieren", "aangelijnde hond"], "punct": ["?", "?"]}
    },
    "booking_reservation": {
        "en": {"greeting": ["Hello,", ""], "entity": ["book in advance", "prior booking", "a reservation"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["reservar con anticipación", "reserva previa", "una reserva"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["réserver à l'avance", "réservation"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["reservieren", "Voranmeldung"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["prenotare", "prenotazione anticipata"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["पहले से बुकिंग", "अग्रिम बुकिंग"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["agendar com antecedência", "reserva"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["reserveren", "een afspraak maken"], "punct": ["?", "?"]}
    },
    "out_of_scope": {
        "en": {"greeting": ["Hello,", ""], "entity": ["weather", "hospital", "the football match", "my phone settings"], "punct": ["?", "?"]},
        "es": {"greeting": ["Hola,", ""], "entity": ["el clima", "la farmacia", "el partido de fútbol"], "punct": ["?", "?"]},
        "fr": {"greeting": ["Bonjour,", ""], "entity": ["la météo", "la pharmacie"], "punct": ["?", "?"]},
        "de": {"greeting": ["Hallo,", ""], "entity": ["Wetter", "Apotheke"], "punct": ["?", "?"]},
        "it": {"greeting": ["Ciao,", ""], "entity": ["tempo", "farmacia"], "punct": ["?", "?"]},
        "hi": {"greeting": ["नमस्ते,", ""], "entity": ["मौसम", "अस्पताल"], "punct": ["?", "?"]},
        "pt": {"greeting": ["Olá,", ""], "entity": ["o tempo", "o hospital"], "punct": ["?", "?"]},
        "nl": {"greeting": ["Hallo,", ""], "entity": ["het weer", "het ziekenhuis"], "punct": ["?", "?"]}
    }
}


def generate_syntactic_samples(count_per_lang_per_intent: int = 8) -> List[Dict[str, str]]:
    """Generates synthetic questions combinatorially across all 9 intents and 8 languages."""
    generated = []
    for intent, lang_templates in SYNTACTIC_TEMPLATES.items():
        entity_bank = SYNTACTIC_ENTITIES.get(intent, {})
        for lang, templates in lang_templates.items():
            if lang not in entity_bank:
                continue
            config = entity_bank[lang]
            greetings = config["greeting"]
            entities = config["entity"]
            puncts = config["punct"]

            combos = []
            for t in templates:
                for g in greetings:
                    for e in entities:
                        p = random.choice(puncts)
                        text = t.format(greeting=g, entity=e, punct=p).strip()
                        text = " ".join(text.split())
                        text = text.replace("¿ ", "¿").replace("¡ ", "¡")
                        combos.append(text)

            random.shuffle(combos)
            selected = combos[:count_per_lang_per_intent]
            for text in selected:
                generated.append({"intent": intent, "lang": lang, "text": text})
    return generated


# =============================================================================
# 2. Multi-Threaded Translation Augmentation (Zero API Key)
# =============================================================================

def translate_single_text(text: str, source_lang: str, target_lang: str) -> Optional[str]:
    if source_lang == target_lang:
        return text
    encoded = urllib.parse.quote(text.strip())
    url = (
        f"https://translate.googleapis.com/translate_a/single"
        f"?client=gtx&sl={source_lang}&tl={target_lang}&dt=t&q={encoded}"
    )
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": (
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                "AppleWebKit/537.36 (KHTML, like Gecko) "
                "Chrome/124.0.0.0 Safari/537.36"
            )
        }
    )
    try:
        with urllib.request.urlopen(req, timeout=6) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            translated = "".join([part[0] for part in data[0] if part and part[0]]).strip()
            return translated if translated else None
    except Exception:
        return None


def run_translation_augmentation(
    seed_samples: List[Dict[str, str]],
    max_seeds: int = 15,
    max_workers: int = 4
) -> List[Dict[str, str]]:
    """Runs concurrent translation of English seeds across other languages."""
    en_seeds = [s for s in seed_samples if s.get("lang") == "en"]
    sampled = random.sample(en_seeds, min(max_seeds, len(en_seeds)))
    target_langs = ["es", "fr", "de", "it", "hi", "pt", "nl"]

    tasks = []
    for s in sampled:
        for tgt in target_langs:
            tasks.append((s["text"], s["intent"], tgt))

    print(f"   Translating {len(tasks)} queries across languages using {max_workers} threads...")
    augmented: List[Dict[str, str]] = []

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        future_to_task = {
            executor.submit(translate_single_text, text, "en", tgt): (text, intent, tgt)
            for text, intent, tgt in tasks
        }
        for future in as_completed(future_to_task):
            text, intent, tgt = future_to_task[future]
            res = future.result()
            if res:
                augmented.append({"intent": intent, "lang": tgt, "text": res})

    return augmented


# =============================================================================
# 3. Featherless AI / OpenAI API High-Throughput Generator
# =============================================================================

def generate_with_llm(
    api_key: str,
    base_url: str = "https://api.featherless.ai/v1",
    model: str = "deepseek-ai/DeepSeek-V4-Flash-0731",
    samples_per_intent: int = 15,
    target_languages: Optional[List[str]] = None
) -> List[Dict[str, str]]:
    """
    Connects to Featherless AI (or any OpenAI-compatible provider) to generate
    synthetic tourist inquiries.
    """
    try:
        from openai import OpenAI
    except ImportError:
        print("❌ Error: 'openai' package not installed. Run 'pip install openai' first.")
        return []

    client = OpenAI(base_url=base_url, api_key=api_key)
    with open(INTENTS_FILE, "r", encoding="utf-8") as f:
        intent_metadata = json.load(f)["intents"]

    target_langs = target_languages or list(LANGUAGES.keys())
    lang_names_str = ", ".join([f"{code} ({LANGUAGES.get(code, code)})" for code in target_langs])

    generated_samples = []

    for item in intent_metadata:
        intent_name = item["intent"]
        desc = item["description"]
        print(f"🤖 Calling LLM for intent: '{intent_name}'...")

        prompt = f"""You are an expert NLP data generator for a multilingual on-device farm tourism assistant.
We need synthetic tourist inquiries for:
- Intent: "{intent_name}"
- Description: {desc}
- Target Languages: {lang_names_str}

Generate {samples_per_intent} diverse, realistic natural language tourist queries for EACH of the target languages.
Ensure wide linguistic variety:
- Mix of short direct questions, long polite sentences, colloquial phrasing, and family/group scenarios.
- Do NOT include numbering or markdown formatting.
- Strict requirement: Return ONLY a valid JSON array of objects with keys: "intent", "lang", "text".

Example format:
[
  {{"intent": "{intent_name}", "lang": "en", "text": "Can we visit the farm tomorrow?"}},
  {{"intent": "{intent_name}", "lang": "es", "text": "¿A qué hora abren mañana?"}}
]
"""
        try:
            response = client.chat.completions.create(
                model=model,
                temperature=0.75,
                max_tokens=4096,
                messages=[
                    {"role": "system", "content": "You are a professional multilingual dataset generation system. Output strict JSON only."},
                    {"role": "user", "content": prompt}
                ]
            )
            content = response.choices[0].message.content.strip()

            if content.startswith("```"):
                lines = content.splitlines()
                if lines[0].startswith("```"):
                    lines = lines[1:]
                if lines and lines[-1].startswith("```"):
                    lines = lines[:-1]
                content = "\n".join(lines).strip()

            parsed = json.loads(content)
            if isinstance(parsed, list):
                valid_items = 0
                for obj in parsed:
                    if "text" in obj and "intent" in obj:
                        obj["intent"] = intent_name
                        lang_code = obj.get("lang", "en").lower().strip()
                        if lang_code in LANGUAGES:
                            obj["lang"] = lang_code
                        generated_samples.append(obj)
                        valid_items += 1
                print(f"   ↳ Successfully generated {valid_items} samples for {intent_name}")
            else:
                print(f"   ⚠️ Warning: LLM output was not a JSON list for {intent_name}")
        except Exception as e:
            print(f"   ❌ Failed to generate with LLM for {intent_name}: {e}")

    return generated_samples


# =============================================================================
# 4. Dataset Processing & Stratified Splitter
# =============================================================================

def deduplicate_samples(samples: List[Dict[str, str]]) -> List[Dict[str, str]]:
    seen: Set[str] = set()
    unique: List[Dict[str, str]] = []
    for item in samples:
        norm = " ".join(item["text"].lower().strip().split())
        key = f"{item['intent']}::{item.get('lang', 'en')}::{norm}"
        if key not in seen and len(norm) > 3:
            seen.add(key)
            unique.append({
                "intent": item["intent"],
                "lang": item.get("lang", "en"),
                "text": item["text"].strip()
            })
    return unique


def create_stratified_split(
    samples: List[Dict[str, str]],
    train_ratio: float = 0.80,
    val_ratio: float = 0.10,
    seed: int = 42
) -> Tuple[List[Dict[str, str]], List[Dict[str, str]], List[Dict[str, str]]]:
    random.seed(seed)
    buckets = defaultdict(list)
    for sample in samples:
        key = (sample["intent"], sample["lang"])
        buckets[key].append(sample)

    train_set: List[Dict[str, str]] = []
    val_set: List[Dict[str, str]] = []
    test_set: List[Dict[str, str]] = []

    for key, items in buckets.items():
        random.shuffle(items)
        n = len(items)
        if n == 1:
            train_set.append(items[0])
            continue
        elif n == 2:
            train_set.append(items[0])
            test_set.append(items[1])
            continue

        n_train = max(1, int(round(n * train_ratio)))
        n_val = max(1, int(round(n * val_ratio)))
        if n_train + n_val >= n:
            n_train = max(1, n - 2)
            n_val = 1

        train_chunk = items[:n_train]
        val_chunk = items[n_train:n_train + n_val]
        test_chunk = items[n_train + n_val:]

        train_set.extend(train_chunk)
        val_set.extend(val_chunk)
        test_set.extend(test_chunk)

    random.shuffle(train_set)
    random.shuffle(val_set)
    random.shuffle(test_set)
    return train_set, val_set, test_set


def export_dataset(
    samples: List[Dict[str, str]],
    output_dir: str,
    train_ratio: float = 0.80,
    val_ratio: float = 0.10
) -> Dict[str, Any]:
    os.makedirs(output_dir, exist_ok=True)
    unique_samples = deduplicate_samples(samples)

    intents = sorted(list(set(s["intent"] for s in unique_samples)))
    label_to_id = {intent: i for i, intent in enumerate(intents)}
    id_to_label = {i: intent for i, intent in enumerate(intents)}

    for s in unique_samples:
        s["label"] = label_to_id[s["intent"]]

    train_set, val_set, test_set = create_stratified_split(
        unique_samples, train_ratio=train_ratio, val_ratio=val_ratio
    )

    def write_jsonl(filename: str, data: List[Dict[str, str]]):
        path = os.path.join(output_dir, filename)
        with open(path, "w", encoding="utf-8") as f:
            for item in data:
                f.write(json.dumps(item, ensure_ascii=False) + "\n")
        return path

    write_jsonl("dataset_full.jsonl", unique_samples)
    write_jsonl("train.jsonl", train_set)
    write_jsonl("val.jsonl", val_set)
    write_jsonl("test.jsonl", test_set)

    import csv
    csv_path = os.path.join(output_dir, "dataset.csv")
    with open(csv_path, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["intent", "label", "lang", "text"])
        writer.writeheader()
        writer.writerows(unique_samples)

    label_map_path = os.path.join(output_dir, "label_map.json")
    with open(label_map_path, "w", encoding="utf-8") as f:
        json.dump({
            "label_to_id": label_to_id,
            "id_to_label": id_to_label,
            "num_labels": len(label_to_id)
        }, f, indent=2, ensure_ascii=False)

    stats: Dict[str, Any] = {
        "total_samples": len(unique_samples),
        "split_counts": {
            "train": len(train_set),
            "val": len(val_set),
            "test": len(test_set)
        },
        "intent_distribution": dict(Counter(s["intent"] for s in unique_samples)),
        "language_distribution": dict(Counter(s["lang"] for s in unique_samples)),
        "languages": list(set(s["lang"] for s in unique_samples)),
        "intents": intents
    }

    stats_path = os.path.join(output_dir, "dataset_stats.json")
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, indent=2, ensure_ascii=False)

    return stats


# =============================================================================
# 5. CLI Entry Point
# =============================================================================

def main():
    parser = argparse.ArgumentParser(
        description="Multilingual Farm Tourism Dataset Generator for mmBERT Intent Classifier"
    )
    parser.add_argument(
        "--mode",
        choices=["seed", "syntax", "augment", "llm", "all"],
        default="syntax",
        help="Generation mode: 'seed' (curated seeds), 'syntax' (seeds + offline syntactic combos), 'augment' (+ translation), 'llm' (Featherless API), or 'all'"
    )
    parser.add_argument(
        "--output-dir",
        default=os.path.join(SCRIPT_DIR, "output"),
        help="Directory to save generated datasets (JSONL, CSV, label_map)"
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("FEATHERLESS_API_KEY", os.environ.get("OPENAI_API_KEY", "")),
        help="API Key for Featherless AI or OpenAI (optional unless using --mode llm)"
    )
    parser.add_argument(
        "--base-url",
        default=os.environ.get("FEATHERLESS_BASE_URL", "https://api.featherless.ai/v1"),
        help="Base URL for LLM API (default: https://api.featherless.ai/v1)"
    )
    parser.add_argument(
        "--model",
        default="deepseek-ai/DeepSeek-V4-Flash-0731",
        help="Model identifier on Featherless AI"
    )
    parser.add_argument(
        "--llm-samples-per-intent",
        type=int,
        default=15,
        help="Number of samples to generate per intent when running LLM mode"
    )
    parser.add_argument(
        "--enable-translation-augment",
        action="store_true",
        help="Run cross-lingual translation augmentation across English seeds"
    )

    args = parser.parse_args()

    print("=" * 72)
    print("🌾 MULTILINGUAL DATASET GENERATOR (mmBERT Edge Classifier)")
    print(f"   Mode       : {args.mode.upper()}")
    print(f"   Output Dir : {args.output_dir}")
    print("=" * 72)

    all_samples: List[Dict[str, str]] = []

    # 1. Curated seeds (always loaded)
    print(f"\n📦 Loading curated seed samples...")
    print(f"   Loaded {len(CURATED_SEED_SAMPLES)} high-quality native seed questions.")
    all_samples.extend(CURATED_SEED_SAMPLES)

    # 2. Syntactic combinations
    if args.mode in ["syntax", "augment", "all"]:
        print(f"\n⚡ Generating programmatic syntactic combinations...")
        syntactic_samples = generate_syntactic_samples(count_per_lang_per_intent=8)
        print(f"   Generated {len(syntactic_samples)} syntactic variations.")
        all_samples.extend(syntactic_samples)

    # 3. Translation Augmentation
    if args.enable_translation_augment or args.mode in ["augment", "all"]:
        print(f"\n🌐 Running multi-threaded cross-lingual translation augmentation...")
        trans_samples = run_translation_augmentation(CURATED_SEED_SAMPLES, max_seeds=12, max_workers=4)
        print(f"   Generated {len(trans_samples)} cross-lingual translated variations.")
        all_samples.extend(trans_samples)

    # 4. LLM Generation
    if args.mode in ["llm", "all"]:
        if not args.api_key:
            print("\n⚠️ No API key found for LLM generation.")
            print("   Set FEATHERLESS_API_KEY environment variable or pass --api-key <KEY>.")
            print("   Skipping LLM generation stage.")
        else:
            print(f"\n🚀 Invoking LLM generation with model: {args.model}...")
            llm_samples = generate_with_llm(
                api_key=args.api_key,
                base_url=args.base_url,
                model=args.model,
                samples_per_intent=args.llm_samples_per_intent
            )
            all_samples.extend(llm_samples)

    # 5. Export and Summarize
    print("\n📊 Compiling, deduplicating, and partitioning dataset...")
    stats = export_dataset(all_samples, args.output_dir)

    print("=" * 72)
    print("✅ DATASET GENERATION COMPLETE!")
    print(f"   Total Unique Samples : {stats['total_samples']}")
    print(f"   Training Split (80%) : {stats['split_counts']['train']}")
    print(f"   Val Split      (10%) : {stats['split_counts']['val']}")
    print(f"   Test Split     (10%) : {stats['split_counts']['test']}")
    print(f"   Intents Covered ({len(stats['intents'])}): {', '.join(stats['intents'])}")
    print(f"   Languages ({len(stats['languages'])}): {', '.join(sorted(stats['languages']))}")
    print("\n   Output Files Generated:")
    print(f"     - {os.path.join(args.output_dir, 'train.jsonl')}")
    print(f"     - {os.path.join(args.output_dir, 'val.jsonl')}")
    print(f"     - {os.path.join(args.output_dir, 'test.jsonl')}")
    print(f"     - {os.path.join(args.output_dir, 'dataset.csv')}")
    print(f"     - {os.path.join(args.output_dir, 'label_map.json')}")
    print(f"     - {os.path.join(args.output_dir, 'dataset_stats.json')}")
    print("=" * 72)


if __name__ == "__main__":
    main()
