import test from "node:test";
import assert from "node:assert/strict";

import { analyzeReview } from "../src/ml/reviewAnalyzer.ts";

test("analyzeReview successfully executes full pipeline in mock mode", async () => {
  const states = [];
  const result = await analyzeReview(
    "I loved the coffee tour, but we waited for a long time.",
    {
      forceMock: true,
      onStateChange: (state) => states.push(state),
    }
  );

  assert.equal(result.status, "success");
  assert.ok(result.analysis, "Analysis object must be present on success");

  // Validate ReviewAnalysis type contract
  const analysis = result.analysis;
  assert.equal(typeof analysis.originalText, "string");
  assert.equal(analysis.originalText, "I loved the coffee tour, but we waited for a long time.");
  assert.equal(typeof analysis.sourceLanguage, "string");
  assert.equal(typeof analysis.translatedText, "string");

  // Validate ReviewClassification contract
  assert.ok(analysis.classification);
  assert.equal(typeof analysis.classification.overallSentiment, "string");
  assert.ok(Array.isArray(analysis.classification.aspects));

  // Validate Recommendation[] contract
  assert.ok(Array.isArray(analysis.recommendations));
  for (const rec of analysis.recommendations) {
    assert.equal(typeof rec.aspect, "string");
    assert.ok(["low", "medium", "high"].includes(rec.priority));
    assert.match(rec.action, /^Consider /);
  }

  // Validate lifecycle state transitions
  assert.ok(states.includes("detecting"));
  assert.ok(states.includes("classifying"));
  assert.ok(states.includes("recommending"));
  assert.ok(states.includes("completed"));
});

test("analyzeReview handles empty input safely without executing ML pipeline", async () => {
  const states = [];
  const emptyResult = await analyzeReview("", {
    onStateChange: (state) => states.push(state),
  });

  assert.equal(emptyResult.status, "empty");
  assert.equal(emptyResult.analysis, undefined);
  assert.match(emptyResult.message, /no review text provided/i);
  assert.ok(states.includes("completed"));

  // Whitespace only
  const whitespaceResult = await analyzeReview("     ");
  assert.equal(whitespaceResult.status, "empty");
});

test("analyzeReview handles undetermined language safely without silently assuming English", async () => {
  const states = [];
  const result = await analyzeReview("123 ??? xyz", {
    onStateChange: (state) => states.push(state),
  });

  assert.equal(result.status, "undetermined_language");
  assert.equal(result.analysis, undefined);
  assert.ok(result.detection);
  assert.equal(result.detection.status, "undetermined");
  assert.match(result.message, /language unclear/i);

  // Must not have called translation or classification
  assert.ok(!states.includes("translating"));
  assert.ok(!states.includes("classifying"));
  assert.ok(states.includes("completed"));
});

test("analyzeReview handles unsupported language safely without falling back to English", async () => {
  // Polish text (Polish "pl" is recognized by detector, but not in our MVP whitelist)
  const result = await analyzeReview("Bardzo dobra wycieczka, ale za droga.", {
    forceMock: true,
  });

  assert.equal(result.status, "unsupported_language");
  assert.equal(result.analysis, undefined);
  assert.match(result.message, /not currently supported/i);
});

test("analyzeReview handles classifier uncertainty with empty aspects and no recommendations", async () => {
  // Single-word input that passes language check but triggers looksMeaningless
  const result = await analyzeReview("Merci!", {
    forceMock: true,
    targetLanguage: "fra_Latn", // Same as target so no translation needed
  });

  assert.equal(result.status, "success");
  assert.ok(result.analysis);
  assert.equal(result.analysis.originalText, "Merci!");
  assert.equal(result.analysis.translatedText, "Merci!");
  // Uncertainty: no aspects meet confidence threshold
  assert.equal(result.analysis.classification.aspects.length, 0);
  // Consequently, no recommendations should be fabricated
  assert.equal(result.analysis.recommendations.length, 0);
});

test("analyzeReview skips translation when review language matches target language", async () => {
  const states = [];
  const frenchReview = "La visite était très agréable et intéressante.";
  const result = await analyzeReview(frenchReview, {
    forceMock: true,
    targetLanguage: "fra_Latn",
    onStateChange: (state) => states.push(state),
  });

  assert.equal(result.status, "success");
  assert.ok(result.analysis);
  // Source is French and target is French -> translatedText equals originalText directly
  assert.equal(result.analysis.translatedText, frenchReview);
  assert.ok(!states.includes("translating"));
});
