/**
 * Centralized taxonomy, thresholds, and heuristic definitions for the review classifier.
 * Sourced directly from teammate's feedback_module/labels.py, rules.py, and classifier.py.
 */

/**
 * Minimum confidence required to consider a detected label valid.
 * Sourced from teammate's `MID = 0.55` in rules.py.
 */
export const CLASSIFIER_CONFIDENCE_THRESHOLD = 0.55;

/**
 * High-confidence threshold indicating definitive finding.
 * Sourced from teammate's `HIGH = 0.80` in rules.py.
 */
export const CLASSIFIER_HIGH_CONFIDENCE_THRESHOLD = 0.80;

/**
 * The 18 taxonomy labels defined in feedback_module/labels.py.
 * 8 aspects x 2 polarities (:pos, :neg) + 2 unpolarized flags.
 */
export const TEAMMATE_LABELS = [
  "guide_hospitality:pos",
  "guide_hospitality:neg",
  "experience_activity:pos",
  "experience_activity:neg",
  "food_drink:pos",
  "food_drink:neg",
  "learning_authenticity:pos",
  "learning_authenticity:neg",
  "price_value:pos",
  "price_value:neg",
  "timing_waiting:pos",
  "timing_waiting:neg",
  "directions_access:pos",
  "directions_access:neg",
  "facilities_cleanliness:pos",
  "facilities_cleanliness:neg",
  "would_recommend_or_return",
  "wish_or_suggestion",
] as const;

export type TeammateLabel = (typeof TEAMMATE_LABELS)[number];

/**
 * Mapping between teammate's internal aspect identifiers and the standardized
 * project aspect taxonomy.
 */
export const ASPECT_TAXONOMY_MAP: Readonly<Record<string, string>> = {
  timing_waiting: "waiting_time",
  directions_access: "directions",
  guide_hospitality: "hospitality",
  food_drink: "food",
  facilities_cleanliness: "cleanliness",
  experience_activity: "experience",
  price_value: "pricing",
  learning_authenticity: "learning_authenticity",
  would_recommend_or_return: "experience",
  wish_or_suggestion: "activities",
};

/**
 * High-severity cues in English, Hindi (Devanagari), and Roman Hindi.
 * Sourced from teammate's `HIGH_CUES` in classifier.py.
 */
export const HIGH_CUES: readonly string[] = [
  "terrible",
  "worst",
  "awful",
  "horrible",
  "disgusting",
  "rude",
  "never again",
  "waste of",
  "scam",
  "unsafe",
  "filthy",
  "hours",
  "refund",
  "बहुत बुरा",
  "बेकार",
  "गंदा",
  "घटिया",
  "ठगी",
  "कभी नहीं",
  "बकवास",
  "bakwas",
  "bekaar",
  "ganda",
  "ghatiya",
];

/**
 * Identifies if input text carries no analyzable meaning (e.g. empty, garbage, or single word/emoji).
 * Matches teammate's `looks_meaningless` in rules.py.
 */
export function looksMeaningless(text: string): boolean {
  if (!text || typeof text !== "string") return true;
  // Match unicode letters across all scripts (equivalent to Python [^\W\d_])
  const letters = text.match(/\p{L}/gu) || [];
  const words = text.trim().split(/\s+/).filter(Boolean);
  return letters.length < 4 || words.length < 2;
}

/**
 * Heuristic severity assessment matching teammate's `heuristic_severity` in classifier.py.
 */
export function heuristicSeverity(
  text: string,
  probs: Record<string, number>
): "low" | "medium" | "high" {
  const hasNeg = Object.entries(probs).some(
    ([lab, p]) => lab.endsWith(":neg") && p >= CLASSIFIER_CONFIDENCE_THRESHOLD
  );

  if (!hasNeg) {
    return "low";
  }

  const lower = (text || "").toLowerCase();
  const hasHighCue = HIGH_CUES.some((cue) => lower.includes(cue.toLowerCase()));
  return hasHighCue ? "high" : "medium";
}
