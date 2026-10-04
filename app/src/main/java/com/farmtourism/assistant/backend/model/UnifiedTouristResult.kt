package com.farmtourism.assistant.backend.model

import com.farmtourism.assistant.backend.pipeline.Tier1FastPathResult
import com.farmtourism.assistant.backend.pipeline.Tier2PromptRequest
import com.farmtourism.assistant.backend.pipeline.TouristTurnResult

/**
 * Result sealed hierarchy for unified tourist message processing across all 3 tiers:
 * - Tier 1: Automated Fast-Path via local database hit.
 * - Tier 2: Template-Guided Human-in-the-Loop when database slot is missing.
 * - Tier 3: Direct Two-Way Translation Fallback for out-of-scope or complex queries.
 */
sealed class UnifiedTouristResult {

    /**
     * Tier 1 Hit: Sub-100ms automated response directly served from local on-device database.
     * 0 human intervention needed from the host farmer.
     */
    data class Tier1Hit(
        val result: Tier1FastPathResult
    ) : UnifiedTouristResult()

    /**
     * Tier 2 Prompt Required: Intent was recognized with high confidence, but database
     * lacks the specific slot. Generates a structured prompt in Noor's native language.
     */
    data class Tier2PromptRequired(
        val promptRequest: Tier2PromptRequest
    ) : UnifiedTouristResult()

    /**
     * Tier 3 Fallback: Low confidence or out-of-scope query translated directly to Noor's native language.
     */
    data class Tier3Fallback(
        val result: TouristTurnResult
    ) : UnifiedTouristResult()
}
