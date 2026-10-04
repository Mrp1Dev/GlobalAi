import test from "node:test";
import assert from "node:assert/strict";

import {
  adaptTeammateOutputToReviewClassification,
  deriveOverallSentiment,
  normalizeSeverity,
} from "../src/ml/classifierAdapter.ts";
import {
  CLASSIFIER_CONFIDENCE_THRESHOLD,
  CLASSIFIER_HIGH_CONFIDENCE_THRESHOLD,
  ASPECT_TAXONOMY_MAP,
  looksMeaningless,
  heuristicSeverity,
} from "../src/ml/classifierTaxonomy.ts";
import { classifyReview, isClassifierBusy } from "../src/ml/classifier.ts";

test("Threshold constants and taxonomy mappings conform to teammate rules specifications", () => {
  assert.equal(CLASSIFIER_CONFIDENCE_THRESHOLD, 0.55);
  assert.equal(CLASSIFIER_HIGH_CONFIDENCE_THRESHOLD, 0.80);
  assert.equal(ASPECT_TAXONOMY_MAP.timing_waiting, "waiting_time");
  assert.equal(ASPECT_TAXONOMY_MAP.guide_hospitality, "hospitality");
  assert.equal(ASPECT_TAXONOMY_MAP.directions_access, "directions");
  assert.equal(ASPECT_TAXONOMY_MAP.facilities_cleanliness, "cleanliness");
});

test("normalizeSeverity correctly normalizes inputs", () => {
  assert.equal(normalizeSeverity("high"), "high");
  assert.equal(normalizeSeverity("medium"), "medium");
  assert.equal(normalizeSeverity("low"), "low");
  assert.equal(normalizeSeverity("unknown"), "low");
  assert.equal(normalizeSeverity(null), "low");
});

test("deriveOverallSentiment calculates sentiment from aspect balances", () => {
  const posAspect = { aspect: "experience", sentiment: "positive", severity: "low", confidence: 0.9 };
  const negAspect = { aspect: "waiting_time", sentiment: "negative", severity: "medium", confidence: 0.7 };

  assert.equal(deriveOverallSentiment([], "low"), "neutral");
  assert.equal(deriveOverallSentiment([posAspect], "low"), "positive");
  assert.equal(deriveOverallSentiment([negAspect], "medium"), "negative");
  assert.equal(deriveOverallSentiment([posAspect, negAspect], "high"), "negative");
  assert.equal(deriveOverallSentiment([posAspect, negAspect], "low"), "positive");
});

test("Valid classifier output maps correctly to ReviewClassification", () => {
  const raw = {
    detected: [
      { label: "timing_waiting:neg", confidence: 0.88 },
      { label: "experience_activity:pos", confidence: 0.92 },
      { label: "directions_access:neg", confidence: 0.62 },
    ],
    severity: "medium",
  };

  const result = adaptTeammateOutputToReviewClassification(raw);

  assert.equal(typeof result.overallSentiment, "string");
  assert.equal(Array.isArray(result.aspects), true);
  assert.equal(result.aspects.length, 3);

  // Check aspect taxonomy mapping
  const waitingAspect = result.aspects.find((a) => a.aspect === "waiting_time");
  assert.ok(waitingAspect, "timing_waiting should map to waiting_time");
  assert.equal(waitingAspect.sentiment, "negative");
  assert.equal(waitingAspect.severity, "medium");
  assert.equal(waitingAspect.confidence, 0.88);

  const expAspect = result.aspects.find((a) => a.aspect === "experience");
  assert.ok(expAspect, "experience_activity should map to experience");
  assert.equal(expAspect.sentiment, "positive");
  assert.equal(expAspect.severity, "low");
  assert.equal(expAspect.confidence, 0.92);

  const dirAspect = result.aspects.find((a) => a.aspect === "directions");
  assert.ok(dirAspect, "directions_access should map to directions");
  assert.equal(dirAspect.sentiment, "negative");
  assert.equal(dirAspect.severity, "medium");
  assert.equal(dirAspect.confidence, 0.62);
});

test("Overall sentiment is preserved and derived deterministically", () => {
  // Pure positive
  const posRaw = {
    detected: [{ label: "guide_hospitality:pos", confidence: 0.85 }],
    severity: "low",
  };
  const posResult = adaptTeammateOutputToReviewClassification(posRaw);
  assert.equal(posResult.overallSentiment, "positive");

  // Pure negative
  const negRaw = {
    detected: [
      { label: "facilities_cleanliness:neg", confidence: 0.81 },
      { label: "timing_waiting:neg", confidence: 0.75 },
    ],
    severity: "medium",
  };
  const negResult = adaptTeammateOutputToReviewClassification(negRaw);
  assert.equal(negResult.overallSentiment, "negative");

  // Mixed with high severity negative cue
  const mixedHighRaw = {
    detected: [
      { label: "experience_activity:pos", confidence: 0.90 },
      { label: "guide_hospitality:neg", confidence: 0.70 },
    ],
    severity: "high",
  };
  const mixedHighResult = adaptTeammateOutputToReviewClassification(mixedHighRaw);
  assert.equal(mixedHighResult.overallSentiment, "negative");

  // No aspects detected
  const emptyResult = adaptTeammateOutputToReviewClassification({ detected: [], severity: "low" });
  assert.equal(emptyResult.overallSentiment, "neutral");
  assert.equal(emptyResult.aspects.length, 0);
});

test("Aspect sentiment is preserved across polarities and unpolarized flags", () => {
  const raw = {
    detected: [
      { label: "guide_hospitality:pos", confidence: 0.78 },
      { label: "food_drink:neg", confidence: 0.82 },
      { label: "would_recommend_or_return", confidence: 0.89 },
      { label: "wish_or_suggestion", confidence: 0.65 },
    ],
    severity: "medium",
  };

  const result = adaptTeammateOutputToReviewClassification(raw);

  const hosp = result.aspects.find((a) => a.aspect === "hospitality");
  assert.equal(hosp?.sentiment, "positive");

  const food = result.aspects.find((a) => a.aspect === "food");
  assert.equal(food?.sentiment, "negative");

  const rec = result.aspects.find((a) => a.aspect === "experience");
  assert.equal(rec?.sentiment, "positive");

  const wish = result.aspects.find((a) => a.aspect === "activities");
  assert.equal(wish?.sentiment, "neutral");
});

test("Severity is preserved properly based on aspect sentiment", () => {
  const highSevRaw = {
    detected: [
      { label: "facilities_cleanliness:neg", confidence: 0.85 },
      { label: "food_drink:pos", confidence: 0.90 },
    ],
    severity: "high",
  };

  const result = adaptTeammateOutputToReviewClassification(highSevRaw);

  const negAspect = result.aspects.find((a) => a.aspect === "cleanliness");
  assert.equal(negAspect?.severity, "high", "Negative aspect must inherit high severity");

  const posAspect = result.aspects.find((a) => a.aspect === "food");
  assert.equal(posAspect?.severity, "low", "Positive aspect must remain low severity");
});

test("Confidence is preserved if genuinely provided and filters below threshold", () => {
  const raw = {
    detected: [
      { label: "price_value:pos", confidence: 0.77 },
      { label: "timing_waiting:neg", confidence: 0.54 }, // Below 0.55 threshold
      { label: "guide_hospitality:pos", confidence: 0.55 }, // Exactly at threshold
    ],
    severity: "low",
  };

  const result = adaptTeammateOutputToReviewClassification(raw);

  assert.equal(result.aspects.length, 2, "Only aspects >= 0.55 confidence should be retained");
  const price = result.aspects.find((a) => a.aspect === "pricing");
  assert.equal(price?.confidence, 0.77);

  const hosp = result.aspects.find((a) => a.aspect === "hospitality");
  assert.equal(hosp?.confidence, 0.55);

  const timing = result.aspects.find((a) => a.aspect === "waiting_time");
  assert.equal(timing, undefined, "Label with confidence 0.54 must be filtered out");
});

test("Invalid and unsupported classifier output is handled safely without throwing", () => {
  assert.deepEqual(adaptTeammateOutputToReviewClassification(null), {
    overallSentiment: "neutral",
    aspects: [],
  });

  assert.deepEqual(adaptTeammateOutputToReviewClassification(undefined), {
    overallSentiment: "neutral",
    aspects: [],
  });

  assert.deepEqual(adaptTeammateOutputToReviewClassification({}), {
    overallSentiment: "neutral",
    aspects: [],
  });

  assert.deepEqual(adaptTeammateOutputToReviewClassification({ error: "empty" }), {
    overallSentiment: "neutral",
    aspects: [],
  });

  // Malformed detected array elements
  const malformed = {
    detected: [{ invalid: true }, null, { label: 123, confidence: "high" }],
    severity: "unknown",
  };
  const malformedResult = adaptTeammateOutputToReviewClassification(malformed);
  assert.equal(malformedResult.overallSentiment, "neutral");
  assert.equal(malformedResult.aspects.length, 0);
});

test("looksMeaningless correctly detects garbage or short inputs", () => {
  assert.equal(looksMeaningless(""), true);
  assert.equal(looksMeaningless("   "), true);
  assert.equal(looksMeaningless("123 ??? xyz"), true);
  assert.equal(looksMeaningless("Ok"), true);
  assert.equal(looksMeaningless("👍"), true);

  assert.equal(looksMeaningless("Great coffee tour!"), false);
  assert.equal(looksMeaningless("बहुत अच्छा टूर था"), false);
  assert.equal(looksMeaningless("We waited too long"), false);
});

test("heuristicSeverity accurately flags high cues across languages", () => {
  const negProbs = { "timing_waiting:neg": 0.88 };

  // English cue
  assert.equal(heuristicSeverity("The tour was terrible!", negProbs), "high");
  // Hindi cue
  assert.equal(heuristicSeverity("व्यवस्था बहुत बेकार थी", negProbs), "high");
  // Roman Hindi cue
  assert.equal(heuristicSeverity("Tour ekdum bakwas tha", negProbs), "high");
  // No high cue
  assert.equal(heuristicSeverity("We had to wait a bit", negProbs), "medium");
  // No negative labels above threshold
  assert.equal(heuristicSeverity("The tour was terrible!", { "timing_waiting:neg": 0.40 }), "low");
});

test("classifyReview supports deterministic mock mode", async () => {
  const result = await classifyReview("I loved the coffee tour, but we waited for a long time.", {
    forceMock: true,
  });

  assert.ok(result);
  assert.equal(typeof result.overallSentiment, "string");
  assert.ok(result.aspects.length >= 2);

  const waiting = result.aspects.find((a) => a.aspect === "waiting_time");
  assert.ok(waiting);
  assert.equal(waiting.sentiment, "negative");
  assert.equal(waiting.confidence, 0.88);
});

test("isClassifierBusy returns boolean flag correctly", () => {
  assert.equal(typeof isClassifierBusy(), "boolean");
  assert.equal(isClassifierBusy(), false);
});

test("Representative dataset inputs adapt accurately to expected project aspects and severity", () => {
  // 1. "We waited almost an hour before the tour started and nobody told us why."
  const waitingReview = adaptTeammateOutputToReviewClassification(
    {
      probs: { "timing_waiting:neg": 0.91, "guide_hospitality:pos": 0.20 },
      severity: "high",
    },
    "We waited almost an hour before the tour started and nobody told us why."
  );
  assert.equal(waitingReview.overallSentiment, "negative");
  assert.equal(waitingReview.aspects.length, 1);
  assert.equal(waitingReview.aspects[0].aspect, "waiting_time");
  assert.equal(waitingReview.aspects[0].sentiment, "negative");
  assert.equal(waitingReview.aspects[0].severity, "high");
  assert.equal(waitingReview.aspects[0].confidence, 0.91);

  // 2. "The guide was warm and answered all our questions. Best part of our trip."
  const hospitalityReview = adaptTeammateOutputToReviewClassification(
    {
      probs: { "guide_hospitality:pos": 0.89, "would_recommend_or_return": 0.85 },
      severity: "low",
    },
    "The guide was warm and answered all our questions. Best part of our trip."
  );
  assert.equal(hospitalityReview.overallSentiment, "positive");
  assert.ok(hospitalityReview.aspects.some((a) => a.aspect === "hospitality" && a.sentiment === "positive"));
  assert.ok(hospitalityReview.aspects.some((a) => a.aspect === "experience" && a.sentiment === "positive"));

  // 3. "The lunch was cold and bland, quite disappointing."
  const foodReview = adaptTeammateOutputToReviewClassification(
    {
      probs: { "food_drink:neg": 0.82 },
      severity: "medium",
    },
    "The lunch was cold and bland, quite disappointing."
  );
  assert.equal(foodReview.overallSentiment, "negative");
  assert.equal(foodReview.aspects[0].aspect, "food");
  assert.equal(foodReview.aspects[0].sentiment, "negative");
  assert.equal(foodReview.aspects[0].severity, "medium");

  // 4. "Hard to find the farm, the road was bumpy and the map took us to the wrong gate."
  const directionsReview = adaptTeammateOutputToReviewClassification(
    {
      probs: { "directions_access:neg": 0.87 },
      severity: "medium",
    },
    "Hard to find the farm, the road was bumpy and the map took us to the wrong gate."
  );
  assert.equal(directionsReview.overallSentiment, "negative");
  assert.equal(directionsReview.aspects[0].aspect, "directions");
  assert.equal(directionsReview.aspects[0].sentiment, "negative");

  // 5. "Spotless place and excellent tea and snacks."
  const multiAspectReview = adaptTeammateOutputToReviewClassification(
    {
      probs: { "facilities_cleanliness:pos": 0.90, "food_drink:pos": 0.85 },
      severity: "low",
    },
    "Spotless place and excellent tea and snacks."
  );
  assert.equal(multiAspectReview.overallSentiment, "positive");
  assert.equal(multiAspectReview.aspects.length, 2);
  assert.ok(multiAspectReview.aspects.some((a) => a.aspect === "cleanliness"));
  assert.ok(multiAspectReview.aspects.some((a) => a.aspect === "food"));
});
