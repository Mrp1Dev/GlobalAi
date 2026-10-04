package com.farmtourism.assistant.backend.review.rules

import com.farmtourism.assistant.backend.review.classifier.ReviewAspectTaxonomy
import com.farmtourism.assistant.backend.review.model.Recommendation
import com.farmtourism.assistant.backend.review.model.ReviewClassification
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity

/**
 * Deterministic Recommendation Engine for Noor's Review System.
 *
 * Implements tourism-specific rule-based suggestions mapped to identified
 * review aspects and sentiments.
 *
 * Guarantees:
 * - 100% deterministic rules (Zero LLM hallucinations or cloud latency).
 * - Single-review scope (Actionable suggestions for Noor's operational choices).
 * - Priority-weighted ordering: High priority issues are prioritized first.
 * - Human-in-the-loop: Noor retains full decision-making agency.
 */
object RecommendationRuleEngine {

    const val GUARDRAIL_DISCLAIMER_EN =
        "Suggested action — Noor decides. The application will not automatically: " +
        "send messages, change prices, change bookings, contact visitors, or modify business information."

    const val GUARDRAIL_DISCLAIMER_HI =
        "सुझाया गया कदम — निर्णय नूर का होगा। यह ऐप अपने आप कोई संदेश नहीं भेजेगा, " +
        "मूल्य नहीं बदलेगा, बुकिंग में फेरबदल नहीं करेगा या किसी पर्यटक से संपर्क नहीं करेगा।"

    private data class RuleEntry(
        val actionEn: String,
        val actionHi: String
    )

    private val RULES: Map<String, Map<Sentiment, RuleEntry>> = mapOf(
        "waiting_time" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider improving arrival and start-time communication.",
                actionHi = "आगमन और यात्रा शुरू होने के समय की जानकारी पहले से स्पष्ट करें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider maintaining the current punctual tour schedule.",
                actionHi = "दौरे की वर्तमान समयबद्ध व्यवस्था और अनुशासन को जारी रखें।"
            )
        ),
        "directions" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider providing clearer directions and landmark guidance before visitor arrival.",
                actionHi = "पर्यटकों के आने से पहले मुख्य मोड़ और रास्ते पर स्पष्ट दिशा-निर्देश बोर्ड लगाएं।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider continuing to share the current clear route guidance with new visitors.",
                actionHi = "नए पर्यटकों के साथ वर्तमान स्पष्ट मार्ग-दर्शन साझा करना जारी रखें।"
            )
        ),
        "hospitality" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider greeting visitors personally at the start and setting aside time for questions.",
                actionHi = "पर्यटकों का व्यक्तिगत स्वागत करें और उनके सवालों के लिए पर्याप्त समय दें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider maintaining the current warm hospitality approach.",
                actionHi = "पर्यटकों के साथ वर्तमान आत्मीय और गर्मजोशी भरा व्यवहार बनाए रखें।"
            )
        ),
        "food" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider reviewing the food offering and clarifying dietary expectations before the visit.",
                actionHi = "खान-पान और ताज़ी सामग्री की गुणवत्ता व विकल्पों की समीक्षा करें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider keeping popular food and drink offerings as regular visit highlights.",
                actionHi = "ताज़ी कॉफ़ी और स्थानीय खान-पान को दौरे का मुख्य आकर्षण बनाए रखें।"
            )
        ),
        "cleanliness" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider reviewing cleaning and preparation of seating and restrooms before visitor arrival.",
                actionHi = "पर्यटकों के आने से पहले बैठने की जगह और शौचालयों की सफाई सुनिश्चित करें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider maintaining the current high standard of cleanliness and facilities.",
                actionHi = "फार्म पर वर्तमान उच्च स्तरीय स्वच्छता और सुविधाओं को बनाए रखें।"
            )
        ),
        "experience" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider reviewing the tour duration and pacing to identify any tiring or dull segments.",
                actionHi = "दौरे की अवधि और गति की समीक्षा करें ताकि पर्यटकों को थकान न हो।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider keeping and strengthening the parts of the experience visitors value most.",
                actionHi = "फार्म दौरे के उन अनुभवों को और मजबूत करें जिन्हें पर्यटक सबसे ज्यादा पसंद कर रहे हैं।"
            )
        ),
        "pricing" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider making pricing details and inclusions clearer before the visit.",
                actionHi = "दौरे में क्या-क्या शामिल है और शुल्क की जानकारी पहले से स्पष्ट रखें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider continuing to clearly communicate pricing and included tour benefits upfront.",
                actionHi = "दौरे के शुल्क और लाभों की अग्रिम व स्पष्ट जानकारी देते रहें।"
            )
        ),
        "communication" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider providing clearer visitor information and reminders before arrival.",
                actionHi = "आगमन से पहले पर्यटकों को स्पष्ट जानकारी और अनुस्मारक संदेश भेजें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider continuing proactive and helpful visitor communication.",
                actionHi = "पर्यटकों के साथ सक्रिय और मददगार संवाद बनाए रखें।"
            )
        ),
        "learning_authenticity" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider explaining daily farm practices in simpler terms to enhance authentic learning.",
                actionHi = "खेती की दैनिक प्रक्रियाओं को सरल भाषा में समझाएं ताकि पर्यटक बेहतर सीख सकें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider continuing to showcase authentic, hands-on farm activities as key highlights.",
                actionHi = "खेत की वास्तविक गतिविधियों और प्रत्यक्ष प्रदर्शन को मुख्य आकर्षण बनाए रखें।"
            )
        ),
        "activities" to mapOf(
            Sentiment.NEGATIVE to RuleEntry(
                actionEn = "Consider adjusting activity difficulty and pacing to ensure all visitors can comfortably participate.",
                actionHi = "गतिविधियों को इस तरह व्यवस्थित करें कि सभी पर्यटक आराम से भाग ले सकें।"
            ),
            Sentiment.POSITIVE to RuleEntry(
                actionEn = "Consider featuring the popular visitor activities prominently in tour descriptions.",
                actionHi = "लोकप्रिय गतिविधियों को अपने टूर विवरण में प्रमुखता से प्रदर्शित करें।"
            )
        )
    )

    fun generateRecommendations(
        classification: ReviewClassification?,
        minConfidence: Float = ReviewAspectTaxonomy.CONFIDENCE_THRESHOLD
    ): List<Recommendation> {
        if (classification == null || classification.aspects.isEmpty()) {
            return emptyList()
        }

        val recommendations = mutableListOf<Recommendation>()
        val seenAspects = mutableSetOf<String>()

        for (aspectItem in classification.aspects) {
            // Confidence threshold check
            if (aspectItem.confidence < minConfidence) continue

            // Only actionable on POSITIVE or NEGATIVE sentiments
            if (aspectItem.sentiment != Sentiment.POSITIVE && aspectItem.sentiment != Sentiment.NEGATIVE) {
                continue
            }

            val rawAspect = aspectItem.aspect.trim().lowercase()
            val normalizedAspect = ReviewAspectTaxonomy.ASPECT_TAXONOMY_MAP[rawAspect] ?: rawAspect
            val aspectRule = RULES[normalizedAspect] ?: continue

            val ruleEntry = aspectRule[aspectItem.sentiment] ?: continue

            if (seenAspects.contains(normalizedAspect)) continue
            seenAspects.add(normalizedAspect)

            // Priority: Negative aspects inherit severity; positive aspects represent reinforcement (LOW)
            val priority = if (aspectItem.sentiment == Sentiment.NEGATIVE) {
                aspectItem.severity
            } else {
                Severity.LOW
            }

            recommendations.add(
                Recommendation(
                    aspect = normalizedAspect,
                    priority = priority,
                    action = ruleEntry.actionEn,
                    localizedActionHindi = ruleEntry.actionHi
                )
            )
        }

        // Deterministic sorting: High > Medium > Low, secondary sort by aspect name
        return recommendations.sortedWith(
            compareByDescending<Recommendation> { it.priority.weight }
                .thenBy { it.aspect }
        )
    }
}
