export type Sentiment = "positive" | "neutral" | "negative";

export type Severity = "low" | "medium" | "high";

export interface ReviewAspect {
    aspect: string;
    sentiment: Sentiment;
    severity: Severity;
    confidence: number;
}

export interface ReviewClassification {
    overallSentiment: Sentiment;
    aspects: ReviewAspect[];
}

export interface Recommendation {
    aspect: string;
    priority: Severity;
    action: string;
}

export interface ReviewAnalysis {
    originalText: string;
    sourceLanguage: string;
    translatedText: string;
    classification: ReviewClassification;
    recommendations: Recommendation[];
}