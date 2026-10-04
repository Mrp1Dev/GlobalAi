/**
 * Centralized Language Mapping & Configuration Module for Noor Review
 *
 * This module is the single source of truth for:
 * 1. Noor's target language configuration.
 * 2. Mapping Google ML Kit BCP-47 language codes to NLLB-200 FLORES codes.
 * 3. Human-readable language display names.
 *
 * Rules:
 * - Never duplicate language mappings across components or workers.
 * - Never silently fall back to English for unsupported languages.
 * - Explicitly return an unsupported status for unmapped languages.
 */

/**
 * Noor's target language for visitor review translations.
 *
 * NOTE: Currently configured to 'fra_Latn' (French) as a TEMPORARY development target.
 * Do not assume French is Noor's real language; update this single variable when Noor's
 * actual local language is established.
 */
export const NOOR_TARGET_LANGUAGE = "fra_Latn";

export interface SupportedLanguageInfo {
  /** Normalized BCP-47 code (e.g. "en", "sw", "hi") */
  bcp47: string;
  /** NLLB-200 FLORES language code (e.g. "eng_Latn", "swh_Latn", "hin_Deva") */
  nllbCode: string;
  /** Human-readable English display name */
  name: string;
}

/**
 * Verified high-confidence language mappings for the MVP.
 * Each NLLB code conforms to the FLORES-200 language code standard.
 *
 * Required MVP languages:
 * - English    -> eng_Latn
 * - French     -> fra_Latn
 * - Hindi      -> hin_Deva
 * - Spanish    -> spa_Latn
 * - German     -> deu_Latn
 * - Portuguese -> por_Latn
 * - Swahili    -> swh_Latn
 */
export const SUPPORTED_LANGUAGES: Readonly<Record<string, SupportedLanguageInfo>> = {
  en: { bcp47: "en", nllbCode: "eng_Latn", name: "English" },
  fr: { bcp47: "fr", nllbCode: "fra_Latn", name: "French" },
  hi: { bcp47: "hi", nllbCode: "hin_Deva", name: "Hindi" },
  es: { bcp47: "es", nllbCode: "spa_Latn", name: "Spanish" },
  de: { bcp47: "de", nllbCode: "deu_Latn", name: "German" },
  pt: { bcp47: "pt", nllbCode: "por_Latn", name: "Portuguese" },
  sw: { bcp47: "sw", nllbCode: "swh_Latn", name: "Swahili" },
  it: { bcp47: "it", nllbCode: "ita_Latn", name: "Italian" },
  ar: { bcp47: "ar", nllbCode: "ara_Arab", name: "Arabic" },
};

export type LanguageMappingResult =
  | {
      supported: true;
      bcp47: string;
      nllbCode: string;
      languageName: string;
    }
  | {
      supported: false;
      bcp47: string;
      reason: string;
    };

/**
 * Resolves an ML Kit BCP-47 language tag into an NLLB FLORES code.
 *
 * Handles regional tags gracefully (e.g. "en-US" -> "en", "es-MX" -> "es").
 * If the language is not supported, explicitly returns supported: false
 * with a clear explanation, WITHOUT silently falling back to English.
 */
export function resolveLanguageMapping(bcp47Code: string): LanguageMappingResult {
  const trimmed = (bcp47Code || "").trim().toLowerCase();
  if (!trimmed || trimmed === "und") {
    return {
      supported: false,
      bcp47: trimmed || "und",
      reason: "Language code is undetermined or empty.",
    };
  }

  // 1. Direct match (e.g. "en", "sw", "hi")
  const directMatch = SUPPORTED_LANGUAGES[trimmed];
  if (directMatch) {
    return {
      supported: true,
      bcp47: directMatch.bcp47,
      nllbCode: directMatch.nllbCode,
      languageName: directMatch.name,
    };
  }

  // 2. Base language prefix match (e.g. "en-US" -> "en", "fr-CA" -> "fr", "pt-BR" -> "pt")
  const baseCode = trimmed.split(/[-_]/)[0];
  if (baseCode && SUPPORTED_LANGUAGES[baseCode]) {
    const baseMatch = SUPPORTED_LANGUAGES[baseCode];
    return {
      supported: true,
      bcp47: baseMatch.bcp47,
      nllbCode: baseMatch.nllbCode,
      languageName: baseMatch.name,
    };
  }

  // 3. Explicit unsupported state (never silently fallback to English)
  return {
    supported: false,
    bcp47: bcp47Code,
    reason: `Language '${bcp47Code}' is detected but not currently supported for translation.`,
  };
}

/**
 * Maps a BCP-47 code to SupportedLanguageInfo or null if unsupported.
 */
export function mapBcp47ToNllb(bcp47Code: string): SupportedLanguageInfo | null {
  const result = resolveLanguageMapping(bcp47Code);
  if (result.supported) {
    return {
      bcp47: result.bcp47,
      nllbCode: result.nllbCode,
      name: result.languageName,
    };
  }
  return null;
}

/**
 * Returns whether a given BCP-47 language tag is supported by our system.
 */
export function isLanguageSupported(bcp47Code: string): boolean {
  return resolveLanguageMapping(bcp47Code).supported;
}

/**
 * Returns a list of all currently supported languages.
 */
export function getSupportedLanguages(): readonly SupportedLanguageInfo[] {
  return Object.values(SUPPORTED_LANGUAGES);
}
