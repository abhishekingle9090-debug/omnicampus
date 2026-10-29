package com.example.ai

/**
 * Centralized Gemini model configuration for OmniCampus AI.
 * Ensures consistent, supported model identifiers across speech transcription,
 * notes synthesis, flashcard generation, and API diagnostics.
 */
object AiModelConfig {
    /**
     * Primary multimodal model: gemini-3.8-flash
     * Verified in Google Generative Language API (models/gemini-3.8-flash).
     * Supports high-speed multimodal input (PCM WAV audio inline data, text, image)
     * and structured JSON generation.
     */
    /**
     * Primary multimodal model: gemini-3.8-flash
     * Verified in Google Generative Language API.
     */
    const val PRIMARY_MODEL = "gemini-3.8-flash"

    /**
     * Verified fast fallback model: gemini-3.5-flash
     * Supports high-speed multimodal audio WAV transcription and structured JSON generation.
     */
    const val FALLBACK_MODEL = "gemini-3.5-flash"

    /**
     * Additional stable fallback: gemini-3.7-flash
     */
    const val TERTIARY_MODEL = "gemini-3.7-flash"

    /**
     * Ordered list of supported models for automatic fallback.
     * Note: gemini-2.5-flash and gemini-1.5-flash are permanently retired by Google (HTTP 404).
     */
    val SUPPORTED_MODELS: List<String> = listOf(PRIMARY_MODEL, FALLBACK_MODEL, TERTIARY_MODEL)
}
