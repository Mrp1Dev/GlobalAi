/**
 * Raw data types and contracts for the teammate review classifier.
 * Sourced from feedback_module/labels.py, classifier.py, and app.py.
 */

export interface TeammateDetectedLabel {
  label: string;
  confidence: number;
}

export interface TeammateSuggestion {
  label: string;
  kind: "issue" | "strength" | "idea" | "follow_up" | "unsure";
  confidence: number;
  possible: boolean;
  urgent: boolean;
  hi?: string;
  en?: string;
}

export interface TeammateAnalyzeResponse {
  original?: string;
  translation_hi?: string | null;
  detected: TeammateDetectedLabel[];
  severity: "low" | "medium" | "high";
  suggestions?: TeammateSuggestion[];
  needs_human?: boolean;
  fallback?: {
    kind: string;
    hi?: string;
    en?: string;
    translation_hi?: string | null;
  } | null;
  model_mode?: string;
  disclaimer_hi?: string;
  error?: string;
}

export type TeammateProbs = Record<string, number>;

export interface RawClassifierResult {
  probs?: TeammateProbs;
  detected?: TeammateDetectedLabel[];
  severity?: "low" | "medium" | "high" | string;
  needs_human?: boolean;
  fallback?: unknown;
  error?: string;
}
