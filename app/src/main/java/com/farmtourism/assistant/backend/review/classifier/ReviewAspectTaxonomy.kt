package com.farmtourism.assistant.backend.review.classifier

import com.farmtourism.assistant.backend.review.model.ReviewAspect
import com.farmtourism.assistant.backend.review.model.Sentiment
import com.farmtourism.assistant.backend.review.model.Severity

object ReviewAspectTaxonomy {

    const val CONFIDENCE_THRESHOLD = 0.55f
    const val HIGH_CONFIDENCE_THRESHOLD = 0.80f

    val LABELS = listOf(
        "guide_hospitality:pos",
        "guide_hospitality:neg",
        "experience_activity:pos",
        "experience_activity:neg",
        "food_drink:pos",
        "food_drink:neg",
        "learning_authenticity:pos",
        "learning_authenticity:neg",
        "price_value:pos",
        "price_value:neg",
        "timing_waiting:pos",
        "timing_waiting:neg",
        "directions_access:pos",
        "directions_access:neg",
        "facilities_cleanliness:pos",
        "facilities_cleanliness:neg",
        "would_recommend_or_return",
        "wish_or_suggestion"
    )

    val LABEL_DESCRIPTIONS = mapOf(
        "guide_hospitality:pos" to "Guests praise the friendly, welcoming, helpful guide or host.",
        "guide_hospitality:neg" to "Guests complain the guide or host was rude, unwelcoming, unhelpful or unfriendly.",
        "experience_activity:pos" to "Guests enjoyed the activities, the farm walk and the overall experience.",
        "experience_activity:neg" to "Guests found the activities boring, too long, too tiring or disappointing.",
        "food_drink:pos" to "Guests liked the food, drinks, tasting or fresh farm produce.",
        "food_drink:neg" to "Guests complained the food or drink was bad, cold, bland or unsafe.",
        "learning_authenticity:pos" to "Guests learned a lot and felt the farm experience was authentic and genuine.",
        "learning_authenticity:neg" to "Guests learned little or felt the experience was staged, fake or not authentic.",
        "price_value:pos" to "Guests felt the price was fair and the tour was good value for money.",
        "price_value:neg" to "Guests felt the tour was overpriced, expensive or poor value for money.",
        "timing_waiting:pos" to "The tour started on time and was well organised.",
        "timing_waiting:neg" to "Guests had to wait a long time, or the tour started late or was disorganised.",
        "directions_access:pos" to "The farm was easy to find and reach.",
        "directions_access:neg" to "The farm was hard to find, the directions were unclear or the road was difficult.",
        "facilities_cleanliness:pos" to "The place was clean and the facilities such as toilets and seating were good.",
        "facilities_cleanliness:neg" to "The place was dirty or the facilities such as toilets and seating were poor.",
        "would_recommend_or_return" to "Guests say they would recommend the tour to others or come back again.",
        "wish_or_suggestion" to "Guests wish for or suggest something extra, such as more activities or a new offering."
    )

    val ASPECT_TAXONOMY_MAP = mapOf(
        "timing_waiting" to "waiting_time",
        "directions_access" to "directions",
        "guide_hospitality" to "hospitality",
        "food_drink" to "food",
        "facilities_cleanliness" to "cleanliness",
        "experience_activity" to "experience",
        "price_value" to "pricing",
        "learning_authenticity" to "learning_authenticity",
        "would_recommend_or_return" to "experience",
        "wish_or_suggestion" to "activities"
    )

    val HIGH_CUES = listOf(
        // English
        "terrible", "worst", "awful", "horrible", "disgusting", "rude", "never again",
        "waste of", "scam", "unsafe", "filthy", "hours", "refund", "delayed", "disorganized",
        "unorganized", "waited", "45 minutes", "hour",
        // Hindi Devanagari
        "बहुत बुरा", "बेकार", "गंदा", "घटिया", "ठगी", "कभी नहीं", "बकवास", "खराब", "देर",
        // Roman Hindi
        "bakwas", "bekaar", "ganda", "ghatiya", "kharab",
        // Spanish
        "terrible", "pésimo", "estafa", "inseguro", "caro", "sucio", "demasiado cara",
        // French
        "terrible", "horrible", "arnaque", "sale", "cher", "mal indiqué",
        // German
        "schrecklich", "furchtbar", "dreckig", "abzocke", "teuer"
    )

    fun looksMeaningless(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 4) return true
        val words = trimmed.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (words.size < 2) return true
        val lettersCount = trimmed.count { it.isLetter() }
        return lettersCount < 4
    }

    fun evaluateSeverity(
        text: String,
        aspects: List<ReviewAspect>
    ): Severity {
        val hasNegativeAspect = aspects.any {
            it.sentiment == Sentiment.NEGATIVE && it.confidence >= CONFIDENCE_THRESHOLD
        }

        if (!hasNegativeAspect) {
            return Severity.LOW
        }

        val lower = text.lowercase()
        val hasHighCue = HIGH_CUES.any { cue -> lower.contains(cue.lowercase()) }
        return if (hasHighCue) Severity.HIGH else Severity.MEDIUM
    }
}
