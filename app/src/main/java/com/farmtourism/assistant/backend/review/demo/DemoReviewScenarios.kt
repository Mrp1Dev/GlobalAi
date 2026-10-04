package com.farmtourism.assistant.backend.review.demo

import com.farmtourism.assistant.backend.review.model.DemoReviewScenario
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity

/**
 * Pre-configured interactive demo scenarios ported from the hackathon benchmark dataset.
 *
 * Demonstrates the core World Bank hackathon value proposition:
 * International tourist writes in their native language (German, Spanish, French, English, Hindi)
 *    -> On-device Language Identification (<5ms)
 *    -> On-device Translation into Noor's language (Hindi)
 *    -> On-device Aspect, Polarity, and Severity Classification
 *    -> Deterministic operational suggestions for farm upgrade
 *    -> Noor remains in the loop as the final decision maker
 */
object DemoReviewScenarios {

    val SCENARIOS: List<DemoReviewScenario> = listOf(
        DemoReviewScenario(
            id = "demo-1-german-clean",
            title = "🇩🇪 German: Punctual & Clean Tour",
            category = "positive",
            visitorLanguageName = "German",
            visitorLanguageCode = "de",
            originalReview = "Die Tour über die Kaffeefarm war wunderbar organisiert und alles war sehr sauber.",
            expectedAspects = listOf("cleanliness", "experience"),
            expectedSentiment = Sentiment.POSITIVE,
            expectedSeverity = Severity.LOW,
            businessContext = "Noor does not need to speak German to understand that hygiene and tour pacing delighted the visitor."
        ),
        DemoReviewScenario(
            id = "demo-2-english-delay",
            title = "🇬🇧 English: 45-Min Delay & Disorganization",
            category = "negative_waiting",
            visitorLanguageName = "English",
            visitorLanguageCode = "en",
            originalReview = "We had to wait over 45 minutes past the start time before the tour even began. It was very disorganized.",
            expectedAspects = listOf("waiting_time"),
            expectedSentiment = Sentiment.NEGATIVE,
            expectedSeverity = Severity.HIGH,
            businessContext = "A critical waiting delay flagged as High Priority. Suggests an operational fix to send arrival reminders."
        ),
        DemoReviewScenario(
            id = "demo-3-spanish-mixed",
            title = "🇪🇸 Spanish: Delicious Coffee, Overpriced Ticket",
            category = "mixed",
            visitorLanguageName = "Spanish",
            visitorLanguageCode = "es",
            originalReview = "Me encantó el paseo por la finca y el café estaba delicioso, pero la entrada nos pareció demasiado cara para lo que incluye.",
            expectedAspects = listOf("pricing", "food", "experience"),
            expectedSentiment = Sentiment.NEGATIVE,
            expectedSeverity = Severity.MEDIUM,
            businessContext = "Visitor loved the tasting and walk but felt the price was high. Suggests highlighting tour inclusions upfront."
        ),
        DemoReviewScenario(
            id = "demo-4-french-directions",
            title = "🇫🇷 French: No Road Signs, Hard to Find",
            category = "directions_issue",
            visitorLanguageName = "French",
            visitorLanguageCode = "fr",
            originalReview = "La ferme est très difficile à trouver. Il n'y a aucun panneau sur la route principale et le chemin est mal indiqué.",
            expectedAspects = listOf("directions"),
            expectedSentiment = Sentiment.NEGATIVE,
            expectedSeverity = Severity.MEDIUM,
            businessContext = "Visitor got lost due to lack of signboards. Suggests putting up painted signs at the fork in the road."
        ),
        DemoReviewScenario(
            id = "demo-5-hindi-hospitality",
            title = "🇮🇳 Hindi: Warm Hospitality & Fresh Coffee",
            category = "positive_hospitality",
            visitorLanguageName = "Hindi",
            visitorLanguageCode = "hi",
            originalReview = "मेजबान बहुत ही मिलनसार थे और ताज़ी कॉफी का स्वाद वाकई लाजवाब था।",
            expectedAspects = listOf("hospitality", "food"),
            expectedSentiment = Sentiment.POSITIVE,
            expectedSeverity = Severity.LOW,
            businessContext = "Local visitor praising the warm personal welcome and coffee taste. Reinforces storytelling strengths."
        )
    )

    fun findById(id: String): DemoReviewScenario? = SCENARIOS.find { it.id == id }
}
