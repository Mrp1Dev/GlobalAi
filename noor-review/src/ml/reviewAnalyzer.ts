/**
 * Review Analyzer - Complete One-Review Analysis Pipeline.
 *
 * Orchestrates the full lifecycle of a visitor review:
 * Visitor Review
 *     ↓
 * Language Identification (ML Kit / Browser fallback)
 *     ↓
 * Centralized Language Mapping
 *     ↓
 * NLLB-200 Translation
 *     ↓
 * Review Classifier (On-device Web Worker)
 *     ↓
 * Deterministic Recommendation Rules
 *     ↓
 * ReviewAnalysis Contract
 *
 * Fail-safe rules:
 * - Never silently falls back to English for unsupported or undetermined languages.
 * - Never invents translations or confidence scores.
 * - Does not automatically trigger any external business actions.
 * - All results are informational suggestions for Noor's decision-making.
 */

import { detectLanguage } from "./languageDetector.ts";
import { NOOR_TARGET_LANGUAGE } from "./languageMapping.ts";
import { translateText } from "./translator.ts";
import { classifyReview } from "./classifier.ts";
import { generateRecommendations } from "../rules/recommendationRules.ts";
import type { LanguageDetectionResult } from "../types/language.ts";
import type { ReviewAnalysis, ReviewClassification, Recommendation } from "../types/review.ts";

/**
 * Explicit pipeline lifecycle states.
 */
export type AnalyzerState =
  | "idle"
  | "detecting"
  | "translating"
  | "classifying"
  | "recommending"
  | "completed"
  | "error";

/**
 * High-level pipeline outcome status.
 */
export type AnalysisStatus =
  | "success"
  | "empty"
  | "undetermined_language"
  | "unsupported_language"
  | "translation_error"
  | "classification_error"
  | "error";

export interface ReviewAnalyzerOptions {
  /** If true, uses deterministic mock inference for offline testing */
  forceMock?: boolean;
  /** Custom target NLLB language code (defaults to NOOR_TARGET_LANGUAGE) */
  targetLanguage?: string;
  /** Minimum aspect confidence for recommendations (defaults to 0.55) */
  minConfidence?: number;
  /** State transition callback */
  onStateChange?: (state: AnalyzerState, message?: string) => void;
  /** If true, uses the development Python service instead of on-device worker */
  useHttpService?: boolean;
}

export interface ReviewAnalysisTimings {
  detectionMs: number;
  translationMs: number;
  classificationMs: number;
  recommendationMs: number;
  totalMs: number;
}

export interface ReviewAnalysisResult {
  status: AnalysisStatus;
  analysis?: ReviewAnalysis;
  detection?: LanguageDetectionResult;
  message?: string;
  error?: string;
  timings?: ReviewAnalysisTimings;
}

/**
 * Analyzes a single visitor review through the complete pipeline.
 */
export async function analyzeReview(
  text: string,
  options?: ReviewAnalyzerOptions
): Promise<ReviewAnalysisResult> {
  const pipelineStart = performance.now();
  let detectionMs = 0;
  let translationMs = 0;
  let classificationMs = 0;
  let recommendationMs = 0;

  const notify = (state: AnalyzerState, msg?: string) => {
    options?.onStateChange?.(state, msg);
  };

  const trimmed = (text || "").trim();

  // 1. Handle empty input
  if (!trimmed) {
    notify("completed");
    return {
      status: "empty",
      message: "No review text provided. Please enter a visitor review.",
      timings: {
        detectionMs: 0,
        translationMs: 0,
        classificationMs: 0,
        recommendationMs: 0,
        totalMs: Math.round(performance.now() - pipelineStart),
      },
    };
  }

  // 2. Step 1: Language Detection & Mapping
  notify("detecting", "Detecting language...");
  let detection: LanguageDetectionResult;
  const tDetectStart = performance.now();

  try {
    detection = await detectLanguage(trimmed);
    detectionMs = Math.round(performance.now() - tDetectStart);
  } catch (err) {
    detectionMs = Math.round(performance.now() - tDetectStart);
    const errorMsg = err instanceof Error ? err.message : "Language detection failed";
    notify("error", errorMsg);
    return {
      status: "error",
      error: `Language detection failed: ${errorMsg}`,
      timings: {
        detectionMs,
        translationMs: 0,
        classificationMs: 0,
        recommendationMs: 0,
        totalMs: Math.round(performance.now() - pipelineStart),
      },
    };
  }

  // Handle undetermined language (Fail safely - never assume English)
  if (detection.status === "undetermined") {
    notify("completed");
    return {
      status: "undetermined_language",
      detection,
      message: "Language unclear — please ask a person.",
      timings: {
        detectionMs,
        translationMs: 0,
        classificationMs: 0,
        recommendationMs: 0,
        totalMs: Math.round(performance.now() - pipelineStart),
      },
    };
  }

  // Handle unsupported language (Fail safely - never silently fall back to English)
  if (detection.status === "unsupported" || detection.status === "unavailable") {
    notify("completed");
    const langCode = detection.status === "unsupported" ? detection.languageCode : undefined;
    const msg = detection.message || (langCode ? `Language '${langCode}' is not currently supported for translation.` : "Language detection unavailable.");
    return {
      status: "unsupported_language",
      detection,
      message: msg,
      timings: {
        detectionMs,
        translationMs: 0,
        classificationMs: 0,
        recommendationMs: 0,
        totalMs: Math.round(performance.now() - pipelineStart),
      },
    };
  }

  const targetLang = options?.targetLanguage || NOOR_TARGET_LANGUAGE;
  const sourceLang = detection.nllbCode;

  // 3. Step 2: NLLB Translation
  let translatedText = "";
  const tTranslateStart = performance.now();

  if (sourceLang === targetLang) {
    // If the review is already in Noor's language, no translation needed
    translatedText = trimmed;
    translationMs = 0;
  } else {
    notify("translating", `Translating from ${detection.languageName} into Noor's language...`);
    try {
      translatedText = await translateText(trimmed, sourceLang, targetLang, {
        forceMock: options?.forceMock,
        onStatus: (st) => {
          if (st === "loading") {
            notify("translating", "Loading NLLB-200 translation model...");
          } else if (st === "ready" || st === "translating") {
            notify("translating", `Translating from ${detection.languageName} into Noor's language...`);
          }
        },
      });
      translationMs = Math.round(performance.now() - tTranslateStart);
    } catch (err) {
      translationMs = Math.round(performance.now() - tTranslateStart);
      const errorMsg = err instanceof Error ? err.message : "Translation error occurred";
      notify("error", errorMsg);
      return {
        status: "translation_error",
        detection,
        error: `Translation error: ${errorMsg}`,
        timings: {
          detectionMs,
          translationMs,
          classificationMs: 0,
          recommendationMs: 0,
          totalMs: Math.round(performance.now() - pipelineStart),
        },
      };
    }
  }

  // 4. Step 3: Review Classification (On-Device Classifier)
  notify("classifying", "Analyzing review aspects and sentiment...");
  let classification: ReviewClassification;
  const tClassifyStart = performance.now();

  try {
    classification = await classifyReview(trimmed, {
      forceMock: options?.forceMock,
      useHttpService: options?.useHttpService,
      onStatus: (st) => {
        if (st === "loading") {
          notify("classifying", "Loading on-device multilingual-e5 classifier...");
        } else if (st === "ready" || st === "classifying") {
          notify("classifying", "Analyzing review with on-device classifier...");
        }
      },
    });
    classificationMs = Math.round(performance.now() - tClassifyStart);
  } catch (err) {
    classificationMs = Math.round(performance.now() - tClassifyStart);
    const errorMsg = err instanceof Error ? err.message : "Classification error occurred";
    notify("error", errorMsg);
    return {
      status: "classification_error",
      detection,
      error: `Classification error: ${errorMsg}`,
      timings: {
        detectionMs,
        translationMs,
        classificationMs,
        recommendationMs: 0,
        totalMs: Math.round(performance.now() - pipelineStart),
      },
    };
  }

  // 5. Step 4: Deterministic Recommendation Rules
  notify("recommending", "Generating operational recommendations...");
  let recommendations: Recommendation[] = [];
  const tRecStart = performance.now();

  try {
    recommendations = generateRecommendations(classification, {
      minConfidence: options?.minConfidence,
    });
    recommendationMs = Math.round(performance.now() - tRecStart);
  } catch (err) {
    recommendationMs = Math.round(performance.now() - tRecStart);
    const errorMsg = err instanceof Error ? err.message : "Recommendation error occurred";
    notify("error", errorMsg);
    return {
      status: "error",
      error: `Recommendation engine error: ${errorMsg}`,
      timings: {
        detectionMs,
        translationMs,
        classificationMs,
        recommendationMs,
        totalMs: Math.round(performance.now() - pipelineStart),
      },
    };
  }

  // 6. Assemble complete ReviewAnalysis contract
  const analysis: ReviewAnalysis = {
    originalText: trimmed,
    sourceLanguage: detection.languageName,
    translatedText,
    classification,
    recommendations,
  };

  const totalMs = Math.round(performance.now() - pipelineStart);

  console.info(
    `[NoorReview Timings] Detection: ${detectionMs}ms | Translation: ${translationMs}ms | Classification: ${classificationMs}ms | Rules: ${recommendationMs}ms | Total: ${totalMs}ms`
  );

  notify("completed", "Analysis complete.");

  return {
    status: "success",
    analysis,
    detection,
    timings: {
      detectionMs,
      translationMs,
      classificationMs,
      recommendationMs,
      totalMs,
    },
  };
}
