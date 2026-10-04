/**
 * Deterministic Hackathon Demo Scenarios for Noor Review.
 *
 * This dataset provides 5 realistic, multilingual visitor review scenarios
 * specifically crafted for live presentation and testing.
 *
 * VALUE PROPOSITION DEMONSTRATED:
 * Visitor writes naturally in their own language
 *         ↓
 * Noor doesn't need to understand that language
 *         ↓
 * System translates locally
 *         ↓
 * System identifies what the visitor is talking about
 *         ↓
 * System flags sentiment/severity
 *         ↓
 * System suggests a concrete improvement
 *         ↓
 * Noor makes the final decision
 *
 * Note: These scenarios are strictly for demonstration and testing verification.
 * They are not hardcoded into production decision logic.
 */

export interface DemoScenario {
  id: string;
  title: string;
  category:
    | "positive"
    | "negative_waiting"
    | "mixed"
    | "directions_issue"
    | "positive_hospitality";
  visitorLanguage: string;
  originalReview: string;
  expectedDetectedLanguage: {
    bcp47: string;
    nllbCode: string;
    name: string;
  };
  expectedTranslation: string;
  expectedClassification: {
    overallSentiment: "positive" | "negative" | "neutral";
    primaryAspects: Array<{
      aspect: string;
      sentiment: "positive" | "negative" | "neutral";
      severity: "low" | "medium" | "high";
    }>;
  };
  expectedRecommendations: string[];
  businessContextForNoor: string;
}

export const DEMO_SCENARIOS: readonly DemoScenario[] = [
  {
    id: "demo-1-positive",
    title: "Scenario 1: Positive Tour & Cleanliness Review (German)",
    category: "positive",
    visitorLanguage: "German",
    originalReview:
      "Die Tour über die Kaffeefarm war wunderbar organisiert und alles war sehr sauber.",
    expectedDetectedLanguage: {
      bcp47: "de",
      nllbCode: "deu_Latn",
      name: "German",
    },
    expectedTranslation:
      "La visite de la ferme de café était merveilleusement organisée et tout était très propre.",
    expectedClassification: {
      overallSentiment: "positive",
      primaryAspects: [
        { aspect: "cleanliness", sentiment: "positive", severity: "low" },
        { aspect: "experience", sentiment: "positive", severity: "low" },
      ],
    },
    expectedRecommendations: [
      "Consider maintaining the current high standard of cleanliness and facilities.",
      "Consider keeping and strengthening the parts of the experience visitors value most.",
    ],
    businessContextForNoor:
      "Noor does not need to speak German to understand that this visitor was delighted by the hygiene standards and tour pacing. The recommendation reassures Noor that the farm's pre-tour cleanup routine is effective.",
  },
  {
    id: "demo-2-negative-waiting",
    title: "Scenario 2: Critical Waiting-Time Delay (English)",
    category: "negative_waiting",
    visitorLanguage: "English",
    originalReview:
      "We had to wait over 45 minutes past the start time before the tour even began. It was very disorganized.",
    expectedDetectedLanguage: {
      bcp47: "en",
      nllbCode: "eng_Latn",
      name: "English",
    },
    expectedTranslation:
      "Nous avons dû attendre plus de 45 minutes après l'heure de début avant que la visite ne commence. C'était très désorganisé.",
    expectedClassification: {
      overallSentiment: "negative",
      primaryAspects: [
        { aspect: "waiting_time", sentiment: "negative", severity: "high" },
      ],
    },
    expectedRecommendations: [
      "Consider improving arrival and start-time communication.",
    ],
    businessContextForNoor:
      "A long delay was flagged as High Priority. Rather than triggering an automated refund or apology message, the system suggests an operational fix: send a reminder message with the exact gate opening time so visitors do not wait unnecessarily in the heat.",
  },
  {
    id: "demo-3-mixed",
    title: "Scenario 3: Mixed Value & Coffee Quality (Spanish)",
    category: "mixed",
    visitorLanguage: "Spanish",
    originalReview:
      "Me encantó el paseo por la finca y el café estaba delicioso, pero la entrada nos pareció demasiado cara para lo que incluye.",
    expectedDetectedLanguage: {
      bcp47: "es",
      nllbCode: "spa_Latn",
      name: "Spanish",
    },
    expectedTranslation:
      "J'ai adoré la promenade dans la ferme et le café était délicieux, mais le billet semblait trop cher pour ce qu'il comprend.",
    expectedClassification: {
      overallSentiment: "negative", // Mixed / Negative: pricing complaint dampens overall experience
      primaryAspects: [
        { aspect: "pricing", sentiment: "negative", severity: "medium" },
        { aspect: "food", sentiment: "positive", severity: "low" },
        { aspect: "experience", sentiment: "positive", severity: "low" },
      ],
    },
    expectedRecommendations: [
      "Consider making pricing details and inclusions clearer before the visit.",
      "Consider keeping and strengthening the parts of the experience visitors value most.",
      "Consider keeping popular food and drink offerings as regular visit highlights.",
    ],
    businessContextForNoor:
      "The visitor praised the coffee and scenery, but questioned ticket pricing. The recommendation advises Noor to highlight tour inclusions (such as the complimentary tasting) on the booking sheet upfront so visitors feel the value.",
  },
  {
    id: "demo-4-directions",
    title: "Scenario 4: Directions & Turnoff Problem (French)",
    category: "directions_issue",
    visitorLanguage: "French",
    originalReview:
      "La ferme est très difficile à trouver. Il n'y a aucun panneau sur la route principale et le chemin est mal indiqué.",
    expectedDetectedLanguage: {
      bcp47: "fr",
      nllbCode: "fra_Latn",
      name: "French",
    },
    expectedTranslation:
      "La ferme est très difficile à trouver. Il n'y a aucun panneau sur la route principale et le chemin est mal indiqué.", // Bypasses translation as source matches target
    expectedClassification: {
      overallSentiment: "negative",
      primaryAspects: [
        { aspect: "directions", sentiment: "negative", severity: "medium" },
      ],
    },
    expectedRecommendations: [
      "Consider providing clearer directions and landmark guidance before visitor arrival.",
    ],
    businessContextForNoor:
      "The system detects French and skips redundant translation compute. It highlights that the visitor got lost. Noor decides to place a painted wooden signpost at the fork in the road.",
  },
  {
    id: "demo-5-hospitality-coffee",
    title: "Scenario 5: Warm Hospitality & Fresh Coffee Tasting (Hindi)",
    category: "positive_hospitality",
    visitorLanguage: "Hindi",
    originalReview:
      "मेजबान बहुत ही मिलनसार थे और ताज़ी कॉफी का स्वाद वाकई लाजवाब था।",
    expectedDetectedLanguage: {
      bcp47: "hi",
      nllbCode: "hin_Deva",
      name: "Hindi",
    },
    expectedTranslation:
      "L'hôte était très accueillant et le goût du café frais était vraiment merveilleux.",
    expectedClassification: {
      overallSentiment: "positive",
      primaryAspects: [
        { aspect: "hospitality", sentiment: "positive", severity: "low" },
        { aspect: "food", sentiment: "positive", severity: "low" },
      ],
    },
    expectedRecommendations: [
      "Consider keeping popular food and drink offerings as regular visit highlights.",
      "Consider maintaining the current warm hospitality approach.",
    ],
    businessContextForNoor:
      "Noor cannot read Devanagari script, yet effortlessly discovers that the guest loved the warm personal welcome and coffee flavor. This reassures Noor that personal storytelling remains their greatest competitive asset.",
  },
];
