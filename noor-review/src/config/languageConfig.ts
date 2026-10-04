/**
 * Re-export centralized language mapping and target configuration.
 *
 * Single source of truth is located in `src/ml/languageMapping.ts`.
 * Do not duplicate language mapping tables here.
 */
export {
  NOOR_TARGET_LANGUAGE,
  SUPPORTED_LANGUAGES,
  resolveLanguageMapping,
  mapBcp47ToNllb,
  isLanguageSupported,
  getSupportedLanguages,
} from "../ml/languageMapping.ts";
export type {
  SupportedLanguageInfo,
  LanguageMappingResult,
} from "../ml/languageMapping.ts";
