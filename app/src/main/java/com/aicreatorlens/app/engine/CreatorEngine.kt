package com.aicreatorlens.app.engine

/**
 * Creator Engine parameters.
 * All values are normalized 0.0-1.0 unless noted otherwise.
 * This is the single source of truth for the processing pipeline.
 */
data class CreatorEngine(
    // Tone Mapping
    val toneMappingStrength: Float = 0.4f,      // 0-1, how much ACES to apply
    val exposure: Float = 0.1f,                  // -2 to +2 stops

    // Color Science
    val contrastCurve: Float = 0.15f,            // -1 to 1, S-curve contrast
    val colorTemperature: Float = 0.55f,         // 0=cool, 1=warm
    val saturation: Float = 1.1f,                // 0-2

    // Dynamic Range
    val highlightRolloff: Float = 0.4f,          // 0-1, soft clip highlights
    val shadowRecovery: Float = 0.15f,           // 0-1, lift shadows

    // Face / Skin
    val skinProtection: Float = 0.7f,            // 0-1, how much to protect skin tones
    val skinSoftness: Float = 0.0f,              // 0-1, subtle smoothing

    // Background
    val backgroundContrast: Float = 0.1f,        // -1 to 1
    val backgroundSaturation: Float = 0.85f,     // 0-2

    // Cinematic Effects
    val filmGrain: Float = 0.15f,                // 0-1
    val glow: Float = 0.05f,                     // 0-1
    val sharpness: Float = 0.6f,                 // 0-1
    val vignette: Float = 0.15f                  // 0-1
)