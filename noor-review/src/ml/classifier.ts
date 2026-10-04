/**
 * Review Classifier Client and Pipeline Integration.
 *
 * Production path: Runs on-device in a dedicated Web Worker (classifier.worker.ts)
 * utilizing Xenova/multilingual-e5-small via @huggingface/transformers.
 *
 * Optional dev path: Can optionally proxy to the Python feedback_module service
 * (http://127.0.0.1:8000) for comparative testing or debugging.
 */

import type { ReviewClassification } from "../types/review.ts";
import type { TeammateAnalyzeResponse } from "../types/classifier.ts";
import { adaptTeammateOutputToReviewClassification } from "./classifierAdapter.ts";
import { looksMeaningless } from "./classifierTaxonomy.ts";

export const DEFAULT_CLASSIFIER_URL = "http://127.0.0.1:8000";
export const DEFAULT_TIMEOUT_MS = 5000;

export interface ClassifyReviewOptions {
  /** Base URL of the teammate's feedback service (only used if useHttpService is true) */
  serviceUrl?: string;
  /** Request timeout in milliseconds */
  timeoutMs?: number;
  /** If true, executes deterministic mock classification without calling model (useful in tests) */
  forceMock?: boolean;
  /** If true, uses the development Python service instead of on-device worker. Defaults to false. */
  useHttpService?: boolean;
  /** Status callback for UI tracking */
  onStatus?: (status: "loading" | "ready" | "classifying") => void;
}

type WorkerStatus =
  | { type: "loading" }
  | { type: "ready" }
  | { type: "result"; probs: Record<string, number>; severity: "low" | "medium" | "high" }
  | { type: "error"; error: string };

let workerInstance: Worker | null = null;
let isBusy = false;

export function isClassifierBusy(): boolean {
  return isBusy;
}

function getWorker(): Worker {
  if (!workerInstance) {
    workerInstance = new Worker(
      new URL("../workers/classifier.worker.ts", import.meta.url),
      { type: "module" }
    );
  }
  return workerInstance;
}

/**
 * Checks health of the teammate's development classifier service.
 */
export async function checkClassifierHealth(
  serviceUrl: string = DEFAULT_CLASSIFIER_URL
): Promise<{ ok: boolean; mode?: string; error?: string }> {
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 2000);
    const res = await fetch(`${serviceUrl.replace(/\/+$/, "")}/health`, {
      method: "GET",
      signal: controller.signal,
    });
    clearTimeout(timeout);
    if (!res.ok) {
      return { ok: false, error: `HTTP ${res.status}` };
    }
    const data = await res.json();
    return { ok: true, mode: data.mode };
  } catch (err) {
    const msg = err instanceof Error ? err.message : "Health check failed";
    return { ok: false, error: msg };
  }
}

/**
 * Executes on-device review classification inside a dedicated Web Worker.
 */
function classifyOnDevice(
  text: string,
  options?: ClassifyReviewOptions
): Promise<ReviewClassification> {
  const worker = getWorker();
  isBusy = true;

  return new Promise((resolve, reject) => {
    const handleMessage = (event: MessageEvent<WorkerStatus>) => {
      const data = event.data;

      if (data.type === "loading") {
        options?.onStatus?.("loading");
      } else if (data.type === "ready") {
        options?.onStatus?.("ready");
      } else if (data.type === "result") {
        isBusy = false;
        worker.removeEventListener("message", handleMessage);
        const result = adaptTeammateOutputToReviewClassification(
          {
            probs: data.probs,
            severity: data.severity,
          },
          text
        );
        resolve(result);
      } else if (data.type === "error") {
        isBusy = false;
        worker.removeEventListener("message", handleMessage);
        reject(new Error(data.error));
      }
    };

    worker.addEventListener("message", handleMessage);
    options?.onStatus?.("classifying");

    worker.postMessage({
      text,
    });
  });
}

/**
 * Optional development fallback: calls teammate's Python FastAPI service.
 */
async function classifyViaHttpService(
  text: string,
  options?: ClassifyReviewOptions
): Promise<ReviewClassification> {
  const baseUrl = (options?.serviceUrl || DEFAULT_CLASSIFIER_URL).replace(/\/+$/, "");
  const timeoutMs = options?.timeoutMs ?? DEFAULT_TIMEOUT_MS;

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const response = await fetch(`${baseUrl}/analyze-feedback`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ text }),
      signal: controller.signal,
    });

    clearTimeout(timer);

    if (!response.ok) {
      throw new Error(`Classifier service returned HTTP ${response.status}: ${response.statusText}`);
    }

    const data: TeammateAnalyzeResponse = await response.json();
    return adaptTeammateOutputToReviewClassification(data, text);
  } catch (error) {
    clearTimeout(timer);

    if (error instanceof DOMException && error.name === "AbortError") {
      throw new Error(
        `Classifier request timed out after ${timeoutMs}ms. Verify teammate service at ${baseUrl}.`
      );
    }

    const detail = error instanceof Error ? error.message : "Unknown error";
    throw new Error(
      `Classifier service unavailable at ${baseUrl} (${detail}). Ensure teammate server is running (e.g. uvicorn app:app --port 8000) or check network.`
    );
  }
}

/**
 * Classifies a visitor review.
 *
 * Defaults to on-device Web Worker running Xenova/multilingual-e5-small.
 * Does NOT require http://127.0.0.1:8000 in production.
 */
export async function classifyReview(
  text: string,
  options?: ClassifyReviewOptions
): Promise<ReviewClassification> {
  const trimmed = (text || "").trim();

  // If text is empty or carries no analyzable meaning
  if (!trimmed || looksMeaningless(trimmed)) {
    return adaptTeammateOutputToReviewClassification(
      {
        detected: [],
        severity: "low",
        needs_human: true,
      },
      trimmed
    );
  }

  // Node environment (unit tests) or explicit forceMock flag
  const isNode = typeof window === "undefined" || typeof Worker === "undefined";
  if (isNode || options?.forceMock) {
    return adaptTeammateOutputToReviewClassification(
      {
        detected: [
          { label: "experience_activity:pos", confidence: 0.92 },
          { label: "timing_waiting:neg", confidence: 0.88 },
          { label: "directions_access:neg", confidence: 0.62 },
        ],
        severity: "medium",
        needs_human: false,
      },
      trimmed
    );
  }

  // Development option to query the Python test oracle
  if (options?.useHttpService) {
    return classifyViaHttpService(trimmed, options);
  }

  // Production on-device execution (default)
  return classifyOnDevice(trimmed, options);
}
