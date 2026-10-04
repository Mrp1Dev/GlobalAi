package com.farmtourism.assistant.backend.review.classifier

import com.farmtourism.assistant.backend.review.model.ReviewAspect
import com.farmtourism.assistant.backend.review.model.ReviewClassification
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * On-Device Aspect and Sentiment Classifier for Visitor Reviews.
 *
 * Implements the 18-label review taxonomy, confidence scoring, severity detection,
 * and overall review sentiment derivation without requiring a cloud API or external server.
 */
class ReviewAspectClassifier(
    private val confidenceThreshold: Float = ReviewAspectTaxonomy.CONFIDENCE_THRESHOLD
) : IReviewClassifier {

    private data class AspectRule(
        val aspect: String,
        val posCues: List<String>,
        val negCues: List<String>
    )

    private val aspectRules = listOf(
        AspectRule(
            aspect = "waiting_time",
            posCues = listOf(
                "on time", "punctual", "well organised", "well organized", "prompt",
                "organisiert", "a tiempo", "puntual", "à l'heure", "bien organisé",
                "समय पर", "समयबद्ध", "समय का पाबंद"
            ),
            negCues = listOf(
                "wait", "waited", "waiting", "delayed", "delay", "late", "disorganized",
                "disorganised", "45 minutes", "hour", "hours", "too long",
                "warten", "warteten", "verspätet", "desorganisiert",
                "esperar", "esperamos", "tarde", "retraso", "desorganizado",
                "attendre", "attendu", "retard", "désorganisé", "trop long",
                "इंतजार", "देरी", "देर", "प्रतीक्षा", "विलंब", "अव्यवस्थित"
            )
        ),
        AspectRule(
            aspect = "cleanliness",
            posCues = listOf(
                "clean", "cleanliness", "hygiene", "tidy", "spotless", "neat",
                "sauber", "sauberkeit", "propre", "propreté", "limpio", "limpieza",
                "साफ", "स्वच्छ", "सफाई", "सुव्यवस्थित"
            ),
            negCues = listOf(
                "dirty", "filthy", "smell", "unclean", "messy", "trash",
                "dreckig", "schmutzig", "sale", "malpropre", "sucio", "asqueroso",
                "गंदा", "गंदगी", "बदबू", "कचरा", "अस्वच्छ"
            )
        ),
        AspectRule(
            aspect = "hospitality",
            posCues = listOf(
                "friendly", "welcoming", "host", "hospitality", "warm", "kind", "helpful",
                "gastfreundlich", "herzlich", "accueillant", "hôte", "chaleureux", "gentil",
                "amable", "acogedor", "anfitrión", "cálido",
                "मिलनसार", "मेजबान", "स्वागत", "सद्भाव", "दयालु", "मददगार"
            ),
            negCues = listOf(
                "rude", "unfriendly", "unwelcoming", "unhelpful", "hostile", "impolite",
                "unfreundlich", "unhöflich", "désagréable", "malpoli", "grosero", "antipático",
                "अशिष्ट", "रूखा", "बदतमीज़", "खराब व्यवहार"
            )
        ),
        AspectRule(
            aspect = "food",
            posCues = listOf(
                "coffee", "tasting", "delicious", "fresh", "produce", "food", "tea",
                "snack", "tasty", "flavor", "flavour", "drink", "cupping",
                "lecker", "kaffee", "kostprobe", "frisch",
                "délicieux", "café", "dégustation", "frais", "nourriture",
                "delicioso", "café", "degustación", "fresco", "comida", "rico",
                "ताज़ी कॉफी", "कॉफ़ी", "स्वाद", "लाजवाब", "स्वादिष्ट", "चाय", "खाना", "ताजा"
            ),
            negCues = listOf(
                "bad food", "cold food", "bland", "taste bad", "stale", "unsafe", "sour",
                "schlechtes essen", "ungenießbar", "mauvaise nourriture", "fade", "comida mala", "desabrida",
                "खराब खाना", "बेस्वाद", "बासी"
            )
        ),
        AspectRule(
            aspect = "directions",
            posCues = listOf(
                "easy to find", "clear directions", "signpost", "good signs", "well marked",
                "gut ausgeschildert", "leicht zu finden", "facile à trouver", "bien indiqué",
                "fácil de encontrar", "bien señalizado",
                "आसानी से मिला", "रास्ता साफ", "बोर्ड लगा हुआ"
            ),
            negCues = listOf(
                "difficult to find", "hard to find", "lost", "no signs", "no sign",
                "road", "path", "turnoff", "unclear directions", "bad directions",
                "schwer zu finden", "kein schild", "verirrt",
                "difficile à trouver", "aucun panneau", "mal indiqué", "chemin",
                "difícil de encontrar", "sin señal", "camino malo", "perdido",
                "रास्ता नहीं मिला", "भटक गए", "कोई बोर्ड नहीं", "खराब रास्ता", "ढूंढना मुश्किल"
            )
        ),
        AspectRule(
            aspect = "experience",
            posCues = listOf(
                "loved", "enjoyed", "great tour", "wonderful", "farm walk", "highlight",
                "amazing", "fantastic", "recommend", "come back", "visit again",
                "wunderbar", "toll", "fantastisch", "empfehlen",
                "adoré", "merveilleux", "magnifique", "promenade", "visite", "recommande",
                "encantó", "maravilloso", "finca", "paseo", "recomiendo",
                "शानदार", "आनंद", "बहुत अच्छा", "पसंद आया", "दोबारा आएंगे"
            ),
            negCues = listOf(
                "boring", "tiring", "disappointing", "waste of time", "dull", "regret",
                "langweilig", "enttäuschend", "zeitverschwendung",
                "ennuyeux", "décevant", "perte de temps", "fatigant",
                "aburrido", "decepcionante", "pérdida de tiempo", "cansado",
                "उबाऊ", "निराशाजनक", "समय की बर्बादी", "थकाऊ"
            )
        ),
        AspectRule(
            aspect = "pricing",
            posCues = listOf(
                "fair price", "good value", "worth it", "reasonable", "affordable", "cheap",
                "preiswert", "gutes preis", "lohnend", "bon prix", "rentable", "buen precio", "económico",
                "उचित मूल्य", "सही दाम", "पैसा वसूल", "किफायती"
            ),
            negCues = listOf(
                "expensive", "overpriced", "too expensive", "pricey", "waste of money",
                "costly", "not worth",
                "teuer", "überteuert", "zu teuer", "geldverschwendung",
                "cher", "trop cher", "hors de prix", "pas rentable",
                "caro", "demasiado cara", "demasiado caro", "costoso",
                "महंगा", "ज्यादा कीमत", "पैसा बर्बाद", "अत्यधिक शुल्क"
            )
        ),
        AspectRule(
            aspect = "learning_authenticity",
            posCues = listOf(
                "learned", "learning", "authentic", "genuine", "educational", "informative", "hands-on",
                "authentisch", "echt", "lehrreich", "authentique", "éducatif", "auténtico", "educativo",
                "सीखने को मिला", "सच्चा", "प्रामाणिक", "जानकारीपूर्ण"
            ),
            negCues = listOf(
                "staged", "fake", "artificial", "learned nothing", "unauthentic",
                "gestellt", "künstlich", "faux", "superficiel", "falso", "artificial",
                "नकली", "दिखावा", "कुछ नहीं सीखा"
            )
        ),
        AspectRule(
            aspect = "activities",
            posCues = listOf(
                "activity", "activities", "fruit picking", "cow milking", "milking", "harvest",
                "workshop", "hands on", "tours", "walk",
                "aktivität", "aktivitäten", "cueillette", "animaux", "actividades",
                "काम", "गतिविधियां", "फल तोड़ना", "दूध निकालना"
            ),
            negCues = listOf(
                "few activities", "nothing to do", "lack of activities", "boring activities",
                "wenig zu tun", "peu d'activités", "pocas actividades",
                "कम गतिविधियां", "करने को कुछ नहीं"
            )
        )
    )

    override suspend fun classifyReview(
        text: String,
        translatedHindiText: String?
    ): Result<ReviewClassification> = withContext(Dispatchers.Default) {
        runCatching {
            val trimmed = text.trim()

            // 1. Meaningless or empty check
            if (trimmed.isBlank() || ReviewAspectTaxonomy.looksMeaningless(trimmed)) {
                return@runCatching ReviewClassification(
                    overallSentiment = Sentiment.NEUTRAL,
                    aspects = emptyList(),
                    reviewSeverity = Severity.LOW
                )
            }

            // Combine original text and translated Hindi text for comprehensive multi-lingual feature extraction
            val combinedText = if (!translatedHindiText.isNullOrBlank()) {
                "$trimmed $translatedHindiText".lowercase()
            } else {
                trimmed.lowercase()
            }

            val detectedAspects = mutableListOf<ReviewAspect>()

            // 2. Evaluate each domain aspect
            for (rule in aspectRules) {
                var posScore = 0
                var negScore = 0

                for (cue in rule.posCues) {
                    val cueLower = cue.lowercase()
                    if (combinedText.contains(cueLower)) {
                        posScore += if (combinedText.contains(" $cueLower ") || combinedText.startsWith("$cueLower ") || combinedText.endsWith(" $cueLower")) 2 else 1
                    }
                }

                for (cue in rule.negCues) {
                    val cueLower = cue.lowercase()
                    if (combinedText.contains(cueLower)) {
                        negScore += if (combinedText.contains(" $cueLower ") || combinedText.startsWith("$cueLower ") || combinedText.endsWith(" $cueLower")) 2 else 1
                    }
                }

                if (posScore == 0 && negScore == 0) {
                    continue
                }

                val sentiment = when {
                    negScore > 0 && posScore == 0 -> Sentiment.NEGATIVE
                    posScore > 0 && negScore == 0 -> Sentiment.POSITIVE
                    negScore >= posScore -> Sentiment.NEGATIVE
                    else -> Sentiment.POSITIVE
                }

                val totalScore = posScore + negScore
                val rawConfidence = 0.55f + 0.15f * totalScore.coerceAtMost(3)
                val confidence = rawConfidence.coerceIn(0.55f, 0.98f)

                if (confidence >= confidenceThreshold) {
                    detectedAspects.add(
                        ReviewAspect(
                            aspect = rule.aspect,
                            sentiment = sentiment,
                            confidence = (confidence * 100).toInt() / 100f
                        )
                    )
                }
            }

            // 3. Resolve review-level severity using HIGH_CUES
            val reviewSeverity = ReviewAspectTaxonomy.evaluateSeverity(combinedText, detectedAspects)

            // 4. Update individual aspect severities
            val finalizedAspects = detectedAspects.map { aspect ->
                val aspectSeverity = if (aspect.sentiment == Sentiment.NEGATIVE) {
                    reviewSeverity
                } else {
                    Severity.LOW
                }
                aspect.copy(severity = aspectSeverity)
            }

            // 5. Derive overall review sentiment
            val overallSentiment = deriveOverallSentiment(finalizedAspects, reviewSeverity)

            ReviewClassification(
                overallSentiment = overallSentiment,
                aspects = finalizedAspects,
                reviewSeverity = reviewSeverity
            )
        }
    }

    private fun deriveOverallSentiment(
        aspects: List<ReviewAspect>,
        reviewSeverity: Severity
    ): Sentiment {
        if (aspects.isEmpty()) return Sentiment.NEUTRAL

        val posAspects = aspects.filter { it.sentiment == Sentiment.POSITIVE }
        val negAspects = aspects.filter { it.sentiment == Sentiment.NEGATIVE }

        if (negAspects.isEmpty() && posAspects.isNotEmpty()) return Sentiment.POSITIVE
        if (posAspects.isEmpty() && negAspects.isNotEmpty()) return Sentiment.NEGATIVE

        // Both positive and negative aspects present
        if (reviewSeverity == Severity.HIGH) {
            return Sentiment.NEGATIVE
        }

        val posConfidenceSum = posAspects.sumOf { it.confidence.toDouble() }
        val negConfidenceSum = negAspects.sumOf { it.confidence.toDouble() }

        return when {
            negConfidenceSum > posConfidenceSum -> Sentiment.NEGATIVE
            posConfidenceSum > negConfidenceSum -> Sentiment.POSITIVE
            else -> Sentiment.NEUTRAL
        }
    }
}
