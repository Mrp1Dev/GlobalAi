import { Capacitor } from "@capacitor/core";
import { resolveLanguageMapping } from "./languageMapping.ts";
import type {
  DetectLanguageOptions,
  LanguageDetectionResult,
} from "../types/language.ts";
import { detectLanguageBrowserFallback } from "./browserLanguageDetectorFallback.ts";
import { detectLanguageNative } from "./nativeLanguageDetector.ts";

/**
 * Detects the language of a visitor review using Google ML Kit on native mobile devices,
 * or a development fallback when running in a browser.
 *
 * Returned structure strictly captures:
 * - detected language (BCP-47 + NLLB FLORES code + language name)
 * - genuine confidence/uncertainty if available (no fabricated values)
 * - unsupported language (known BCP-47, but unmapped in system)
 * - undetermined language ('und', empty, or ambiguous)
 * - native/browser unavailable state (if fallback is disabled on web or platform unsupported)
 */
export async function detectLanguage(
  text: string,
  options?: DetectLanguageOptions
): Promise<LanguageDetectionResult> {
  const isNative = Capacitor.isNativePlatform();
  const allowFallback = options?.allowBrowserFallback ?? true;
  const trimmed = text.trim();

  // If running in browser and fallback is explicitly disabled
  if (!isNative && !allowFallback) {
    return {
      status: "unavailable",
      message:
        "Native Google ML Kit language identification is unavailable in the browser environment.",
      engine: "unavailable",
    };
  }

  // Handle empty or blank text
  if (!trimmed) {
    return {
      status: "undetermined",
      message: "No text provided to detect language.",
      engine: isNative ? "mlkit" : "browser-fallback",
    };
  }

  let languageCode = "und";
  let confidence: number | undefined;
  const engine = isNative ? "mlkit" : "browser-fallback";

  try {
    if (isNative) {
      const outcome = await detectLanguageNative(trimmed);
      languageCode = outcome.languageCode;
      confidence = outcome.confidence;
    } else {
      languageCode = await detectLanguageBrowserFallback(trimmed);
      // Browser fallback does NOT fabricate confidence
      confidence = undefined;
    }
  } catch (error) {
    const errorMsg =
      error instanceof Error ? error.message : "Unknown detection error";

    if (!isNative) {
      return {
        status: "unavailable",
        message: `Browser detection failed: ${errorMsg}`,
        engine: "unavailable",
      };
    }

    return {
      status: "undetermined",
      message: `Language detection failed: ${errorMsg}`,
      engine: "mlkit",
    };
  }

  // Handle undetermined outcome ('und')
  if (!languageCode || languageCode === "und") {
    return {
      status: "undetermined",
      message: "Language unclear — please ask a person.",
      rawLanguageCode: "und",
      confidence,
      engine,
    };
  }

  // Resolve mapping to NLLB FLORES code and display name
  const mappingResult = resolveLanguageMapping(languageCode);

  if (!mappingResult.supported) {
    return {
      status: "unsupported",
      languageCode,
      message: mappingResult.reason,
      confidence,
      engine,
    };
  }

  return {
    status: "detected",
    languageCode,
    nllbCode: mappingResult.nllbCode,
    languageName: mappingResult.languageName,
    confidence,
    engine,
  };
}