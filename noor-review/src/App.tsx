import { useState } from "react";
import { analyzeReview } from "./ml/reviewAnalyzer.ts";
import type { AnalyzerState, ReviewAnalysisResult } from "./ml/reviewAnalyzer.ts";
import type { ReviewClassification } from "./types/review.ts";

/**
 * Normalizes aspect identifiers to clean, readable titles on small screens.
 */
function formatAspectName(raw: string): string {
  const map: Record<string, string> = {
    waiting_time: "Waiting time",
    timing_waiting: "Waiting time",
    directions: "Directions",
    directions_access: "Directions",
    hospitality: "Hospitality",
    food: "Food & refreshments",
    cleanliness: "Cleanliness",
    experience: "Experience",
    experience_activity: "Experience",
    pricing: "Pricing",
    communication: "Communication",
    learning_authenticity: "Learning & authenticity",
    activities: "Activities",
  };
  if (map[raw]) return map[raw];
  return raw
    .replace(/_/g, " ")
    .replace(/\b\w/g, (c) => c.toUpperCase());
}

/**
 * Derives user-facing overall sentiment label matching the prompt specification (e.g. "Mixed / Negative").
 */
function formatOverallSentiment(classification: ReviewClassification): string {
  const posCount = classification.aspects.filter((a) => a.sentiment === "positive").length;
  const negCount = classification.aspects.filter((a) => a.sentiment === "negative").length;

  if (posCount > 0 && negCount > 0) {
    if (classification.overallSentiment === "negative") {
      return "Mixed / Negative";
    }
    if (classification.overallSentiment === "positive") {
      return "Mixed / Positive";
    }
    return "Mixed";
  }

  if (classification.overallSentiment === "positive") return "Positive";
  if (classification.overallSentiment === "negative") return "Negative";
  return "Neutral";
}

/**
 * Formats a recommendation rule action into an actionable improvement string.
 */
function formatImprovementAction(action: string): string {
  const clean = action.trim();
  if (/^Consider\s+/i.test(clean)) {
    const withoutConsider = clean.replace(/^Consider\s+/i, "");
    const gerundMap: Record<string, string> = {
      improving: "Improve",
      providing: "Provide",
      reviewing: "Review",
      maintaining: "Maintain",
      keeping: "Keep",
      making: "Make",
      greeting: "Greet",
      continuing: "Continue",
    };
    const words = withoutConsider.split(" ");
    const firstWord = words[0]?.toLowerCase() || "";
    if (gerundMap[firstWord]) {
      return gerundMap[firstWord] + withoutConsider.slice(firstWord.length);
    }
    return withoutConsider.charAt(0).toUpperCase() + withoutConsider.slice(1);
  }
  return clean;
}

function App() {
  const [review, setReview] = useState(
    "I loved the coffee tour, but we waited for a long time."
  );
  const [result, setResult] = useState<ReviewAnalysisResult | null>(null);
  const [useMockClassifier, setUseMockClassifier] = useState(false);
  const [phase, setPhase] = useState<AnalyzerState>("idle");
  const [statusMessage, setStatusMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");

  const isBusy =
    phase === "detecting" ||
    phase === "translating" ||
    phase === "classifying" ||
    phase === "recommending";

  const isEmpty = !review.trim();

  function handleClear() {
    if (isBusy) return;
    setReview("");
    setResult(null);
    setPhase("idle");
    setStatusMessage("");
    setErrorMessage("");
  }

  async function handleAnalyze() {
    if (isBusy || isEmpty) return;

    setPhase("detecting");
    setResult(null);
    setErrorMessage("");
    setStatusMessage("Detecting language...");

    try {
      const outcome = await analyzeReview(review, {
        forceMock: useMockClassifier,
        onStateChange: (state, msg) => {
          setPhase(state);
          if (msg) {
            setStatusMessage(msg);
          }
        },
      });

      setResult(outcome);

      if (
        outcome.status === "error" ||
        outcome.status === "translation_error" ||
        outcome.status === "classification_error"
      ) {
        setPhase("error");
        setErrorMessage(
          outcome.error || outcome.message || "An error occurred during analysis."
        );
        setStatusMessage("");
      } else {
        setPhase("completed");
        setStatusMessage("");
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : "Analysis failed unexpectedly.";
      setErrorMessage(msg);
      setPhase("error");
      setStatusMessage("");
    }
  }

  const detection = result?.detection;
  const analysis = result?.analysis;

  return (
    <main
      style={{
        maxWidth: "480px",
        width: "100%",
        margin: "0 auto",
        padding: "24px 16px 40px",
        fontFamily: "var(--font-sans)",
        color: "#111827",
        boxSizing: "border-box",
      }}
    >
      {/* App Header */}
      <header style={{ marginBottom: "20px" }}>
        <h1 style={{ fontSize: "24px", fontWeight: "700", letterSpacing: "-0.5px" }}>
          Noor Review
        </h1>
        <p style={{ fontSize: "14px", color: "#6b7280", marginTop: "2px" }}>
          Multilingual Visitor Feedback
        </p>
      </header>

      {/* Input Section */}
      <section style={{ marginBottom: "16px" }}>
        <label
          htmlFor="visitor-review"
          style={{
            display: "block",
            fontSize: "15px",
            fontWeight: "600",
            marginBottom: "8px",
            color: "#374151",
          }}
        >
          Visitor review
        </label>
        <textarea
          id="visitor-review"
          value={review}
          onChange={(e) => {
            setReview(e.target.value);
            if (result) setResult(null);
            if (errorMessage) setErrorMessage("");
          }}
          disabled={isBusy}
          placeholder="Paste or type a visitor review here..."
          rows={4}
          style={{
            width: "100%",
            padding: "12px",
            fontSize: "16px",
            lineHeight: "1.5",
            borderRadius: "8px",
            border: "1px solid #d1d5db",
            backgroundColor: isBusy ? "#f3f4f6" : "#ffffff",
            color: "#111827",
            boxSizing: "border-box",
            resize: "vertical",
          }}
        />
      </section>

      {/* Single Primary Action Button */}
      <div style={{ marginBottom: "12px" }}>
        <button
          type="button"
          onClick={handleAnalyze}
          disabled={isBusy || isEmpty}
          style={{
            width: "100%",
            minHeight: "48px",
            padding: "12px 20px",
            fontSize: "16px",
            fontWeight: "600",
            backgroundColor: isBusy || isEmpty ? "#e5e7eb" : "#1d4ed8",
            color: isBusy || isEmpty ? "#9ca3af" : "#ffffff",
            border: "none",
            borderRadius: "8px",
            cursor: isBusy || isEmpty ? "not-allowed" : "pointer",
          }}
        >
          {phase === "detecting" && "Detecting language..."}
          {phase === "translating" && "Translating..."}
          {phase === "classifying" && "Analyzing review..."}
          {phase === "recommending" && "Generating recommendations..."}
          {(phase === "idle" || phase === "completed" || phase === "error") &&
            "Analyze Review"}
        </button>
      </div>

      {/* Minimal Utility Controls: Clear & Offline Mock Mode */}
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginBottom: "20px",
          fontSize: "13px",
          color: "#6b7280",
        }}
      >
        <label
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "6px",
            cursor: isBusy ? "not-allowed" : "pointer",
          }}
        >
          <input
            type="checkbox"
            checked={useMockClassifier}
            disabled={isBusy}
            onChange={(e) => setUseMockClassifier(e.target.checked)}
          />
          <span>Offline mock mode</span>
        </label>

        {(!isEmpty || result) && (
          <button
            type="button"
            onClick={handleClear}
            disabled={isBusy}
            style={{
              background: "none",
              border: "none",
              padding: "4px 8px",
              color: "#6b7280",
              fontSize: "13px",
              textDecoration: "underline",
              cursor: isBusy ? "not-allowed" : "pointer",
            }}
          >
            Clear input
          </button>
        )}
      </div>

      {/* Clear Loading State */}
      {isBusy && statusMessage && (
        <div
          role="status"
          style={{
            padding: "12px 14px",
            marginBottom: "20px",
            backgroundColor: "#eff6ff",
            border: "1px solid #bfdbfe",
            borderRadius: "8px",
            color: "#1e40af",
            fontSize: "14px",
            fontWeight: "500",
          }}
        >
          ⏳ {statusMessage}
        </div>
      )}

      {/* Clear Error State */}
      {errorMessage && (
        <div
          role="alert"
          style={{
            padding: "12px 14px",
            marginBottom: "20px",
            backgroundColor: "#fef2f2",
            border: "1px solid #fecaca",
            borderRadius: "8px",
            color: "#991b1b",
            fontSize: "14px",
          }}
        >
          <strong>Error:</strong> {errorMessage}
        </div>
      )}

      {/* Clear Uncertainty: Undetermined Language */}
      {result?.status === "undetermined_language" && (
        <div
          style={{
            padding: "14px",
            marginBottom: "20px",
            backgroundColor: "#fffbeb",
            border: "1px solid #fde68a",
            borderRadius: "8px",
            color: "#92400e",
          }}
        >
          <p style={{ fontWeight: "600", fontSize: "15px", marginBottom: "4px" }}>
            ⚠️ Language unclear — please ask a person.
          </p>
          <p style={{ fontSize: "13px", lineHeight: "1.4" }}>
            The language of this review could not be identified with certainty.
            Please ask the visitor or someone nearby to assist.
          </p>
        </div>
      )}

      {/* Clear Uncertainty: Unsupported Language */}
      {result?.status === "unsupported_language" && (
        <div
          style={{
            padding: "14px",
            marginBottom: "20px",
            backgroundColor: "#fffbeb",
            border: "1px solid #fde68a",
            borderRadius: "8px",
            color: "#92400e",
          }}
        >
          <p style={{ fontWeight: "600", fontSize: "15px", marginBottom: "4px" }}>
            ⚠️ Language not currently supported
          </p>
          <p style={{ fontSize: "13px", lineHeight: "1.4" }}>
            {result.message ||
              (detection && "message" in detection ? detection.message : "") ||
              "This language is not currently supported for automated translation."}
          </p>
        </div>
      )}

      {/* Analysis Results */}
      {analysis && (
        <article style={{ marginTop: "12px" }}>
          {/* Section: Detected Language, Original Review, Translation */}
          <section style={{ marginBottom: "20px" }}>
            <div style={{ marginBottom: "16px" }}>
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  textTransform: "uppercase",
                  color: "#6b7280",
                  marginBottom: "4px",
                }}
              >
                Detected language
              </div>
              <div style={{ fontSize: "16px", fontWeight: "600", color: "#111827" }}>
                {analysis.sourceLanguage}
              </div>
            </div>

            <div style={{ marginBottom: "16px" }}>
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  textTransform: "uppercase",
                  color: "#6b7280",
                  marginBottom: "4px",
                }}
              >
                Original review
              </div>
              <p
                style={{
                  fontSize: "15px",
                  color: "#374151",
                  lineHeight: "1.5",
                  fontStyle: "italic",
                }}
              >
                "{analysis.originalText}"
              </p>
            </div>

            <div>
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  textTransform: "uppercase",
                  color: "#6b7280",
                  marginBottom: "4px",
                }}
              >
                Translation
              </div>
              <p
                style={{
                  fontSize: "15px",
                  color: "#111827",
                  lineHeight: "1.5",
                  fontWeight: "500",
                }}
              >
                "{analysis.translatedText}"
              </p>
            </div>
          </section>

          {/* Divider */}
          <hr
            style={{
              border: "none",
              borderTop: "1px solid #e5e7eb",
              margin: "24px 0",
            }}
          />

          {/* Section: What the visitor said */}
          <section style={{ marginBottom: "20px" }}>
            <h2
              style={{
                fontSize: "18px",
                fontWeight: "700",
                marginBottom: "16px",
                color: "#111827",
              }}
            >
              What the visitor said
            </h2>

            <div style={{ marginBottom: "16px" }}>
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  textTransform: "uppercase",
                  color: "#6b7280",
                  marginBottom: "4px",
                }}
              >
                Overall sentiment
              </div>
              <div
                style={{
                  fontSize: "16px",
                  fontWeight: "600",
                  color:
                    analysis.classification.overallSentiment === "positive"
                      ? "#15803d"
                      : analysis.classification.overallSentiment === "negative"
                      ? "#b91c1c"
                      : "#374151",
                }}
              >
                {formatOverallSentiment(analysis.classification)}
              </div>
            </div>

            <div>
              <div
                style={{
                  fontSize: "13px",
                  fontWeight: "600",
                  textTransform: "uppercase",
                  color: "#6b7280",
                  marginBottom: "8px",
                }}
              >
                Areas mentioned
              </div>

              {analysis.classification.aspects.length === 0 ? (
                <div
                  style={{
                    padding: "10px 12px",
                    backgroundColor: "#f9fafb",
                    border: "1px solid #e5e7eb",
                    borderRadius: "6px",
                    fontSize: "14px",
                    color: "#6b7280",
                  }}
                >
                  Uncertain — no specific areas could be identified with confidence.
                </div>
              ) : (
                <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                  {analysis.classification.aspects.map((aspect, idx) => (
                    <div
                      key={`${aspect.aspect}-${idx}`}
                      style={{
                        padding: "12px",
                        backgroundColor: "#ffffff",
                        border: "1px solid #e5e7eb",
                        borderRadius: "8px",
                      }}
                    >
                      <div
                        style={{
                          fontSize: "16px",
                          fontWeight: "700",
                          color: "#111827",
                          marginBottom: "4px",
                        }}
                      >
                        {formatAspectName(aspect.aspect)}
                      </div>
                      <div
                        style={{
                          fontSize: "14px",
                          color:
                            aspect.sentiment === "positive"
                              ? "#15803d"
                              : aspect.sentiment === "negative"
                              ? "#b91c1c"
                              : "#4b5563",
                          fontWeight: "500",
                          marginBottom: "2px",
                          textTransform: "capitalize",
                        }}
                      >
                        {aspect.sentiment}
                      </div>
                      <div
                        style={{
                          fontSize: "14px",
                          color:
                            aspect.severity === "high"
                              ? "#b91c1c"
                              : aspect.severity === "medium"
                              ? "#b45309"
                              : "#4b5563",
                          fontWeight: aspect.severity === "high" ? "600" : "400",
                          marginBottom: "2px",
                        }}
                      >
                        {aspect.severity.charAt(0).toUpperCase() + aspect.severity.slice(1)} priority
                      </div>
                      <div style={{ fontSize: "13px", color: "#6b7280" }}>
                        Confidence {aspect.confidence.toFixed(2)}
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </section>

          {/* Divider */}
          <hr
            style={{
              border: "none",
              borderTop: "1px solid #e5e7eb",
              margin: "24px 0",
            }}
          />

          {/* Section: Suggested improvement */}
          <section style={{ marginBottom: "20px" }}>
            <h2
              style={{
                fontSize: "18px",
                fontWeight: "700",
                marginBottom: "12px",
                color: "#111827",
              }}
            >
              Suggested improvement
            </h2>

            {analysis.recommendations.length > 0 ? (
              <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
                {analysis.recommendations.map((rec, idx) => (
                  <div
                    key={`${rec.aspect}-${idx}`}
                    style={{
                      padding: "12px 14px",
                      backgroundColor: "#f9fafb",
                      border: "1px solid #e5e7eb",
                      borderRadius: "8px",
                      fontSize: "15px",
                      lineHeight: "1.4",
                      color: "#111827",
                    }}
                  >
                    {formatImprovementAction(rec.action)}
                  </div>
                ))}
              </div>
            ) : (
              <p style={{ fontSize: "14px", color: "#6b7280" }}>
                No immediate improvements suggested based on this review.
              </p>
            )}
          </section>

          {/* Divider */}
          <hr
            style={{
              border: "none",
              borderTop: "1px solid #e5e7eb",
              margin: "24px 0",
            }}
          />

          {/* Section: Suggested action — Noor decides */}
          <section
            style={{
              padding: "16px",
              backgroundColor: "#f9fafb",
              border: "1px solid #e5e7eb",
              borderRadius: "8px",
              fontSize: "14px",
              color: "#374151",
            }}
          >
            <h3
              style={{
                fontSize: "15px",
                fontWeight: "700",
                color: "#111827",
                marginBottom: "8px",
              }}
            >
              Suggested action — Noor decides.
            </h3>
            <p style={{ marginBottom: "6px", color: "#4b5563" }}>
              The application will not automatically:
            </p>
            <ul
              style={{
                margin: "0 0 10px 0",
                paddingLeft: "20px",
                lineHeight: "1.6",
                color: "#4b5563",
              }}
            >
              <li>send messages</li>
              <li>change prices</li>
              <li>change bookings</li>
              <li>contact visitors</li>
              <li>modify business information</li>
            </ul>
            <p style={{ fontWeight: "600", color: "#111827" }}>
              Noor remains the decision maker.
            </p>
          </section>

          {/* Performance Diagnostics */}
          {result?.timings && (
            <div
              style={{
                marginTop: "14px",
                fontSize: "11px",
                color: "#9ca3af",
                textAlign: "center",
              }}
            >
              ⏱️ Detection: {result.timings.detectionMs}ms · Translation: {result.timings.translationMs}ms · Classifier: {result.timings.classificationMs}ms · Rules: {result.timings.recommendationMs}ms
            </div>
          )}
        </article>
      )}
    </main>
  );
}

export default App;