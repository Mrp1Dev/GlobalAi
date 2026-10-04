package com.farmtourism.assistant.backend.model

/**
 * Host farmer configuration.
 *
 * @param name Farmer's display name (e.g., "Noor").
 * @param language BCP-47 tag for farmer's native tongue (defaults to "hi" for Hindi).
 * @param farmName Name of the agricultural property / experience.
 * @param location Description of the location (e.g., "Himachal Organic Orchards").
 */
data class FarmerProfile(
    val name: String = "Noor",
    val language: String = "hi",
    val farmName: String = "Noor's Himalayan Agro-Farm",
    val location: String = "Himachal Pradesh, India"
)
