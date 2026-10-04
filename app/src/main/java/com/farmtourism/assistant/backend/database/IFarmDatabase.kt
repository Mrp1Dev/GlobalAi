package com.farmtourism.assistant.backend.database

import com.farmtourism.assistant.backend.model.IntentTemplate

/**
 * Interface for the local on-device database on Noor's phone.
 * Caches farm configuration slots, intent templates, and previously answered values.
 */
interface IFarmDatabase {

    /**
     * Retrieves the stored atomic value for a specific slot key (e.g. "tour_price_inr" -> "500").
     * Returns null if missing (triggering a Tier 2 DB miss).
     */
    suspend fun getSlot(slotKey: String): String?

    /**
     * Saves or updates a slot key-value pair in local storage.
     */
    suspend fun setSlot(slotKey: String, value: String)

    /**
     * Checks if a specific slot key is populated in the database.
     */
    suspend fun hasSlot(slotKey: String): Boolean

    /**
     * Returns all currently stored farm slot entries.
     */
    suspend fun getAllSlots(): Map<String, String>

    /**
     * Removes a slot entry from local storage.
     */
    suspend fun deleteSlot(slotKey: String)

    /**
     * Retrieves template metadata for a specific intent code.
     */
    suspend fun getTemplate(intent: String): IntentTemplate?

    /**
     * Saves or updates an intent template definition.
     */
    suspend fun saveTemplate(template: IntentTemplate)

    /**
     * Returns all registered intent templates.
     */
    suspend fun getAllTemplates(): List<IntentTemplate>

    /**
     * Clears all cached slots and resets database state.
     */
    suspend fun clear()
}
