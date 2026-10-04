import test from "node:test";
import assert from "node:assert/strict";

import { DEMO_SCENARIOS } from "../src/demo/demoScenarios.ts";
import { detectLanguage } from "../src/ml/languageDetector.ts";
import { mapBcp47ToNllb } from "../src/ml/languageMapping.ts";
import { generateRecommendations } from "../src/rules/recommendationRules.ts";

test("DEMO_SCENARIOS contains exactly 5 representative hackathon scenarios", () => {
  assert.equal(DEMO_SCENARIOS.length, 5, "Expected exactly 5 demo scenarios");

  const expectedCategories = [
    "positive",
    "negative_waiting",
    "mixed",
    "directions_issue",
    "positive_hospitality",
  ];

  const actualCategories = DEMO_SCENARIOS.map((s) => s.category);
  assert.deepEqual(actualCategories, expectedCategories);
});

test("Each demo scenario has complete, valid documentation fields", () => {
  for (const scenario of DEMO_SCENARIOS) {
    assert.ok(scenario.id, "Scenario must have an id");
    assert.ok(scenario.title, "Scenario must have a title");
    assert.ok(scenario.originalReview, "Scenario must have originalReview");
    assert.ok(scenario.visitorLanguage, "Scenario must declare visitorLanguage");

    // Language fields
    assert.ok(scenario.expectedDetectedLanguage.bcp47, "Must have bcp47 code");
    assert.ok(scenario.expectedDetectedLanguage.nllbCode, "Must have nllb code");
    assert.ok(scenario.expectedDetectedLanguage.name, "Must have language name");

    // Translation
    assert.ok(scenario.expectedTranslation, "Must have expectedTranslation");

    // Classification
    assert.ok(["positive", "negative", "neutral"].includes(scenario.expectedClassification.overallSentiment));
    assert.ok(Array.isArray(scenario.expectedClassification.primaryAspects));
    assert.ok(scenario.expectedClassification.primaryAspects.length > 0);

    for (const aspect of scenario.expectedClassification.primaryAspects) {
      assert.ok(typeof aspect.aspect === "string");
      assert.ok(["positive", "negative", "neutral"].includes(aspect.sentiment));
      assert.ok(["low", "medium", "high"].includes(aspect.severity));
    }

    // Recommendations
    assert.ok(Array.isArray(scenario.expectedRecommendations));
    assert.ok(scenario.expectedRecommendations.length > 0);
    for (const rec of scenario.expectedRecommendations) {
      assert.match(rec, /^Consider /, "Recommendation must be phrased as suggestion");
    }

    // Business context for Noor
    assert.ok(scenario.businessContextForNoor, "Must provide business rationale for Noor");
    assert.ok(scenario.businessContextForNoor.length > 30, "Business rationale must be descriptive");
  }
});

test("Language detection and NLLB mapping accurately identify demo review texts", async () => {
  for (const scenario of DEMO_SCENARIOS) {
    const detection = await detectLanguage(scenario.originalReview);
    assert.equal(
      detection.languageCode,
      scenario.expectedDetectedLanguage.bcp47,
      `Language detection mismatch for scenario ${scenario.id}: expected ${scenario.expectedDetectedLanguage.bcp47}, got ${detection.languageCode}`
    );

    const mapping = mapBcp47ToNllb(detection.languageCode);
    assert.ok(mapping, `Language ${detection.languageCode} should be supported for scenario ${scenario.id}`);
    assert.equal(
      mapping.nllbCode,
      scenario.expectedDetectedLanguage.nllbCode,
      `NLLB code mismatch for scenario ${scenario.id}`
    );
    assert.equal(
      mapping.name,
      scenario.expectedDetectedLanguage.name,
      `Language name mismatch for scenario ${scenario.id}`
    );
  }
});

test("Deterministic recommendation engine produces exact expected recommendations for all 5 scenarios", () => {
  for (const scenario of DEMO_SCENARIOS) {
    // Construct classification object from scenario's expected primary aspects with high confidence (0.85)
    const mockClassification = {
      overallSentiment: scenario.expectedClassification.overallSentiment,
      aspects: scenario.expectedClassification.primaryAspects.map((item) => ({
        aspect: item.aspect,
        sentiment: item.sentiment,
        severity: item.severity,
        confidence: 0.85,
      })),
    };

    const recommendations = generateRecommendations(mockClassification);
    const recommendationActions = recommendations.map((r) => r.action);

    assert.deepEqual(
      recommendationActions,
      scenario.expectedRecommendations,
      `Recommendation actions mismatch for scenario ${scenario.id}`
    );
  }
});

test("Scenario 4 (French) demonstrates translation bypass when source matches target", () => {
  const scenario4 = DEMO_SCENARIOS.find((s) => s.id === "demo-4-directions");
  assert.ok(scenario4);
  assert.equal(scenario4.expectedDetectedLanguage.nllbCode, "fra_Latn");
  // Expected translation is identical to original because target language is French
  assert.equal(scenario4.expectedTranslation, scenario4.originalReview);
});

test("Scenario 2 (Waiting Time) produces High priority recommendation first", () => {
  const scenario2 = DEMO_SCENARIOS.find((s) => s.id === "demo-2-negative-waiting");
  assert.ok(scenario2);

  const mockClassification = {
    overallSentiment: scenario2.expectedClassification.overallSentiment,
    aspects: scenario2.expectedClassification.primaryAspects.map((item) => ({
      aspect: item.aspect,
      sentiment: item.sentiment,
      severity: item.severity,
      confidence: 0.91,
    })),
  };

  const recs = generateRecommendations(mockClassification);
  assert.equal(recs.length, 1);
  assert.equal(recs[0].priority, "high");
  assert.equal(recs[0].aspect, "waiting_time");
});
