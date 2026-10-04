/**
 * Deterministic Recommendation Engine for Noor Review.
 *
 * Implements tourism-specific rule-based suggestions mapped to identified
 * review aspects and sentiments.
 *
 * Constraints:
 * - Deterministic rules only (No LLM, no generative model, no cloud API).
 * - Single-review scope (Does not aggregate or claim to represent all visitors).
 * - Phrased as actionable suggestions ("Consider ..."), not rigid instructions.
 * - Requires sufficient classifier confidence (>= 0.55).
 * - Ignores neutral or unsupported aspects.
 */

import type { Recommendation, ReviewClassification, Severity, Sentiment } from "../types/review.ts";
import { CLASSIFIER_CONFIDENCE_THRESHOLD, ASPECT_TAXONOMY_MAP } from "../ml/classifierTaxonomy.ts";

export interface RecommendationOptions {
  /**
   * Minimum confidence required to trigger a recommendation.
   * Defaults to CLASSIFIER_CONFIDENCE_THRESHOLD (0.55).
   */
  minConfidence?: number;
}

/**
 * Standardized rule catalog mapping (aspect + sentiment) to suggested action.
 * Phrased as helpful operational suggestions.
 */
export const RECOMMENDATION_RULES: Readonly<Record<string, Partial<Record<Sentiment, string>>>> = {
  waiting_time: {
    negative: "Consider improving arrival and start-time communication.",
    positive: "Consider maintaining the current punctual tour schedule.",
  },
  directions: {
    negative: "Consider providing clearer directions and landmark guidance before visitor arrival.",
    positive: "Consider continuing to share the current clear route guidance with new visitors.",
  },
  hospitality: {
    negative: "Consider greeting visitors personally at the start and setting aside time for questions.",
    positive: "Consider maintaining the current warm hospitality approach.",
  },
  food: {
    negative: "Consider reviewing the food offering and clarifying dietary expectations before the visit.",
    positive: "Consider keeping popular food and drink offerings as regular visit highlights.",
  },
  cleanliness: {
    negative: "Consider reviewing cleaning and preparation of seating and restrooms before visitor arrival.",
    positive: "Consider maintaining the current high standard of cleanliness and facilities.",
  },
  experience: {
    negative: "Consider reviewing the tour duration and pacing to identify any tiring or dull segments.",
    positive: "Consider keeping and strengthening the parts of the experience visitors value most.",
  },
  pricing: {
    negative: "Consider making pricing details and inclusions clearer before the visit.",
    positive: "Consider continuing to clearly communicate pricing and included tour benefits upfront.",
  },
  communication: {
    negative: "Consider providing clearer visitor information and reminders before arrival.",
    positive: "Consider continuing proactive and helpful visitor communication.",
  },
  learning_authenticity: {
    negative: "Consider explaining daily farm practices in simpler terms to enhance authentic learning.",
    positive: "Consider continuing to showcase authentic, hands-on farm activities as key highlights.",
  },
  activities: {
    negative: "Consider adjusting activity difficulty and pacing to ensure all visitors can comfortably participate.",
    positive: "Consider featuring the popular visitor activities prominently in tour descriptions.",
  },
};

/**
 * Priority weighting for ordering recommendations.
 * High priority issues must be presented first to Noor.
 */
const SEVERITY_WEIGHTS: Record<Severity, number> = {
  high: 3,
  medium: 2,
  low: 1,
};

/**
 * Generates deterministic recommendations from a completed review classification.
 *
 * Only produces recommendations when:
 * 1. The aspect is recognized in the ruleset (or maps to a recognized aspect).
 * 2. Sentiment is meaningful (positive or negative; neutral observations do not warrant action).
 * 3. Confidence meets or exceeds the threshold (>= 0.55).
 */
export function generateRecommendations(
  classification: ReviewClassification | null | undefined,
  options?: RecommendationOptions
): Recommendation[] {
  if (!classification || !Array.isArray(classification.aspects) || classification.aspects.length === 0) {
    return [];
  }

  const minConfidence = options?.minConfidence ?? CLASSIFIER_CONFIDENCE_THRESHOLD;
  const recommendations: Recommendation[] = [];
  const seenAspects = new Set<string>();

  for (const aspectItem of classification.aspects) {
    // 1. Confidence check
    if (typeof aspectItem.confidence !== "number" || aspectItem.confidence < minConfidence) {
      continue;
    }

    // 2. Meaningful sentiment check (ignore neutral)
    if (aspectItem.sentiment !== "positive" && aspectItem.sentiment !== "negative") {
      continue;
    }

    // 3. Aspect normalization & rule lookup
    const rawAspect = (aspectItem.aspect || "").trim();
    const normalizedAspect = ASPECT_TAXONOMY_MAP[rawAspect] || rawAspect;
    const aspectRules = RECOMMENDATION_RULES[normalizedAspect];

    if (!aspectRules) {
      continue;
    }

    const actionText = aspectRules[aspectItem.sentiment];
    if (!actionText) {
      continue;
    }

    // Avoid duplicate recommendations for the same aspect
    if (seenAspects.has(normalizedAspect)) {
      continue;
    }
    seenAspects.add(normalizedAspect);

    // 4. Priority assignment:
    // Negative aspects inherit their review/aspect severity (high, medium, low).
    // Positive aspects represent positive reinforcement (low priority).
    const priority: Severity = aspectItem.sentiment === "negative" ? (aspectItem.severity || "medium") : "low";

    recommendations.push({
      aspect: normalizedAspect,
      priority,
      action: actionText,
    });
  }

  // 5. Deterministic sorting:
  // Sort high priority -> medium priority -> low priority.
  // Secondary sort by aspect name for consistent deterministic ordering.
  recommendations.sort((a, b) => {
    const weightA = SEVERITY_WEIGHTS[a.priority] || 0;
    const weightB = SEVERITY_WEIGHTS[b.priority] || 0;
    if (weightB !== weightA) {
      return weightB - weightA;
    }
    return a.aspect.localeCompare(b.aspect);
  });

  return recommendations;
}
