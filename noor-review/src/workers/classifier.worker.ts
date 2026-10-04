import { pipeline } from "@huggingface/transformers";

/**
 * 18-label taxonomy and descriptions from feedback_module/labels.py.
 * Source of truth for on-device zero-shot classification.
 */
const LABEL_DESCRIPTIONS: Readonly<Record<string, string>> = {
  "guide_hospitality:pos": "Guests praise the friendly, welcoming, helpful guide or host.",
  "guide_hospitality:neg": "Guests complain the guide or host was rude, unwelcoming, unhelpful or unfriendly.",
  "experience_activity:pos": "Guests enjoyed the activities, the farm walk and the overall experience.",
  "experience_activity:neg": "Guests found the activities boring, too long, too tiring or disappointing.",
  "food_drink:pos": "Guests liked the food, drinks, tasting or fresh farm produce.",
  "food_drink:neg": "Guests complained the food or drink was bad, cold, bland or unsafe.",
  "learning_authenticity:pos": "Guests learned a lot and felt the farm experience was authentic and genuine.",
  "learning_authenticity:neg": "Guests learned little or felt the experience was staged, fake or not authentic.",
  "price_value:pos": "Guests felt the price was fair and the tour was good value for money.",
  "price_value:neg": "Guests felt the tour was overpriced, expensive or poor value for money.",
  "timing_waiting:pos": "The tour started on time and was well organised.",
  "timing_waiting:neg": "Guests had to wait a long time, or the tour started late or was disorganised.",
  "directions_access:pos": "The farm was easy to find and reach.",
  "directions_access:neg": "The farm was hard to find, the directions were unclear or the road was difficult.",
  "facilities_cleanliness:pos": "The place was clean and the facilities such as toilets and seating were good.",
  "facilities_cleanliness:neg": "The place was dirty or the facilities such as toilets and seating were poor.",
  "would_recommend_or_return": "Guests say they would recommend the tour to others or come back again.",
  "wish_or_suggestion": "Guests wish for or suggest something extra, such as more activities or a new offering.",
};

const LABELS = Object.keys(LABEL_DESCRIPTIONS);

const HIGH_CUES: readonly string[] = [
  "terrible", "worst", "awful", "horrible", "disgusting", "rude", "never again",
  "waste of", "scam", "unsafe", "filthy", "hours", "refund",
  "बहुत बुरा", "बेकार", "गंदा", "घटिया", "ठगी", "कभी नहीं", "बकवास",
  "bakwas", "bekaar", "ganda", "ghatiya",
];

function sigmoid(x: number): number {
  return 1.0 / (1.0 + Math.exp(-x));
}

function dotProduct(a: Float32Array, b: Float32Array): number {
  let sum = 0;
  const len = a.length;
  for (let i = 0; i < len; i++) {
    sum += a[i] * b[i];
  }
  return sum;
}

function heuristicSeverity(text: string, probs: Record<string, number>): "low" | "medium" | "high" {
  const hasNeg = Object.entries(probs).some(
    ([lab, p]) => lab.endsWith(":neg") && p >= 0.55
  );
  if (!hasNeg) {
    return "low";
  }
  const t = text.toLowerCase();
  return HIGH_CUES.some((cue) => t.includes(cue.toLowerCase())) ? "high" : "medium";
}

let extractor: any = null;
let cachedLabelEmbeddings: Array<{ label: string; vector: Float32Array }> | null = null;

self.onmessage = async (event) => {
  const { text } = event.data;

  try {
    // 1. Initialize extractor and cache label embeddings on first run
    if (!extractor) {
      self.postMessage({ type: "loading" });

      extractor = await pipeline(
        "feature-extraction",
        "Xenova/multilingual-e5-small",
        {
          dtype: "q8",
        }
      );

      // Precompute static label embeddings (18 x 384 numbers)
      const labelQueries = LABELS.map((lab) => "query: " + LABEL_DESCRIPTIONS[lab]);
      const labelTensor = await extractor(labelQueries, {
        pooling: "mean",
        normalize: true,
      });

      const dim = 384;
      const rawData: Float32Array = labelTensor.data;
      cachedLabelEmbeddings = [];

      for (let i = 0; i < LABELS.length; i++) {
        const slice = rawData.slice(i * dim, (i + 1) * dim);
        cachedLabelEmbeddings.push({
          label: LABELS[i],
          vector: slice,
        });
      }

      self.postMessage({ type: "ready" });
    }

    if (!text || typeof text !== "string") {
      self.postMessage({
        type: "result",
        probs: {},
        severity: "low",
      });
      return;
    }

    // 2. Embed the visitor's review text
    const reviewTensor = await extractor(["query: " + text.trim()], {
      pooling: "mean",
      normalize: true,
    });

    const reviewVec: Float32Array = reviewTensor.data;

    // 3. Compute cosine similarities against label vectors
    const sims: number[] = [];
    if (!cachedLabelEmbeddings) {
      throw new Error("Label embeddings were not initialized");
    }

    for (const item of cachedLabelEmbeddings) {
      sims.push(dotProduct(item.vector, reviewVec));
    }

    // 4. Zero-shot baseline: z-score similarities across labels, squash to 0..1 via sigmoid
    let sum = 0;
    for (let i = 0; i < sims.length; i++) {
      sum += sims[i];
    }
    const mean = sum / sims.length;

    let varSum = 0;
    for (let i = 0; i < sims.length; i++) {
      varSum += Math.pow(sims[i] - mean, 2);
    }
    const std = Math.sqrt(varSum / sims.length) + 1e-9;

    const probs: Record<string, number> = {};
    for (let i = 0; i < LABELS.length; i++) {
      const zi = (sims[i] - mean) / std;
      probs[LABELS[i]] = sigmoid(2.0 * (zi - 1.5));
    }

    // 5. Evaluate severity using keyword cues and negative probabilities
    const severity = heuristicSeverity(text, probs);

    self.postMessage({
      type: "result",
      probs,
      severity,
    });
  } catch (error) {
    const errorMsg = error instanceof Error ? error.message : "Classification worker error";
    self.postMessage({
      type: "error",
      error: errorMsg,
    });
  }
};
