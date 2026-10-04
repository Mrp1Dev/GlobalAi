/**
 * Development fallback for language detection in the browser.
 *
 * NOTE: Google ML Kit is native-only (Android/iOS). When running in the
 * standard Vite browser development environment, this lightweight fallback
 * provides deterministic language identification for testing.
 *
 * It does NOT pretend to be ML Kit and will return 'und' (undetermined)
 * when text is ambiguous, short, or unknown.
 */
export async function detectLanguageBrowserFallback(text: string): Promise<string> {
  console.info(
    "[LanguageDetector] Running in browser development mode (native ML Kit is Android/iOS only). Using browser dev fallback."
  );

  const clean = text.trim();
  if (!clean) {
    return "und";
  }

  // 1. Script-based identification
  if (/[\u0600-\u06FF]/.test(clean)) return "ar";
  if (/[\u0900-\u097F]/.test(clean)) return "hi";
  if (/[\u3040-\u309F\u30A0-\u30FF]/.test(clean)) return "ja";
  if (/[\uAC00-\uD7AF\u1100-\u11FF]/.test(clean)) return "ko";
  if (/[\u4E00-\u9FFF]/.test(clean)) return "zh";
  if (/[\u0400-\u04FF]/.test(clean)) return "ru";

  // 2. Word token heuristic for Latin-script visitor languages
  const tokens = clean
    .toLowerCase()
    .replace(/[^\p{L}\s]/gu, " ")
    .split(/\s+/)
    .filter(Boolean);

  if (tokens.length === 0) {
    return "und";
  }

  const tokenSet = new Set(tokens);

  const languageSignatures: Record<string, string[]> = {
    en: [
      "the", "and", "loved", "waited", "coffee", "tour", "good", "great",
      "very", "long", "time", "place", "we", "was", "for", "with", "but",
      "nice", "food", "friendly", "guide", "farm", "visit"
    ],
    fr: [
      "le", "la", "les", "et", "très", "visite", "café", "merci", "bien",
      "nous", "pour", "avec", "un", "une", "dans", "mais", "temps", "attendu"
    ],
    es: [
      "el", "la", "los", "las", "y", "muy", "gracias", "bueno", "visita",
      "café", "con", "por", "para", "pero", "tiempo", "esperamos", "guía"
    ],
    de: [
      "der", "die", "das", "und", "sehr", "danke", "gut", "zeit", "mit",
      "für", "aber", "war", "kaffee", "tour", "gewartet", "schön"
    ],
    it: [
      "il", "la", "di", "e", "molto", "grazie", "buono", "visita", "caffè",
      "con", "per", "ma", "tempo", "aspettato", "bello"
    ],
    pt: [
      "o", "a", "os", "as", "e", "muito", "obrigado", "visita", "café",
      "com", "para", "mas", "tempo", "esperamos", "bom"
    ],
    pl: [
      "bardzo", "dobra", "dobre", "wycieczka", "droga", "ale", "za", "jest", "nie", "w", "z"
    ],
    nl: [
      "het", "de", "een", "en", "van", "maar", "voor", "niet", "heel", "leuk", "mooi"
    ],
  };

  let bestLang = "und";
  let maxScore = 0;
  let secondScore = 0;

  for (const [lang, words] of Object.entries(languageSignatures)) {
    let score = 0;
    for (const word of words) {
      if (tokenSet.has(word)) {
        score++;
      }
    }

    if (score > maxScore) {
      secondScore = maxScore;
      maxScore = score;
      bestLang = lang;
    } else if (score > secondScore) {
      secondScore = score;
    }
  }

  // Require clear match to avoid false certainty
  if (maxScore > 0 && maxScore > secondScore) {
    return bestLang;
  }

  return "und";
}
