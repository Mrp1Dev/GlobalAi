/**
 * Adapter module: Translates teammate classifier outputs into the standard
 * Noor Review project contracts (ReviewClassification, ReviewAspect, Sentiment, Severity).
 *
 * Keeps all teammate-specific formats, labels, and heuristics isolated.
 */

import type { Sentiment, Severity, ReviewAspect, ReviewClassification } from "../types/review.ts";
import type { RawClassifierResult, TeammateAnalyzeResponse } from "../types/classifier.ts";
import {
  CLASSIFIER_CONFIDENCE_THRESHOLD,
  ASPECT_TAXONOMY_MAP,
  looksMeaningless,
  heuristicSeverity,
} from "./classifierTaxonomy.ts";

/**
 * Normalizes a raw severity string into a valid project Severity type.
 */
export function normalizeSeverity(severity?: unknown): Severity {
  if (severity === "high") return "high";
  if (severity === "medium") return "medium";
  return "low";
}

/**
 * Adapts raw teammate classifier output (from HTTP API or local inference)
 * into the standardized `ReviewClassification` type.
 *
 * Guaranteed:
 * - Pure function, throws no unhandled exceptions on malformed inputs.
 * - Filters labels below CLASSIFIER_CONFIDENCE_THRESHOLD (0.55).
 * - Preserves genuine confidence scores.
 * - Maps teammate taxonomy to project aspects.
 * - Deterministically derives overall sentiment.
 */
export function adaptTeammateOutputToReviewClassification(
  raw: RawClassifierResult | TeammateAnalyzeResponse | null | undefined,
  reviewText?: string
): ReviewClassification {
  // 1. Safe handling of empty, null, or erroneous responses
  if (!raw || typeof raw !== "object") {
    return { overallSentiment: "neutral", aspects: [] };
  }

  if (raw.error || (reviewText && looksMeaningless(reviewText))) {
    return { overallSentiment: "neutral", aspects: [] };
  }

  // 2. Extract raw detection list
  const rawObj = raw as RawClassifierResult & TeammateAnalyzeResponse;
  let detectedList: Array<{ label: string; confidence: number }> = [];

  if (Array.isArray(rawObj.detected)) {
    detectedList = rawObj.detected
      .filter((d) => d && typeof d.label === "string" && typeof d.confidence === "number")
      .map((d) => ({ label: d.label, confidence: d.confidence }));
  } else if (rawObj.probs && typeof rawObj.probs === "object") {
    detectedList = Object.entries(rawObj.probs)
      .filter(([label, conf]) => typeof label === "string" && typeof conf === "number")
      .map(([label, confidence]) => ({ label, confidence: confidence as number }));
  }

  // 3. Resolve review-level severity
  const reviewSeverity = normalizeSeverity(
    rawObj.severity || (rawObj.probs && reviewText ? heuristicSeverity(reviewText, rawObj.probs) : "low")
  );

  // 4. Map detected labels to ReviewAspects
  const aspectMap = new Map<string, ReviewAspect>();

  for (const item of detectedList) {
    if (item.confidence < CLASSIFIER_CONFIDENCE_THRESHOLD) {
      continue;
    }

    const label = item.label.trim();
    let rawAspect = label;
    let sentiment: Sentiment = "neutral";

    if (label.endsWith(":pos")) {
      rawAspect = label.slice(0, -4);
      sentiment = "positive";
    } else if (label.endsWith(":neg")) {
      rawAspect = label.slice(0, -4);
      sentiment = "negative";
    } else if (label === "would_recommend_or_return") {
      rawAspect = label;
      sentiment = "positive";
    } else if (label === "wish_or_suggestion") {
      rawAspect = label;
      sentiment = "neutral";
    }

    const normalizedAspect = ASPECT_TAXONOMY_MAP[rawAspect] || rawAspect;

    // Severity mapping:
    // Negative aspects inherit review severity ("high" or "medium" or "low").
    // Positive and neutral aspects are non-critical ("low").
    const aspectSeverity: Severity = sentiment === "negative" ? reviewSeverity : "low";

    const genuineConfidence = Math.round(item.confidence * 100) / 100;

    const candidateAspect: ReviewAspect = {
      aspect: normalizedAspect,
      sentiment,
      severity: aspectSeverity,
      confidence: genuineConfidence,
    };

    // If an aspect already exists, retain the one with higher confidence
    const existing = aspectMap.get(normalizedAspect);
    if (!existing || candidateAspect.confidence > existing.confidence) {
      aspectMap.set(normalizedAspect, candidateAspect);
    }
  }

  const aspects = Array.from(aspectMap.values());

  // 5. Derive overall sentiment
  const overallSentiment = deriveOverallSentiment(aspects, reviewSeverity);

  return {
    overallSentiment,
    aspects,
  };
}

/**
 * Derives overall review sentiment based on detected aspects and severity.
 */
export function deriveOverallSentiment(
  aspects: ReviewAspect[],
  reviewSeverity: Severity
): Sentiment {
  if (aspects.length === 0) {
    return "neutral";
  }

  const posAspects = aspects.filter((a) => a.sentiment === "positive");
  const negAspects = aspects.filter((a) => a.sentiment === "negative");

  if (negAspects.length === 0 && posAspects.length > 0) {
    return "positive";
  }

  if (posAspects.length === 0 && negAspects.length > 0) {
    return "negative";
  }

  if (posAspects.length > 0 && negAspects.length > 0) {
    // High-severity negative findings dominate overall perception
    if (reviewSeverity === "high") {
      return "negative";
    }

    const posConfidenceSum = posAspects.reduce((sum, a) => sum + a.confidence, 0);
    const negConfidenceSum = negAspects.reduce((sum, a) => sum + a.confidence, 0);

    if (negConfidenceSum > posConfidenceSum) {
      return "negative";
    }
    if (posConfidenceSum > negConfidenceSum) {
      return "positive";
    }
    return "neutral";
  }

  return "neutral";
}
