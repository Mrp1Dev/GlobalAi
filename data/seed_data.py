"""
Curated multilingual seed dataset for Farm Tourism Intent Classification.
Provides native-quality samples across 9 intents and 8 languages:
  - English (en)
  - Spanish (es)
  - French (fr)
  - German (de)
  - Italian (it)
  - Hindi (hi)
  - Portuguese (pt)
  - Dutch (nl)
"""

from typing import List, Dict

CURATED_SEED_SAMPLES: List[Dict[str, str]] = [
    # =========================================================================
    # INTENT: price_tour
    # =========================================================================
    # English
    {"intent": "price_tour", "lang": "en", "text": "How much does a guided farm tour cost?"},
    {"intent": "price_tour", "lang": "en", "text": "What is the entrance fee for the farm visit?"},
    {"intent": "price_tour", "lang": "en", "text": "Are there ticket discounts for children and students?"},
    {"intent": "price_tour", "lang": "en", "text": "How much per person for the agricultural tour?"},
    {"intent": "price_tour", "lang": "en", "text": "Can you tell me the price of the weekend walking tour?"},
    {"intent": "price_tour", "lang": "en", "text": "What does a family tour package cost?"},
    {"intent": "price_tour", "lang": "en", "text": "Is the farm tour free or do we need tickets?"},
    {"intent": "price_tour", "lang": "en", "text": "How much are you charging for groups of five?"},
    # Spanish
    {"intent": "price_tour", "lang": "es", "text": "Hola, ¿cuánto cuesta el tour guiado por la granja?"},
    {"intent": "price_tour", "lang": "es", "text": "¿Cuál es el precio de la entrada por persona?"},
    {"intent": "price_tour", "lang": "es", "text": "¿Hay tarifas reducidas para niños o estudiantes?"},
    {"intent": "price_tour", "lang": "es", "text": "¿Cuánto sale la visita a los campos de cultivo?"},
    {"intent": "price_tour", "lang": "es", "text": "¿Tienen paquetes familiares y cuál es su costo?"},
    {"intent": "price_tour", "lang": "es", "text": "¿Cuánto cobran por el recorrido completo de la granja?"},
    {"intent": "price_tour", "lang": "es", "text": "¿Hay que pagar entrada para entrar a la finca?"},
    # French
    {"intent": "price_tour", "lang": "fr", "text": "Bonjour, quel est le tarif pour visiter la ferme?"},
    {"intent": "price_tour", "lang": "fr", "text": "Combien coûte une visite guidée pour deux adultes?"},
    {"intent": "price_tour", "lang": "fr", "text": "Quel est le prix du billet d'entrée?"},
    {"intent": "price_tour", "lang": "fr", "text": "Y a-t-il un tarif de groupe ou un tarif enfant?"},
    {"intent": "price_tour", "lang": "fr", "text": "Combien coûte la découverte de l'exploitation agricole?"},
    {"intent": "price_tour", "lang": "fr", "text": "Pouvez-vous m'indiquer le prix de la promenade guidée?"},
    # German
    {"intent": "price_tour", "lang": "de", "text": "Wie viel kostet eine Führung auf dem Bauernhof?"},
    {"intent": "price_tour", "lang": "de", "text": "Was kostet der Eintritt pro Person für die Farm?"},
    {"intent": "price_tour", "lang": "de", "text": "Gibt es Ermäßigungen für Familien oder Kinder?"},
    {"intent": "price_tour", "lang": "de", "text": "Wie hoch sind die Ticketpreise für den Rundgang?"},
    {"intent": "price_tour", "lang": "de", "text": "Was verlangen Sie für eine Führung durch die Plantage?"},
    {"intent": "price_tour", "lang": "de", "text": "Ist die Besichtigung kostenpflichtig?"},
    # Italian
    {"intent": "price_tour", "lang": "it", "text": "Quanto costa il tour guidato della fattoria?"},
    {"intent": "price_tour", "lang": "it", "text": "Qual è il prezzo del biglietto d'ingresso a persona?"},
    {"intent": "price_tour", "lang": "it", "text": "Ci sono sconti per famiglie con bambini?"},
    {"intent": "price_tour", "lang": "it", "text": "Quanto si paga per visitare i campi e la stalla?"},
    {"intent": "price_tour", "lang": "it", "text": "Quali sono le tariffe per il tour pomeridiano?"},
    # Hindi
    {"intent": "price_tour", "lang": "hi", "text": "फार्म टूर की टिकट कितने की है?"},
    {"intent": "price_tour", "lang": "hi", "text": "खेत में घूमने का प्रति व्यक्ति कितना खर्चा आएगा?"},
    {"intent": "price_tour", "lang": "hi", "text": "क्या बच्चों के लिए टिकट में कोई छूट है?"},
    {"intent": "price_tour", "lang": "hi", "text": "टूर का किराया कितना है?"},
    {"intent": "price_tour", "lang": "hi", "text": "पूरे खेत के गाइडेड टूर की फीस क्या है?"},
    # Portuguese
    {"intent": "price_tour", "lang": "pt", "text": "Quanto custa o passeio guiado pela fazenda?"},
    {"intent": "price_tour", "lang": "pt", "text": "Qual é o valor da entrada por pessoa?"},
    {"intent": "price_tour", "lang": "pt", "text": "Tem desconto para crianças na visita à fazenda?"},
    {"intent": "price_tour", "lang": "pt", "text": "Quanto vocês cobram pelo roteiro turístico rural?"},
    # Dutch
    {"intent": "price_tour", "lang": "nl", "text": "Hoeveel kost een rondleiding over de boerderij?"},
    {"intent": "price_tour", "lang": "nl", "text": "Wat is de toegangsprijs per persoon?"},
    {"intent": "price_tour", "lang": "nl", "text": "Zijn er groepskortingen voor het boerderijbezoek?"},
    {"intent": "price_tour", "lang": "nl", "text": "Wat zijn de tarieven voor een bezoek met het gezin?"},

    # =========================================================================
    # INTENT: price_produce
    # =========================================================================
    # English
    {"intent": "price_produce", "lang": "en", "text": "How much do fresh organic mangoes cost per kilo?"},
    {"intent": "price_produce", "lang": "en", "text": "What is the price of fresh raw cow milk?"},
    {"intent": "price_produce", "lang": "en", "text": "Do you sell organic honey and how much is a jar?"},
    {"intent": "price_produce", "lang": "en", "text": "Can we buy seasonal vegetables directly from the farm?"},
    {"intent": "price_produce", "lang": "en", "text": "What are your prices for farm-fresh eggs?"},
    {"intent": "price_produce", "lang": "en", "text": "How much for a basket of freshly harvested fruits?"},
    {"intent": "price_produce", "lang": "en", "text": "Do you sell home-made spices and pickles here?"},
    # Spanish
    {"intent": "price_produce", "lang": "es", "text": "¿A qué precio venden la miel orgánica fresca?"},
    {"intent": "price_produce", "lang": "es", "text": "¿Cuánto cuestan las frutas de temporada por kilo?"},
    {"intent": "price_produce", "lang": "es", "text": "¿Venden leche fresca de vaca y cuál es su costo?"},
    {"intent": "price_produce", "lang": "es", "text": "¿Podemos comprar verduras ecológicas directamente del huerto?"},
    {"intent": "price_produce", "lang": "es", "text": "¿Cuánto sale una canasta de huevos de campo?"},
    {"intent": "price_produce", "lang": "es", "text": "¿Tienen mermeladas caseras para comprar?"},
    # French
    {"intent": "price_produce", "lang": "fr", "text": "Combien coûte le pot de miel bio de votre ferme?"},
    {"intent": "price_produce", "lang": "fr", "text": "Quel est le prix au kilo pour les fruits frais?"},
    {"intent": "price_produce", "lang": "fr", "text": "Vendez-vous du lait frais de vache et à quel tarif?"},
    {"intent": "price_produce", "lang": "fr", "text": "Peut-on acheter des légumes cultivés sans pesticides sur place?"},
    {"intent": "price_produce", "lang": "fr", "text": "Combien vendez-vous les œufs fermiers?"},
    # German
    {"intent": "price_produce", "lang": "de", "text": "Was kostet ein Glas frischer Bio-Honig bei Ihnen?"},
    {"intent": "price_produce", "lang": "de", "text": "Wie viel kostet frisches Obst pro Kilo direkt vom Baum?"},
    {"intent": "price_produce", "lang": "de", "text": "Verkaufen Sie frische Rohmilch und Hofprodukte?"},
    {"intent": "price_produce", "lang": "de", "text": "Kann man feldfrisches Gemüse im Hofladen erwerben?"},
    {"intent": "price_produce", "lang": "de", "text": "Wie teuer sind die frischen Eier von freilaufenden Hühnern?"},
    # Italian
    {"intent": "price_produce", "lang": "it", "text": "Quanto costa il miele biologico prodotto in fattoria?"},
    {"intent": "price_produce", "lang": "it", "text": "Quali sono i prezzi per le verdure e la frutta di stagione?"},
    {"intent": "price_produce", "lang": "it", "text": "Vendete latte fresco appena munto?"},
    {"intent": "price_produce", "lang": "it", "text": "Possiamo acquistare prodotti agricoli a chilometro zero?"},
    {"intent": "price_produce", "lang": "it", "text": "Quanto costa una confezione di uova fresche?"},
    # Hindi
    {"intent": "price_produce", "lang": "hi", "text": "खेत का ताजा शहद कितने रुपये किलो है?"},
    {"intent": "price_produce", "lang": "hi", "text": "क्या हम यहाँ से ताजी जैविक सब्जियां खरीद सकते हैं?"},
    {"intent": "price_produce", "lang": "hi", "text": "ताजे गाय के दूध का क्या भाव है?"},
    {"intent": "price_produce", "lang": "hi", "text": "मौसमी फलों की कीमत क्या रखी गई है?"},
    {"intent": "price_produce", "lang": "hi", "text": "देसी अंडे और घर का बना अचार कितने में मिलेगा?"},
    # Portuguese
    {"intent": "price_produce", "lang": "pt", "text": "Quanto custa o pote de mel orgânico da fazenda?"},
    {"intent": "price_produce", "lang": "pt", "text": "Qual é o preço do quilo das frutas colhidas na hora?"},
    {"intent": "price_produce", "lang": "pt", "text": "Vocês vendem leite fresco de vaca e ovos caipiras?"},
    {"intent": "price_produce", "lang": "pt", "text": "Posso comprar hortaliças e legumes diretamente aqui?"},
    # Dutch
    {"intent": "price_produce", "lang": "nl", "text": "Wat kost een pot verse biologische honing?"},
    {"intent": "price_produce", "lang": "nl", "text": "Hoeveel kost het seizoensfruit per kilo?"},
    {"intent": "price_produce", "lang": "nl", "text": "Verkopen jullie verse melk en eieren van de boerderij?"},
    {"intent": "price_produce", "lang": "nl", "text": "Kunnen we verse groenten direct van het veld kopen?"},

    # =========================================================================
    # INTENT: visitation_hours
    # =========================================================================
    # English
    {"intent": "visitation_hours", "lang": "en", "text": "What time does the farm open and close?"},
    {"intent": "visitation_hours", "lang": "en", "text": "Can we visit the farm tomorrow morning at 10 AM?"},
    {"intent": "visitation_hours", "lang": "en", "text": "What are your public visiting hours on weekends?"},
    {"intent": "visitation_hours", "lang": "en", "text": "Are you open on Sundays and public holidays?"},
    {"intent": "visitation_hours", "lang": "en", "text": "When is the best time of day to take a tour?"},
    {"intent": "visitation_hours", "lang": "en", "text": "Until what time can visitors enter in the evening?"},
    # Spanish
    {"intent": "visitation_hours", "lang": "es", "text": "¿A qué hora abre y cierra la granja?"},
    {"intent": "visitation_hours", "lang": "es", "text": "¿Cuáles son los horarios de visita los fines de semana?"},
    {"intent": "visitation_hours", "lang": "es", "text": "¿Está abierta la finca los domingos y días festivos?"},
    {"intent": "visitation_hours", "lang": "es", "text": "¿Podemos llegar a las diez de la mañana para el recorrido?"},
    {"intent": "visitation_hours", "lang": "es", "text": "¿Cuál es la última hora de ingreso por la tarde?"},
    # French
    {"intent": "visitation_hours", "lang": "fr", "text": "À quelle heure la ferme ouvre-t-elle le matin?"},
    {"intent": "visitation_hours", "lang": "fr", "text": "Quels sont les horaires d'ouverture le week-end?"},
    {"intent": "visitation_hours", "lang": "fr", "text": "La ferme est-elle ouverte au public les jours fériés?"},
    {"intent": "visitation_hours", "lang": "fr", "text": "Jusqu'à quelle heure peut-on faire la visite le soir?"},
    # German
    {"intent": "visitation_hours", "lang": "de", "text": "Wie sind die Öffnungszeiten des Bauernhofs?"},
    {"intent": "visitation_hours", "lang": "de", "text": "Wann öffnet die Farm am Wochenende für Besucher?"},
    {"intent": "visitation_hours", "lang": "de", "text": "Haben Sie auch an Feiertagen und sonntags geöffnet?"},
    {"intent": "visitation_hours", "lang": "de", "text": "Bis wie viel Uhr darf man abends auf dem Hof bleiben?"},
    # Italian
    {"intent": "visitation_hours", "lang": "it", "text": "Quali sono gli orari di apertura e chiusura della fattoria?"},
    {"intent": "visitation_hours", "lang": "it", "text": "A che ora iniziano le visite guidate la mattina?"},
    {"intent": "visitation_hours", "lang": "it", "text": "Siete aperti la domenica e nei giorni festivi?"},
    {"intent": "visitation_hours", "lang": "it", "text": "Fino a che ora possiamo entrare nel pomeriggio?"},
    # Hindi
    {"intent": "visitation_hours", "lang": "hi", "text": "खेत पर्यटकों के लिए कितने बजे खुलता है?"},
    {"intent": "visitation_hours", "lang": "hi", "text": "क्या रविवार को फार्म खुला रहता है?"},
    {"intent": "visitation_hours", "lang": "hi", "text": "शाम को खेत किस समय बंद होता है?"},
    {"intent": "visitation_hours", "lang": "hi", "text": "आने का सबसे अच्छा समय क्या है?"},
    # Portuguese
    {"intent": "visitation_hours", "lang": "pt", "text": "Quais são os horários de funcionamento da fazenda?"},
    {"intent": "visitation_hours", "lang": "pt", "text": "A fazenda abre aos sábados e domingos?"},
    {"intent": "visitation_hours", "lang": "pt", "text": "A que horas abre para visitas pela manhã?"},
    # Dutch
    {"intent": "visitation_hours", "lang": "nl", "text": "Wat zijn de openingstijden van de boerderij?"},
    {"intent": "visitation_hours", "lang": "nl", "text": "Is de boerderij in het weekend open voor bezoekers?"},
    {"intent": "visitation_hours", "lang": "nl", "text": "Tot hoe laat kunnen we 's avonds langskomen?"},

    # =========================================================================
    # INTENT: farm_location_directions
    # =========================================================================
    # English
    {"intent": "farm_location_directions", "lang": "en", "text": "Where is the farm located and how do we get there?"},
    {"intent": "farm_location_directions", "lang": "en", "text": "Can we reach the farm by public bus or train?"},
    {"intent": "farm_location_directions", "lang": "en", "text": "Is there car parking available at the farm?"},
    {"intent": "farm_location_directions", "lang": "en", "text": "Can you provide the GPS coordinates or Google Maps location?"},
    {"intent": "farm_location_directions", "lang": "en", "text": "Which highway exit should we take to reach Noor's farm?"},
    {"intent": "farm_location_directions", "lang": "en", "text": "Is the access road paved or suitable for small cars?"},
    # Spanish
    {"intent": "farm_location_directions", "lang": "es", "text": "¿Dónde está ubicada la granja y cómo llegamos?"},
    {"intent": "farm_location_directions", "lang": "es", "text": "¿Hay estacionamiento disponible para vehículos?"},
    {"intent": "farm_location_directions", "lang": "es", "text": "¿Se puede llegar en autobús o transporte público?"},
    {"intent": "farm_location_directions", "lang": "es", "text": "¿Podrían compartir su ubicación en el mapa o coordenadas?"},
    {"intent": "farm_location_directions", "lang": "es", "text": "¿El camino de acceso está pavimentado para autos pequeños?"},
    # French
    {"intent": "farm_location_directions", "lang": "fr", "text": "Où se trouve exactement la ferme et comment y accéder?"},
    {"intent": "farm_location_directions", "lang": "fr", "text": "Y a-t-il un parking gratuit sur place pour les voitures?"},
    {"intent": "farm_location_directions", "lang": "fr", "text": "Peut-on venir en transports en commun ou en train?"},
    {"intent": "farm_location_directions", "lang": "fr", "text": "Pouvez-vous nous envoyer l'adresse exacte et l'itinéraire?"},
    # German
    {"intent": "farm_location_directions", "lang": "de", "text": "Wo genau liegt der Bauernhof und wie kommt man dorthin?"},
    {"intent": "farm_location_directions", "lang": "de", "text": "Gibt es vor Ort Parkplätze für Besucher?"},
    {"intent": "farm_location_directions", "lang": "de", "text": "Kann man die Farm mit öffentlichen Verkehrsmitteln erreichen?"},
    {"intent": "farm_location_directions", "lang": "de", "text": "Können Sie uns eine Wegbeschreibung oder Koordinaten schicken?"},
    # Italian
    {"intent": "farm_location_directions", "lang": "it", "text": "Dove si trova l'agriturismo e come ci arriviamo in auto?"},
    {"intent": "farm_location_directions", "lang": "it", "text": "C'è un parcheggio disponibile per i visitatori?"},
    {"intent": "farm_location_directions", "lang": "it", "text": "È possibile arrivare con i mezzi pubblici dalla città?"},
    {"intent": "farm_location_directions", "lang": "it", "text": "Qual è l'indirizzo esatto per il navigatore satellitare?"},
    # Hindi
    {"intent": "farm_location_directions", "lang": "hi", "text": "खेत का सही पता क्या है और हम वहाँ कैसे पहुँच सकते हैं?"},
    {"intent": "farm_location_directions", "lang": "hi", "text": "क्या फार्म पर गाड़ियां पार्क करने की जगह है?"},
    {"intent": "farm_location_directions", "lang": "hi", "text": "क्या यहाँ बस या ट्रेन से पहुँचा जा सकता है?"},
    {"intent": "farm_location_directions", "lang": "hi", "text": "कृपया लोकेशन या रास्ता बताएं।"},
    # Portuguese
    {"intent": "farm_location_directions", "lang": "pt", "text": "Onde fica a fazenda e como faço para chegar de carro?"},
    {"intent": "farm_location_directions", "lang": "pt", "text": "Tem estacionamento no local para os turistas?"},
    {"intent": "farm_location_directions", "lang": "pt", "text": "É possível chegar de ônibus ou transporte coletivo?"},
    # Dutch
    {"intent": "farm_location_directions", "lang": "nl", "text": "Waar ligt de boerderij en hoe komen we er met de auto?"},
    {"intent": "farm_location_directions", "lang": "nl", "text": "Is er parkeergelegenheid aanwezig op het terrein?"},
    {"intent": "farm_location_directions", "lang": "nl", "text": "Kunnen we er komen met het openbaar vervoer?"},

    # =========================================================================
    # INTENT: activities_available
    # =========================================================================
    # English
    {"intent": "activities_available", "lang": "en", "text": "What activities and experiences can visitors do at the farm?"},
    {"intent": "activities_available", "lang": "en", "text": "Can our kids try milking cows or feeding farm animals?"},
    {"intent": "activities_available", "lang": "en", "text": "Do you offer fruit picking or harvesting activities?"},
    {"intent": "activities_available", "lang": "en", "text": "Are there tractor rides or farming workshops available?"},
    {"intent": "activities_available", "lang": "en", "text": "Can we take a guided walk through the crop fields?"},
    {"intent": "activities_available", "lang": "en", "text": "Do you have pottery making or organic farming demonstrations?"},
    # Spanish
    {"intent": "activities_available", "lang": "es", "text": "¿Qué actividades interactivas se pueden hacer en la granja?"},
    {"intent": "activities_available", "lang": "es", "text": "¿Los niños pueden ordeñar vacas o alimentar a los animales?"},
    {"intent": "activities_available", "lang": "es", "text": "¿Ofrecen recolección de frutas o paseos en tractor?"},
    {"intent": "activities_available", "lang": "es", "text": "¿Hay talleres de agricultura ecológica o alfarería?"},
    {"intent": "activities_available", "lang": "es", "text": "¿Qué experiencias agrícolas tienen para turistas?"},
    # French
    {"intent": "activities_available", "lang": "fr", "text": "Quelles activités proposez-vous pour les visiteurs à la ferme?"},
    {"intent": "activities_available", "lang": "fr", "text": "Les enfants peuvent-ils nourrir les animaux ou traire les vaches?"},
    {"intent": "activities_available", "lang": "fr", "text": "Proposez-vous la cueillette de fruits ou des balades en tracteur?"},
    {"intent": "activities_available", "lang": "fr", "text": "Y a-t-il des ateliers pratiques sur l'agriculture biologique?"},
    # German
    {"intent": "activities_available", "lang": "de", "text": "Welche Aktivitäten und Erlebnisse werden auf dem Hof angeboten?"},
    {"intent": "activities_available", "lang": "de", "text": "Dürfen Kinder Kühe melken oder Hoftiere füttern?"},
    {"intent": "activities_available", "lang": "de", "text": "Kann man Obst selbst pflücken oder eine Traktorfahrt machen?"},
    {"intent": "activities_available", "lang": "de", "text": "Gibt es Workshops zu ökologischer Landwirtschaft?"},
    # Italian
    {"intent": "activities_available", "lang": "it", "text": "Quali attività si possono svolgere durante la visita in fattoria?"},
    {"intent": "activities_available", "lang": "it", "text": "I bambini possono dare da mangiare agli animali o mungere le mucche?"},
    {"intent": "activities_available", "lang": "it", "text": "Offrite la raccolta della frutta o giri sul trattore?"},
    {"intent": "activities_available", "lang": "it", "text": "Ci sono laboratori didattici per famiglie?"},
    # Hindi
    {"intent": "activities_available", "lang": "hi", "text": "खेत पर पर्यटक क्या-क्या मजेदार काम और अनुभव कर सकते हैं?"},
    {"intent": "activities_available", "lang": "hi", "text": "क्या बच्चे गाय का दूध दुहना या जानवरों को चारा खिलाना सीख सकते हैं?"},
    {"intent": "activities_available", "lang": "hi", "text": "क्या यहाँ फल तोड़ने और ट्रैक्टर की सवारी की सुविधा है?"},
    {"intent": "activities_available", "lang": "hi", "text": "खेत में कौन-कौन सी गतिविधियां उपलब्ध हैं?"},
    # Portuguese
    {"intent": "activities_available", "lang": "pt", "text": "Quais atividades os turistas podem fazer na propriedade?"},
    {"intent": "activities_available", "lang": "pt", "text": "As crianças podem alimentar os animais ou tirar leite da vaca?"},
    {"intent": "activities_available", "lang": "pt", "text": "Tem colheita de frutas no pomar ou passeio de trator?"},
    # Dutch
    {"intent": "activities_available", "lang": "nl", "text": "Welke activiteiten zijn er te doen op de boerderij?"},
    {"intent": "activities_available", "lang": "nl", "text": "Mogen kinderen dieren voeren of koeien melken?"},
    {"intent": "activities_available", "lang": "nl", "text": "Bieden jullie zelf fruit plukken of tractorritten aan?"},

    # =========================================================================
    # INTENT: amenities_food
    # =========================================================================
    # English
    {"intent": "amenities_food", "lang": "en", "text": "Do you serve traditional meals, lunch, or tea at the farm?"},
    {"intent": "amenities_food", "lang": "en", "text": "Are there clean restrooms and washrooms for visitors?"},
    {"intent": "amenities_food", "lang": "en", "text": "Is safe drinking water available on the premises?"},
    {"intent": "amenities_food", "lang": "en", "text": "Do you offer vegetarian or vegan food options?"},
    {"intent": "amenities_food", "lang": "en", "text": "Are there shaded rest areas or places to sit down?"},
    {"intent": "amenities_food", "lang": "en", "text": "Can we bring our own picnic food and snacks?"},
    # Spanish
    {"intent": "amenities_food", "lang": "es", "text": "¿Ofrecen comida típica, almuerzo campestre o té a los visitantes?"},
    {"intent": "amenities_food", "lang": "es", "text": "¿Tienen baños limpios y servicios sanitarios disponibles?"},
    {"intent": "amenities_food", "lang": "es", "text": "¿Hay agua potable para beber en la finca?"},
    {"intent": "amenities_food", "lang": "es", "text": "¿Tienen opciones de comida vegetariana?"},
    {"intent": "amenities_food", "lang": "es", "text": "¿Hay zonas con sombra para descansar?"},
    # French
    {"intent": "amenities_food", "lang": "fr", "text": "Servez-vous des repas traditionnels ou le déjeuner à la ferme?"},
    {"intent": "amenities_food", "lang": "fr", "text": "Y a-t-il des toilettes propres pour les visiteurs?"},
    {"intent": "amenities_food", "lang": "fr", "text": "Y a-t-il de l'eau potable fraîche disponible sur place?"},
    {"intent": "amenities_food", "lang": "fr", "text": "Proposez-vous des plats végétariens faits maison?"},
    # German
    {"intent": "amenities_food", "lang": "de", "text": "Gibt es auf dem Hof Verpflegung, Mittagessen oder Tee?"},
    {"intent": "amenities_food", "lang": "de", "text": "Sind saubere Toiletten und Waschräume für Gäste vorhanden?"},
    {"intent": "amenities_food", "lang": "de", "text": "Gibt es trinkbares Leitungswasser auf der Farm?"},
    {"intent": "amenities_food", "lang": "de", "text": "Gibt es schattige Sitzgelegenheiten zum Ausruhen?"},
    # Italian
    {"intent": "amenities_food", "lang": "it", "text": "È possibile pranzare in agriturismo con piatti tipici locali?"},
    {"intent": "amenities_food", "lang": "it", "text": "Ci sono servizi igienici puliti per i visitatori?"},
    {"intent": "amenities_food", "lang": "it", "text": "C'è disponibilità di acqua potabile e bibite fresche?"},
    {"intent": "amenities_food", "lang": "it", "text": "Avete opzioni vegetariane per il pranzo?"},
    # Hindi
    {"intent": "amenities_food", "lang": "hi", "text": "क्या खेत पर देशी खाना, दोपहर का भोजन या चाय-नाश्ता मिलता है?"},
    {"intent": "amenities_food", "lang": "hi", "text": "क्या यहाँ पर्यटकों के लिए साफ शौचालय और बाथरूम की सुविधा है?"},
    {"intent": "amenities_food", "lang": "hi", "text": "क्या पीने का साफ फिल्टर पानी उपलब्ध है?"},
    {"intent": "amenities_food", "lang": "hi", "text": "क्या विश्राम के लिए छायादार बैठने की जगह है?"},
    # Portuguese
    {"intent": "amenities_food", "lang": "pt", "text": "Vocês servem almoço típico da roça ou café colonial?"},
    {"intent": "amenities_food", "lang": "pt", "text": "Tem banheiros limpos para os turistas usarem?"},
    {"intent": "amenities_food", "lang": "pt", "text": "Tem água potável e área coberta para descanso?"},
    # Dutch
    {"intent": "amenities_food", "lang": "nl", "text": "Serveren jullie traditionele lunch of thee op de boerderij?"},
    {"intent": "amenities_food", "lang": "nl", "text": "Zijn er schone toiletten en wasruimtes voor gasten?"},
    {"intent": "amenities_food", "lang": "nl", "text": "Is er drinkwater en een schaduwrijke plek om te zitten?"},

    # =========================================================================
    # INTENT: pet_policy
    # =========================================================================
    # English
    {"intent": "pet_policy", "lang": "en", "text": "Are dogs and domestic pets allowed on the farm?"},
    {"intent": "pet_policy", "lang": "en", "text": "Can I bring my pet dog along during the farm visit?"},
    {"intent": "pet_policy", "lang": "en", "text": "What is your policy regarding pets and animals?"},
    {"intent": "pet_policy", "lang": "en", "text": "Do dogs need to be kept on a leash at all times?"},
    {"intent": "pet_policy", "lang": "en", "text": "Are guide dogs permitted inside the farm buildings?"},
    # Spanish
    {"intent": "pet_policy", "lang": "es", "text": "¿Se permiten perros y mascotas en la granja?"},
    {"intent": "pet_policy", "lang": "es", "text": "¿Puedo llevar a mi perro con correa durante la visita?"},
    {"intent": "pet_policy", "lang": "es", "text": "¿Cuál es su política sobre mascotas domésticas?"},
    {"intent": "pet_policy", "lang": "es", "text": "¿Es un lugar apto para mascotas (pet friendly)?"},
    # French
    {"intent": "pet_policy", "lang": "fr", "text": "Les chiens et les animaux de compagnie sont-ils acceptés?"},
    {"intent": "pet_policy", "lang": "fr", "text": "Puis-je venir avec mon chien tenu en laisse?"},
    {"intent": "pet_policy", "lang": "fr", "text": "Quelle est votre politique concernant les animaux domestiques?"},
    # German
    {"intent": "pet_policy", "lang": "de", "text": "Sind Hunde und Haustiere auf dem Bauernhof erlaubt?"},
    {"intent": "pet_policy", "lang": "de", "text": "Darf ich meinen Hund an der Leine mitbringen?"},
    {"intent": "pet_policy", "lang": "de", "text": "Wie lautet Ihre Regelung bezüglich Haustieren?"},
    # Italian
    {"intent": "pet_policy", "lang": "it", "text": "I cani sono ammessi durante la visita alla fattoria?"},
    {"intent": "pet_policy", "lang": "it", "text": "Posso portare il mio animale domestico al guinzaglio?"},
    {"intent": "pet_policy", "lang": "it", "text": "La vostra struttura accetta animali domestici?"},
    # Hindi
    {"intent": "pet_policy", "lang": "hi", "text": "क्या खेत में पालतू कुत्ते या जानवर लाने की अनुमति है?"},
    {"intent": "pet_policy", "lang": "hi", "text": "क्या हम अपने पालतू कुत्ते को साथ ला सकते हैं?"},
    {"intent": "pet_policy", "lang": "hi", "text": "पालतू जानवरों के लिए खेत के क्या नियम हैं?"},
    # Portuguese
    {"intent": "pet_policy", "lang": "pt", "text": "Animais de estimação e cachorros são permitidos na fazenda?"},
    {"intent": "pet_policy", "lang": "pt", "text": "Posso levar meu cachorro com coleira para o passeio?"},
    # Dutch
    {"intent": "pet_policy", "lang": "nl", "text": "Zijn honden en huisdieren toegestaan op het terrein?"},
    {"intent": "pet_policy", "lang": "nl", "text": "Mag ik mijn aangelijnde hond meenemen naar de boerderij?"},

    # =========================================================================
    # INTENT: booking_reservation
    # =========================================================================
    # English
    {"intent": "booking_reservation", "lang": "en", "text": "Do we need to book our farm tour in advance?"},
    {"intent": "booking_reservation", "lang": "en", "text": "Can we just walk in without a prior reservation?"},
    {"intent": "booking_reservation", "lang": "en", "text": "How do I make a reservation for a group of 10 people?"},
    {"intent": "booking_reservation", "lang": "en", "text": "Is booking required for weekend visits?"},
    {"intent": "booking_reservation", "lang": "en", "text": "What is the cancellation policy for tour bookings?"},
    # Spanish
    {"intent": "booking_reservation", "lang": "es", "text": "¿Es necesario reservar la visita con antelación?"},
    {"intent": "booking_reservation", "lang": "es", "text": "¿Podemos llegar directamente sin previa reserva?"},
    {"intent": "booking_reservation", "lang": "es", "text": "¿Cómo puedo hacer una reserva para un grupo grande?"},
    {"intent": "booking_reservation", "lang": "es", "text": "¿Se requiere reservar para venir el sábado?"},
    # French
    {"intent": "booking_reservation", "lang": "fr", "text": "Faut-il réserver la visite guidée à l'avance?"},
    {"intent": "booking_reservation", "lang": "fr", "text": "Peut-on venir directement sans réservation préalable?"},
    {"intent": "booking_reservation", "lang": "fr", "text": "Comment réserver un créneau pour une famille?"},
    # German
    {"intent": "booking_reservation", "lang": "de", "text": "Muss man die Führung im Voraus buchen?"},
    {"intent": "booking_reservation", "lang": "de", "text": "Kann man auch spontan ohne Reservierung vorbeikommen?"},
    {"intent": "booking_reservation", "lang": "de", "text": "Wie kann ich einen Termin für eine Reisegruppe reservieren?"},
    # Italian
    {"intent": "booking_reservation", "lang": "it", "text": "È necessaria la prenotazione anticipata per la visita?"},
    {"intent": "booking_reservation", "lang": "it", "text": "Possiamo venire direttamente senza aver prenotato?"},
    {"intent": "booking_reservation", "lang": "it", "text": "Come si effettua una prenotazione per domenica?"},
    # Hindi
    {"intent": "booking_reservation", "lang": "hi", "text": "क्या खेत आने से पहले बुकिंग कराना जरूरी है?"},
    {"intent": "booking_reservation", "lang": "hi", "text": "क्या हम बिना पहले से बताए सीधे आ सकते हैं?"},
    {"intent": "booking_reservation", "lang": "hi", "text": "१० लोगों के ग्रुप के लिए बुकिंग कैसे करें?"},
    # Portuguese
    {"intent": "booking_reservation", "lang": "pt", "text": "É preciso fazer reserva com antecedência para visitar?"},
    {"intent": "booking_reservation", "lang": "pt", "text": "Podemos ir sem agendar horário antes?"},
    # Dutch
    {"intent": "booking_reservation", "lang": "nl", "text": "Moeten we van tevoren reserveren voor de rondleiding?"},
    {"intent": "booking_reservation", "lang": "nl", "text": "Kunnen we gewoon spontaan binnenlopen zonder afspraak?"},

    # =========================================================================
    # INTENT: tour_duration_difficulty
    # =========================================================================
    # English
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "How long does the guided coffee farm tour take?"},
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "Is the walking trail up the slope steep or physically demanding?"},
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "What is the typical duration of the walking visit?"},
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "Is this farm tour suitable for young children or elderly visitors?"},
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "How much walking is involved on the hillside paths?"},
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "Do we need sturdy hiking shoes for the coffee plantation walk?"},
    {"intent": "tour_duration_difficulty", "lang": "en", "text": "Are tours held at scheduled times or can we start anytime?"},
    # Spanish
    {"intent": "tour_duration_difficulty", "lang": "es", "text": "¿Cuánto tiempo dura el recorrido guiado por la finca de café?"},
    {"intent": "tour_duration_difficulty", "lang": "es", "text": "¿El sendero por la ladera es muy empinado o exigente?"},
    {"intent": "tour_duration_difficulty", "lang": "es", "text": "¿Cuál es la duración estimada de la visita a pie?"},
    {"intent": "tour_duration_difficulty", "lang": "es", "text": "¿La caminata por la plantación es apta para niños pequeños?"},
    {"intent": "tour_duration_difficulty", "lang": "es", "text": "¿Se requieren zapatos de montaña para recorrer los caminos de la colina?"},
    {"intent": "tour_duration_difficulty", "lang": "es", "text": "¿A qué hora comienzan las visitas guiadas?"},
    # French
    {"intent": "tour_duration_difficulty", "lang": "fr", "text": "Combien de temps dure la visite guidée de la plantation de café?"},
    {"intent": "tour_duration_difficulty", "lang": "fr", "text": "Le sentier sur la pente est-il raide ou difficile pour marcher?"},
    {"intent": "tour_duration_difficulty", "lang": "fr", "text": "Quelle est la durée moyenne de la promenade dans les champs?"},
    {"intent": "tour_duration_difficulty", "lang": "fr", "text": "La visite est-elle accessible aux enfants ou aux personnes âgées?"},
    {"intent": "tour_duration_difficulty", "lang": "fr", "text": "Faut-il prévoir des chaussures de marche pour les sentiers en pente?"},
    # German
    {"intent": "tour_duration_difficulty", "lang": "de", "text": "Wie lange dauert die geführte Tour durch die Kaffeeplantage?"},
    {"intent": "tour_duration_difficulty", "lang": "de", "text": "Ist der Weg am Hang steil oder körperlich anstrengend?"},
    {"intent": "tour_duration_difficulty", "lang": "de", "text": "Wie viel Zeit sollten wir für den gesamten Rundgang einplanen?"},
    {"intent": "tour_duration_difficulty", "lang": "de", "text": "Ist der Rundgang auch für kleinere Kinder oder Senioren geeignet?"},
    {"intent": "tour_duration_difficulty", "lang": "de", "text": "Benötigt man feste Wanderschuhe für die Pfade am Berghang?"},
    # Italian
    {"intent": "tour_duration_difficulty", "lang": "it", "text": "Quanto tempo dura la visita guidata alla piantagione di caffè?"},
    {"intent": "tour_duration_difficulty", "lang": "it", "text": "Il sentiero lungo il pendio è ripido o impegnativo da percorrere?"},
    {"intent": "tour_duration_difficulty", "lang": "it", "text": "Qual è la durata media del tour a piedi?"},
    {"intent": "tour_duration_difficulty", "lang": "it", "text": "La passeggiata è adatta a bambini piccoli o persone anziane?"},
    {"intent": "tour_duration_difficulty", "lang": "it", "text": "Servono scarpe da trekking per camminare tra i filari in collina?"},
    # Hindi
    {"intent": "tour_duration_difficulty", "lang": "hi", "text": "कॉफ़ी फार्म के गाइडेड टूर में कुल कितना समय लगता है?"},
    {"intent": "tour_duration_difficulty", "lang": "hi", "text": "क्या खेत की ढलान पर चलने का रास्ता बहुत कठिन या चढ़ाई वाला है?"},
    {"intent": "tour_duration_difficulty", "lang": "hi", "text": "खेत का पैदल भ्रमण कितने घंटे का होता है?"},
    {"intent": "tour_duration_difficulty", "lang": "hi", "text": "क्या यह टूर बच्चों और बुजुर्गों के लिए आसान है?"},
    {"intent": "tour_duration_difficulty", "lang": "hi", "text": "क्या पहाड़ी रास्ते पर घूमने के लिए खास जूतों की जरूरत है?"},
    # Portuguese
    {"intent": "tour_duration_difficulty", "lang": "pt", "text": "Quanto tempo dura o passeio guiado pela fazenda de café?"},
    {"intent": "tour_duration_difficulty", "lang": "pt", "text": "A trilha pela encosta é íngreme ou fisicamente difícil?"},
    {"intent": "tour_duration_difficulty", "lang": "pt", "text": "Qual é a duração total da caminhada pela plantação?"},
    {"intent": "tour_duration_difficulty", "lang": "pt", "text": "O percurso é recomendado para crianças ou idosos?"},
    # Dutch
    {"intent": "tour_duration_difficulty", "lang": "nl", "text": "Hoe lang duurt de rondleiding over de koffieplantage?"},
    {"intent": "tour_duration_difficulty", "lang": "nl", "text": "Is het wandelpad op de heuvelrug steil of zwaar?"},
    {"intent": "tour_duration_difficulty", "lang": "nl", "text": "Hoeveel tijd moeten we uittrekken voor het complete bezoek?"},
    {"intent": "tour_duration_difficulty", "lang": "nl", "text": "Is de wandeling goed begaanbaar voor kinderen en senioren?"},

    # =========================================================================
    # INTENT: out_of_scope (Negative / Tier 3 Fallback class)
    # =========================================================================
    # English
    {"intent": "out_of_scope", "lang": "en", "text": "What is the weather going to be like in Mumbai next week?"},
    {"intent": "out_of_scope", "lang": "en", "text": "Do you know who won the cricket match yesterday?"},
    {"intent": "out_of_scope", "lang": "en", "text": "Can you recommend a good hotel near the airport?"},
    {"intent": "out_of_scope", "lang": "en", "text": "Hello, nice to meet you! How are you doing today?"},
    {"intent": "out_of_scope", "lang": "en", "text": "Where can I exchange euros for Indian rupees?"},
    {"intent": "out_of_scope", "lang": "en", "text": "Can you help me fix my phone's camera setting?"},
    # Spanish
    {"intent": "out_of_scope", "lang": "es", "text": "¿Cómo estará el clima en la ciudad mañana?"},
    {"intent": "out_of_scope", "lang": "es", "text": "¡Hola! Mucho gusto, ¿cómo estás hoy?"},
    {"intent": "out_of_scope", "lang": "es", "text": "¿Dónde queda la farmacia más cercana en el pueblo?"},
    {"intent": "out_of_scope", "lang": "es", "text": "¿Quién ganó el partido de fútbol anoche?"},
    # French
    {"intent": "out_of_scope", "lang": "fr", "text": "Quel temps fera-t-il demain dans la région?"},
    {"intent": "out_of_scope", "lang": "fr", "text": "Bonjour! Enchanté de faire votre connaissance."},
    {"intent": "out_of_scope", "lang": "fr", "text": "Pouvez-vous me conseiller un bon restaurant en ville?"},
    # German
    {"intent": "out_of_scope", "lang": "de", "text": "Wie wird das Wetter morgen in der Umgebung?"},
    {"intent": "out_of_scope", "lang": "de", "text": "Hallo! Schön Sie kennenzulernen, wie geht es Ihnen?"},
    {"intent": "out_of_scope", "lang": "de", "text": "Wo finde ich den nächsten Geldautomaten?"},
    # Italian
    {"intent": "out_of_scope", "lang": "it", "text": "Che tempo farà domani da queste parti?"},
    {"intent": "out_of_scope", "lang": "it", "text": "Piacere di conoscerti! Come stai oggi?"},
    {"intent": "out_of_scope", "lang": "it", "text": "Dove posso trovare una farmacia nelle vicinanze?"},
    # Hindi
    {"intent": "out_of_scope", "lang": "hi", "text": "नमस्ते! आप कैसी हैं और आपका दिन कैसा चल रहा है?"},
    {"intent": "out_of_scope", "lang": "hi", "text": "कल यहाँ का मौसम कैसा रहने की संभावना है?"},
    {"intent": "out_of_scope", "lang": "hi", "text": "नजदीकी एटीएम या बैंक कहाँ पर है?"},
    # Portuguese
    {"intent": "out_of_scope", "lang": "pt", "text": "Como estará o tempo amanhã por aqui?"},
    {"intent": "out_of_scope", "lang": "pt", "text": "Olá! Prazer em conhecer, tudo bem com você?"},
    # Dutch
    {"intent": "out_of_scope", "lang": "nl", "text": "Wat voor weer wordt het morgen in deze streek?"},
    {"intent": "out_of_scope", "lang": "nl", "text": "Hallo, leuk om kennis te maken! Hoe gaat het met u?"}
]
