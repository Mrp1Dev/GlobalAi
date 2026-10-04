import { Capacitor } from "@capacitor/core";
import { LanguageIdentification } from "@capacitor-mlkit/language-identification";

export interface NativeIdentificationOutcome {
  languageCode: string;
  confidence?: number;
}

/**
 * Native Google ML Kit language detector for mobile platforms (Android/iOS).
 * Uses @capacitor-mlkit/language-identification on-device.
 *
 * Calls `identifyPossibleLanguages` to obtain genuine confidence scores from ML Kit,
 * with graceful fallback to `identifyLanguage` if needed.
 */
export async function detectLanguageNative(
  text: string
): Promise<NativeIdentificationOutcome> {
  if (!Capacitor.isNativePlatform()) {
    throw new Error(
      "Native ML Kit Language Identification is only supported on Android and iOS platforms."
    );
  }

  try {
    const result = await LanguageIdentification.identifyPossibleLanguages({
      text,
      confidenceThreshold: 0.01,
    });

    if (result.identifiedLanguages && result.identifiedLanguages.length > 0) {
      const top = result.identifiedLanguages[0];
      return {
        languageCode: top.language,
        confidence:
          typeof top.confidence === "number" ? top.confidence : undefined,
      };
    }

    return {
      languageCode: "und",
    };
  } catch (error) {
    console.warn(
      "[MLKit] identifyPossibleLanguages error, falling back to identifyLanguage:",
      error
    );

    const fallbackResult = await LanguageIdentification.identifyLanguage({
      text,
      confidenceThreshold: 0.5,
    });

    return {
      languageCode: fallbackResult.language || "und",
      confidence: undefined,
    };
  }
}
