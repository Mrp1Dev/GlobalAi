import test from "node:test";
import assert from "node:assert/strict";

import {
  NOOR_TARGET_LANGUAGE,
  resolveLanguageMapping,
  mapBcp47ToNllb,
  isLanguageSupported,
  getSupportedLanguages,
} from "../src/ml/languageMapping.ts";

test("Noor target language is defined and marked as temporary development target", () => {
  assert.equal(NOOR_TARGET_LANGUAGE, "fra_Latn");
});

test("Supported MVP languages are mapped to exact NLLB FLORES-200 codes", () => {
  const expectedMappings = {
    en: "eng_Latn",
    fr: "fra_Latn",
    hi: "hin_Deva",
    es: "spa_Latn",
    de: "deu_Latn",
    pt: "por_Latn",
    sw: "swh_Latn",
  };

  for (const [bcp47, expectedNllb] of Object.entries(expectedMappings)) {
    const res = resolveLanguageMapping(bcp47);
    assert.equal(res.supported, true, `Language '${bcp47}' should be supported`);
    if (res.supported) {
      assert.equal(res.nllbCode, expectedNllb, `Expected '${bcp47}' to map to '${expectedNllb}'`);
      assert.ok(res.languageName.length > 0, `Language '${bcp47}' should have a display name`);
    }
  }
});

test("Handles regional BCP-47 variants cleanly", () => {
  const variants = [
    { input: "en-US", expectedNllb: "eng_Latn" },
    { input: "en-GB", expectedNllb: "eng_Latn" },
    { input: "fr-CA", expectedNllb: "fra_Latn" },
    { input: "es-MX", expectedNllb: "spa_Latn" },
    { input: "pt-BR", expectedNllb: "por_Latn" },
    { input: "de-AT", expectedNllb: "deu_Latn" },
  ];

  for (const { input, expectedNllb } of variants) {
    const res = resolveLanguageMapping(input);
    assert.equal(res.supported, true, `Variant '${input}' should be supported`);
    if (res.supported) {
      assert.equal(res.nllbCode, expectedNllb);
    }
  }
});

test("Unsupported languages return explicit unsupported state and DO NOT fallback to English", () => {
  const unsupportedCodes = ["xyz", "unknown", "klingon", "pl", "ro"];

  for (const code of unsupportedCodes) {
    const res = resolveLanguageMapping(code);
    assert.equal(res.supported, false, `Code '${code}' must NOT be supported`);
    if (!res.supported) {
      assert.equal(res.bcp47, code);
      assert.match(res.reason, /not currently supported/i);
      // Must never silently fallback to English
      assert.equal("nllbCode" in res, false);
    }
  }
});

test("Undetermined and empty codes return explicit unsupported/undetermined state", () => {
  const emptyRes = resolveLanguageMapping("");
  assert.equal(emptyRes.supported, false);

  const undRes = resolveLanguageMapping("und");
  assert.equal(undRes.supported, false);
});

test("isLanguageSupported helper returns boolean accurately", () => {
  assert.equal(isLanguageSupported("en"), true);
  assert.equal(isLanguageSupported("sw"), true);
  assert.equal(isLanguageSupported("hi"), true);
  assert.equal(isLanguageSupported("xyz"), false);
});

test("mapBcp47ToNllb helper returns SupportedLanguageInfo or null", () => {
  const en = mapBcp47ToNllb("en");
  assert.notEqual(en, null);
  assert.equal(en?.nllbCode, "eng_Latn");

  const unk = mapBcp47ToNllb("unknown");
  assert.equal(unk, null);
});

test("getSupportedLanguages returns all configured MVP languages", () => {
  const all = getSupportedLanguages();
  assert.ok(all.length >= 7);
  const codes = all.map((l) => l.bcp47);
  assert.ok(codes.includes("en"));
  assert.ok(codes.includes("fr"));
  assert.ok(codes.includes("hi"));
  assert.ok(codes.includes("es"));
  assert.ok(codes.includes("de"));
  assert.ok(codes.includes("pt"));
  assert.ok(codes.includes("sw"));
});
