import test from "node:test";
import assert from "node:assert/strict";

import {
  generateRecommendations,
  RECOMMENDATION_RULES,
} from "../src/rules/recommendationRules.ts";

test("Every rule in RECOMMENDATION_RULES is valid and contains actionable suggestions", () => {
  const aspects = Object.keys(RECOMMENDATION_RULES);
  assert.ok(aspects.length >= 8, "Expected at least 8 aspects in ruleset");

  for (const [aspect, rules] of Object.entries(RECOMMENDATION_RULES)) {
    if (rules.negative) {
      assert.match(rules.negative, /^Consider /, `${aspect} negative rule must start with 'Consider '`);
      assert.ok(rules.negative.length > 15, `${aspect} negative rule must be descriptive`);
    }
    if (rules.positive) {
      assert.match(rules.positive, /^Consider /, `${aspect} positive rule must start with 'Consider '`);
      assert.ok(rules.positive.length > 15, `${aspect} positive rule must be descriptive`);
    }
  }
});

test("Rule verification: waiting_time negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "waiting_time", sentiment: "negative", severity: "high", confidence: 0.88 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].aspect, "waiting_time");
  assert.equal(neg[0].priority, "high");
  assert.equal(neg[0].action, "Consider improving arrival and start-time communication.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "waiting_time", sentiment: "positive", severity: "low", confidence: 0.85 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].priority, "low");
  assert.equal(pos[0].action, "Consider maintaining the current punctual tour schedule.");
});

test("Rule verification: directions negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "directions", sentiment: "negative", severity: "medium", confidence: 0.75 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].aspect, "directions");
  assert.equal(neg[0].priority, "medium");
  assert.equal(neg[0].action, "Consider providing clearer directions and landmark guidance before visitor arrival.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "directions", sentiment: "positive", severity: "low", confidence: 0.80 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider continuing to share the current clear route guidance with new visitors.");
});

test("Rule verification: hospitality negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "hospitality", sentiment: "negative", severity: "high", confidence: 0.85 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].priority, "high");
  assert.equal(neg[0].action, "Consider greeting visitors personally at the start and setting aside time for questions.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "hospitality", sentiment: "positive", severity: "low", confidence: 0.90 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider maintaining the current warm hospitality approach.");
});

test("Rule verification: food negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "food", sentiment: "negative", severity: "medium", confidence: 0.79 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].action, "Consider reviewing the food offering and clarifying dietary expectations before the visit.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "food", sentiment: "positive", severity: "low", confidence: 0.84 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider keeping popular food and drink offerings as regular visit highlights.");
});

test("Rule verification: cleanliness negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "cleanliness", sentiment: "negative", severity: "high", confidence: 0.86 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].priority, "high");
  assert.equal(neg[0].action, "Consider reviewing cleaning and preparation of seating and restrooms before visitor arrival.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "cleanliness", sentiment: "positive", severity: "low", confidence: 0.82 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider maintaining the current high standard of cleanliness and facilities.");
});

test("Rule verification: experience negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "experience", sentiment: "negative", severity: "medium", confidence: 0.70 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].action, "Consider reviewing the tour duration and pacing to identify any tiring or dull segments.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "experience", sentiment: "positive", severity: "low", confidence: 0.92 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider keeping and strengthening the parts of the experience visitors value most.");
});

test("Rule verification: pricing negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "pricing", sentiment: "negative", severity: "medium", confidence: 0.74 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].action, "Consider making pricing details and inclusions clearer before the visit.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "pricing", sentiment: "positive", severity: "low", confidence: 0.78 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider continuing to clearly communicate pricing and included tour benefits upfront.");
});

test("Rule verification: communication negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "communication", sentiment: "negative", severity: "medium", confidence: 0.72 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].action, "Consider providing clearer visitor information and reminders before arrival.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "communication", sentiment: "positive", severity: "low", confidence: 0.77 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider continuing proactive and helpful visitor communication.");
});

test("Rule verification: learning_authenticity negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "learning_authenticity", sentiment: "negative", severity: "medium", confidence: 0.81 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].action, "Consider explaining daily farm practices in simpler terms to enhance authentic learning.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "learning_authenticity", sentiment: "positive", severity: "low", confidence: 0.89 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider continuing to showcase authentic, hands-on farm activities as key highlights.");
});

test("Rule verification: activities negative and positive", () => {
  const neg = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "activities", sentiment: "negative", severity: "medium", confidence: 0.68 }],
  });
  assert.equal(neg.length, 1);
  assert.equal(neg[0].action, "Consider adjusting activity difficulty and pacing to ensure all visitors can comfortably participate.");

  const pos = generateRecommendations({
    overallSentiment: "positive",
    aspects: [{ aspect: "activities", sentiment: "positive", severity: "low", confidence: 0.75 }],
  });
  assert.equal(pos.length, 1);
  assert.equal(pos[0].action, "Consider featuring the popular visitor activities prominently in tour descriptions.");
});

test("Unknown aspect produces no recommendations", () => {
  const result = generateRecommendations({
    overallSentiment: "negative",
    aspects: [{ aspect: "unknown_aspect_xyz", sentiment: "negative", severity: "high", confidence: 0.99 }],
  });
  assert.equal(result.length, 0);
});

test("Low confidence aspects below threshold produce no recommendations", () => {
  const result = generateRecommendations({
    overallSentiment: "negative",
    aspects: [
      { aspect: "waiting_time", sentiment: "negative", severity: "high", confidence: 0.54 }, // Below 0.55
      { aspect: "food", sentiment: "negative", severity: "medium", confidence: 0.30 },
    ],
  });
  assert.equal(result.length, 0);

  // Custom minConfidence option
  const customThresholdResult = generateRecommendations(
    {
      overallSentiment: "negative",
      aspects: [{ aspect: "waiting_time", sentiment: "negative", severity: "high", confidence: 0.65 }],
    },
    { minConfidence: 0.70 }
  );
  assert.equal(customThresholdResult.length, 0);
});

test("Neutral sentiment produces no recommendations", () => {
  const result = generateRecommendations({
    overallSentiment: "neutral",
    aspects: [
      { aspect: "hospitality", sentiment: "neutral", severity: "low", confidence: 0.88 },
      { aspect: "waiting_time", sentiment: "neutral", severity: "low", confidence: 0.75 },
    ],
  });
  assert.equal(result.length, 0);
});

test("Multiple aspects are sorted with high priority first", () => {
  const result = generateRecommendations({
    overallSentiment: "negative",
    aspects: [
      { aspect: "experience", sentiment: "positive", severity: "low", confidence: 0.92 },
      { aspect: "cleanliness", sentiment: "negative", severity: "high", confidence: 0.88 },
      { aspect: "pricing", sentiment: "negative", severity: "medium", confidence: 0.70 },
    ],
  });

  assert.equal(result.length, 3);
  // High priority first
  assert.equal(result[0].aspect, "cleanliness");
  assert.equal(result[0].priority, "high");

  // Medium priority second
  assert.equal(result[1].aspect, "pricing");
  assert.equal(result[1].priority, "medium");

  // Low priority third
  assert.equal(result[2].aspect, "experience");
  assert.equal(result[2].priority, "low");
});

test("Handles alias mappings from raw teammate labels seamlessly", () => {
  const result = generateRecommendations({
    overallSentiment: "negative",
    aspects: [
      { aspect: "timing_waiting", sentiment: "negative", severity: "high", confidence: 0.85 },
      { aspect: "guide_hospitality", sentiment: "positive", severity: "low", confidence: 0.80 },
    ],
  });

  assert.equal(result.length, 2);
  assert.equal(result[0].aspect, "waiting_time");
  assert.equal(result[1].aspect, "hospitality");
});

test("Handles null, undefined, and empty classifications safely", () => {
  assert.deepEqual(generateRecommendations(null), []);
  assert.deepEqual(generateRecommendations(undefined), []);
  assert.deepEqual(generateRecommendations({ overallSentiment: "neutral", aspects: [] }), []);
});
