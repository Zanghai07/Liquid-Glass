package com.zanghai.liquidglass.lib

/**
 * Data representing a single ripple wave on the liquid glass surface.
 *
 * @property centerX X center of the ripple in UV space (0.0 – 1.0)
 * @property centerY Y center of the ripple in UV space (0.0 – 1.0)
 * @property startTimeMs System time in milliseconds when the ripple was created
 * @property amplitude Initial wave amplitude
 */
internal data class RippleData(
    val centerX: Float,
    val centerY: Float,
    val startTimeMs: Long,
    val amplitude: Float
)

/**
 * Simulates touch-driven ripple physics on the liquid glass surface.
 *
 * Maintains up to [MAX_RIPPLES] simultaneous ripples. Each ripple follows
 * damped wave physics:
 *
 *     displacement = A · sin(k·r − ω·t) · e^(-γ_t · t) · e^(-γ_r · r)
 *
 * where:
 * - A = initial amplitude
 * - k = spatial frequency
 * - r = distance from center
 * - ω = angular frequency
 * - t = time since creation
 * - γ_t = temporal decay coefficient
 * - γ_r = spatial decay coefficient
 */
internal class PhysicsSimulator(
    private var rippleAmplitude: Float = 0.04f,
    private var rippleDuration: Float = 2.0f  // seconds
) {
    companion object {
        const val MAX_RIPPLES = 5
    }

    private val ripples = mutableListOf<RippleData>()

    /**
     * Add a new ripple at the given UV-space position.
     * If the maximum number of ripples is reached, the oldest one is replaced.
     *
     * @param uvX X coordinate in UV space (0.0 – 1.0)
     * @param uvY Y coordinate in UV space (0.0 – 1.0)
     */
    @Synchronized
    fun addRipple(uvX: Float, uvY: Float) {
        if (ripples.size >= MAX_RIPPLES) {
            ripples.removeAt(0) // Remove oldest
        }
        ripples.add(
            RippleData(
                centerX = uvX.coerceIn(0f, 1f),
                centerY = uvY.coerceIn(0f, 1f),
                startTimeMs = System.currentTimeMillis(),
                amplitude = rippleAmplitude
            )
        )
    }

    /**
     * Remove expired ripples (those older than [rippleDuration]).
     */
    @Synchronized
    fun update() {
        val now = System.currentTimeMillis()
        val durationMs = (rippleDuration * 1000).toLong()
        ripples.removeAll { (now - it.startTimeMs) > durationMs }
    }

    /**
     * Get a snapshot of currently active ripples.
     */
    @Synchronized
    fun getActiveRipples(): List<RippleData> = ripples.toList()

    /**
     * Get the number of currently active ripples.
     */
    @Synchronized
    fun getActiveCount(): Int = ripples.size

    /**
     * Get the elapsed time in seconds for a ripple.
     */
    fun getElapsedSeconds(ripple: RippleData): Float {
        return (System.currentTimeMillis() - ripple.startTimeMs) / 1000f
    }

    /**
     * Update configuration.
     */
    fun updateConfig(amplitude: Float, duration: Float) {
        rippleAmplitude = amplitude
        rippleDuration = duration
    }

    /**
     * Clear all active ripples.
     */
    @Synchronized
    fun clear() {
        ripples.clear()
    }
}
