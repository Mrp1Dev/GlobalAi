export type DetectionStatus =
  | "detected"
  | "undetermined"
  | "unsupported"
  | "unavailable";

export type DetectionEngine = "mlkit" | "browser-fallback" | "unavailable";

export interface DetectedLanguageResult {
  status: "detected";
  /** BCP-47 language tag (e.g. "en", "fr", "es", "hi") */
  languageCode: string;
  /** NLLB Flores-200 language code (e.g. "eng_Latn", "fra_Latn") */
  nllbCode: string;
  /** Human-readable display name (e.g. "English", "French") */
  languageName: string;
  /**
   * Real confidence score [0.0 - 1.0] if provided by the underlying engine.
   * Undefined if the engine does not provide numeric confidence. Never fabricated.
   */
  confidence?: number;
  /** Underlying detection engine utilized */
  engine: "mlkit" | "browser-fallback";
}

export interface UndeterminedLanguageResult {
  status: "undetermined";
  /** Descriptive reason (e.g. "Language unclear — please ask a person.") */
  message: string;
  /** Raw undetermined code returned by engine, if any (e.g. "und") */
  rawLanguageCode?: string;
  /** Confidence score if provided by engine */
  confidence?: number;
  engine: "mlkit" | "browser-fallback";
}

export interface UnsupportedLanguageResult {
  status: "unsupported";
  /** BCP-47 language tag that was detected */
  languageCode: string;
  /** Human-readable language name if known */
  languageName?: string;
  /** Descriptive message explaining the lack of system support */
  message: string;
  /** Confidence score if provided by engine */
  confidence?: number;
  engine: "mlkit" | "browser-fallback";
}

export interface UnavailableLanguageResult {
  status: "unavailable";
  /** Reason why language detection is unavailable on this platform */
  message: string;
  engine: "unavailable";
}

export type LanguageDetectionResult =
  | DetectedLanguageResult
  | UndeterminedLanguageResult
  | UnsupportedLanguageResult
  | UnavailableLanguageResult;

export interface DetectLanguageOptions {
  /**
   * Allow development fallback heuristic in standard browser environment.
   * Default: true.
   * When false on a non-native platform, detectLanguage returns status: "unavailable".
   */
  allowBrowserFallback?: boolean;
}
