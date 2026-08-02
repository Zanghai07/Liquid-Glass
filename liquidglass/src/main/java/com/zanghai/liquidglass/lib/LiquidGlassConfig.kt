package com.zanghai.liquidglass.lib

import android.graphics.Color

/**
 * Configuration for [LiquidGlassView].
 *
 * Controls all physical and visual properties of the liquid glass effect.
 * Use [Builder] for fluent construction or one of the presets like [WATER], [CRYSTAL], etc.
 *
 * Example:
 * ```kotlin
 * val config = LiquidGlassConfig.Builder()
 *     .indexOfRefraction(1.5f)
 *     .chromaticAberration(0.02f)
 *     .liquidSpeed(0.4f)
 *     .build()
 * ```
 */
data class LiquidGlassConfig(
    /** Index of refraction (1.0 = air/no refraction, 1.33 = water, 1.5 = glass, 2.42 = diamond) */
    val indexOfRefraction: Float = 1.45f,

    /** Strength of RGB channel separation — simulates light dispersion (0.0 = none) */
    val chromaticAberration: Float = 0.015f,

    /** Exponent for Fresnel edge reflection (higher = sharper edge effect) */
    val fresnelPower: Float = 3.0f,

    /** Specular highlight sharpness (Blinn-Phong exponent) */
    val shininess: Float = 64.0f,

    /** Specular highlight brightness multiplier */
    val specularIntensity: Float = 0.8f,

    /** Speed of procedural liquid surface animation */
    val liquidSpeed: Float = 0.3f,

    /** Scale/frequency of the liquid noise pattern */
    val liquidScale: Float = 2.5f,

    /** Strength of liquid surface distortion (how "wavy" the glass is) */
    val liquidAmplitude: Float = 0.025f,

    /** Color of the edge glow effect (ARGB integer) */
    val edgeGlowColor: Int = Color.WHITE,

    /** Strength of edge glow (0.0 = none) */
    val edgeGlowIntensity: Float = 0.5f,

    /** Glass tint color (ARGB integer) */
    val tintColor: Int = Color.TRANSPARENT,

    /** Strength of color tinting (0.0 = clear glass, 1.0 = fully tinted) */
    val tintIntensity: Float = 0.0f,

    /** Background blur radius in pixels */
    val blurRadius: Float = 8.0f,

    /** Strength of touch ripple effect */
    val rippleAmplitude: Float = 0.04f,

    /** Duration of touch ripple animation in seconds */
    val rippleDuration: Float = 2.0f,

    /** Corner radius in dp */
    val cornerRadius: Float = 28.0f,

    /** Background capture downscale factor for performance (0.1 – 1.0) */
    val captureDownscale: Float = 0.5f,

    /** Background capture rate in frames per second */
    val captureFrameRate: Int = 30,

    /** Enable gyroscope/accelerometer for dynamic lighting */
    val sensorEnabled: Boolean = true,

    /** Sensitivity of sensor-based lighting direction */
    val sensorSensitivity: Float = 1.0f
) {
    /**
     * Builder for constructing [LiquidGlassConfig] instances fluently.
     */
    class Builder {
        private var config = LiquidGlassConfig()

        fun indexOfRefraction(ior: Float) = apply { config = config.copy(indexOfRefraction = ior) }
        fun chromaticAberration(strength: Float) = apply { config = config.copy(chromaticAberration = strength) }
        fun fresnelPower(power: Float) = apply { config = config.copy(fresnelPower = power) }
        fun shininess(shininess: Float) = apply { config = config.copy(shininess = shininess) }
        fun specularIntensity(intensity: Float) = apply { config = config.copy(specularIntensity = intensity) }
        fun liquidSpeed(speed: Float) = apply { config = config.copy(liquidSpeed = speed) }
        fun liquidScale(scale: Float) = apply { config = config.copy(liquidScale = scale) }
        fun liquidAmplitude(amplitude: Float) = apply { config = config.copy(liquidAmplitude = amplitude) }
        fun edgeGlowColor(color: Int) = apply { config = config.copy(edgeGlowColor = color) }
        fun edgeGlowIntensity(intensity: Float) = apply { config = config.copy(edgeGlowIntensity = intensity) }
        fun tintColor(color: Int) = apply { config = config.copy(tintColor = color) }
        fun tintIntensity(intensity: Float) = apply { config = config.copy(tintIntensity = intensity) }
        fun blurRadius(radius: Float) = apply { config = config.copy(blurRadius = radius) }
        fun rippleAmplitude(amplitude: Float) = apply { config = config.copy(rippleAmplitude = amplitude) }
        fun rippleDuration(duration: Float) = apply { config = config.copy(rippleDuration = duration) }
        fun cornerRadius(radius: Float) = apply { config = config.copy(cornerRadius = radius) }
        fun captureDownscale(scale: Float) = apply { config = config.copy(captureDownscale = scale.coerceIn(0.1f, 1.0f)) }
        fun captureFrameRate(fps: Int) = apply { config = config.copy(captureFrameRate = fps.coerceIn(1, 60)) }
        fun sensorEnabled(enabled: Boolean) = apply { config = config.copy(sensorEnabled = enabled) }
        fun sensorSensitivity(sensitivity: Float) = apply { config = config.copy(sensorSensitivity = sensitivity) }

        fun build() = config
    }

    companion object {
        /** Clear glass with subtle refraction and minimal distortion */
        val CLEAR_GLASS = LiquidGlassConfig(
            indexOfRefraction = 1.3f,
            chromaticAberration = 0.005f,
            liquidAmplitude = 0.01f,
            blurRadius = 4.0f,
            tintIntensity = 0.0f
        )

        /** Water-like effect with blue tint and strong liquid animation */
        val WATER = LiquidGlassConfig(
            indexOfRefraction = 1.33f,
            chromaticAberration = 0.01f,
            liquidSpeed = 0.5f,
            liquidAmplitude = 0.04f,
            liquidScale = 3.0f,
            tintColor = 0x220088FF.toInt(),
            tintIntensity = 0.15f,
            blurRadius = 2.0f
        )

        /** Crystal/diamond-like with strong dispersion and high specular */
        val CRYSTAL = LiquidGlassConfig(
            indexOfRefraction = 2.0f,
            chromaticAberration = 0.04f,
            fresnelPower = 5.0f,
            shininess = 128.0f,
            specularIntensity = 1.2f,
            liquidAmplitude = 0.005f,
            liquidSpeed = 0.1f
        )

        /** Frosted/matte glass with heavy blur */
        val FROSTED = LiquidGlassConfig(
            indexOfRefraction = 1.2f,
            chromaticAberration = 0.008f,
            blurRadius = 24.0f,
            liquidAmplitude = 0.008f,
            tintColor = Color.WHITE,
            tintIntensity = 0.08f
        )

        /** iOS-style liquid glass — balanced refraction, subtle animation */
        val IOS_STYLE = LiquidGlassConfig(
            indexOfRefraction = 1.4f,
            chromaticAberration = 0.012f,
            fresnelPower = 3.5f,
            blurRadius = 12.0f,
            liquidSpeed = 0.25f,
            liquidAmplitude = 0.018f,
            edgeGlowIntensity = 0.6f,
            tintColor = 0x15FFFFFF.toInt(),
            tintIntensity = 0.05f,
            cornerRadius = 24.0f
        )
    }
}
