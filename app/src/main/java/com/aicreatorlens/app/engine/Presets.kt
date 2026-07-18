package com.aicreatorlens.app.engine

object Presets {
    val CREATOR = CreatorEngine(
        toneMappingStrength = 0.4f,
        exposure = 0.1f,
        contrastCurve = 0.15f,
        colorTemperature = 0.55f,
        saturation = 1.1f,
        highlightRolloff = 0.4f,
        shadowRecovery = 0.15f,
        skinProtection = 0.7f,
        skinSoftness = 0.0f,
        backgroundContrast = 0.1f,
        backgroundSaturation = 0.85f,
        filmGrain = 0.15f,
        glow = 0.05f,
        sharpness = 0.6f,
        vignette = 0.15f
    )

    val CINEMATIC = CreatorEngine(
        toneMappingStrength = 0.6f,
        exposure = 0.0f,
        contrastCurve = 0.25f,
        colorTemperature = 0.45f,
        saturation = 0.95f,
        highlightRolloff = 0.6f,
        shadowRecovery = 0.1f,
        skinProtection = 0.6f,
        skinSoftness = 0.0f,
        backgroundContrast = 0.2f,
        backgroundSaturation = 0.8f,
        filmGrain = 0.25f,
        glow = 0.1f,
        sharpness = 0.4f,
        vignette = 0.3f
    )

    val GOLDEN_HOUR = CreatorEngine(
        toneMappingStrength = 0.3f,
        exposure = 0.15f,
        contrastCurve = 0.1f,
        colorTemperature = 0.7f,
        saturation = 1.2f,
        highlightRolloff = 0.5f,
        shadowRecovery = 0.2f,
        skinProtection = 0.8f,
        skinSoftness = 0.0f,
        backgroundContrast = 0.0f,
        backgroundSaturation = 1.1f,
        filmGrain = 0.1f,
        glow = 0.15f,
        sharpness = 0.5f,
        vignette = 0.2f
    )

    val DARK_MOOD = CreatorEngine(
        toneMappingStrength = 0.5f,
        exposure = -0.1f,
        contrastCurve = 0.3f,
        colorTemperature = 0.35f,
        saturation = 0.8f,
        highlightRolloff = 0.7f,
        shadowRecovery = 0.05f,
        skinProtection = 0.5f,
        skinSoftness = 0.0f,
        backgroundContrast = 0.3f,
        backgroundSaturation = 0.7f,
        filmGrain = 0.2f,
        glow = 0.0f,
        sharpness = 0.5f,
        vignette = 0.4f
    )

    val NONE = CreatorEngine(
        toneMappingStrength = 0.0f,
        exposure = 0.0f,
        contrastCurve = 0.0f,
        colorTemperature = 0.5f,
        saturation = 1.0f,
        highlightRolloff = 0.0f,
        shadowRecovery = 0.0f,
        skinProtection = 0.0f,
        skinSoftness = 0.0f,
        backgroundContrast = 0.0f,
        backgroundSaturation = 1.0f,
        filmGrain = 0.0f,
        glow = 0.0f,
        sharpness = 0.5f,
        vignette = 0.0f
    )

    val ALL = listOf(
        "None" to NONE,
        "Creator" to CREATOR,
        "Cinematic" to CINEMATIC,
        "Golden Hour" to GOLDEN_HOUR,
        "Dark Mood" to DARK_MOOD
    )
}