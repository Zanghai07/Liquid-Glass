package com.zanghai.liquidglass.lib

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log

/**
 * Tracks device orientation using rotation vector sensor (or accelerometer + magnetometer fallback)
 * and provides a smoothly-interpolated light direction vector for dynamic specular highlights.
 *
 * The light direction changes as the user tilts their device, creating a living, responsive
 * glass effect where highlights glide across the surface.
 */
internal class SensorTracker(
    context: Context,
    private var sensitivity: Float = 1.0f
) : SensorEventListener {

    companion object {
        private const val TAG = "LiquidGlass:Sensor"
        private const val SMOOTHING_FACTOR = 0.15f // Exponential moving average alpha
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3) // azimuth, pitch, roll

    // Fallback sensor data
    private var accelerometerData: FloatArray? = null
    private var magnetometerData: FloatArray? = null

    // Smoothed light direction (vec3), exposed for shader uniform
    private val _lightDirection = floatArrayOf(0.3f, 0.5f, 0.8f) // Default: slightly above-right
    val lightDirection: FloatArray get() = _lightDirection.copyOf()

    private var isRunning = false
    private var useRotationVector = true

    /**
     * Start listening for sensor events.
     */
    fun start() {
        if (isRunning) return
        isRunning = true

        if (rotationSensor != null) {
            useRotationVector = true
            sensorManager.registerListener(
                this, rotationSensor,
                SensorManager.SENSOR_DELAY_GAME
            )
        } else {
            // Fallback to accelerometer + magnetometer
            useRotationVector = false
            accelerometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            magnetometer?.let {
                sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
            if (accelerometer == null && magnetometer == null) {
                Log.w(TAG, "No orientation sensors available — using static lighting")
            }
        }
    }

    /**
     * Stop listening for sensor events and release resources.
     */
    fun stop() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
    }

    /**
     * Reset the light direction to the default position.
     */
    fun reset() {
        _lightDirection[0] = 0.3f
        _lightDirection[1] = 0.5f
        _lightDirection[2] = 0.8f
    }

    /**
     * Update sensor sensitivity from config changes.
     */
    fun setSensitivity(value: Float) {
        sensitivity = value
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                // Rotation vector gives us the rotation matrix directly
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                SensorManager.getOrientation(rotationMatrix, orientationAngles)
                updateLightFromOrientation()
            }
            Sensor.TYPE_ACCELEROMETER -> {
                accelerometerData = event.values.copyOf()
                tryComputeOrientationFromFallback()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetometerData = event.values.copyOf()
                tryComputeOrientationFromFallback()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        // Not needed for lighting approximation
    }

    /**
     * Compute orientation from accelerometer + magnetometer when rotation vector is unavailable.
     */
    private fun tryComputeOrientationFromFallback() {
        val accel = accelerometerData ?: return
        val mag = magnetometerData ?: return

        val r = FloatArray(9)
        if (SensorManager.getRotationMatrix(r, null, accel, mag)) {
            SensorManager.getOrientation(r, orientationAngles)
            updateLightFromOrientation()
        }
    }

    /**
     * Convert device orientation angles into a light direction vector.
     *
     * We map pitch and roll to X/Y light direction, keeping Z always positive
     * (light always comes from "in front of" the glass surface).
     */
    private fun updateLightFromOrientation() {
        // orientationAngles[1] = pitch (rotation around X axis, -π to π)
        // orientationAngles[2] = roll  (rotation around Y axis, -π/2 to π/2)
        val pitch = orientationAngles[1] * sensitivity
        val roll = orientationAngles[2] * sensitivity

        // Map orientation to light direction
        // When device is flat: light comes from directly above (0, 0, 1)
        // When tilted: light shifts to the tilt direction
        val targetX = Math.sin(roll.toDouble()).toFloat()
        val targetY = Math.sin(pitch.toDouble()).toFloat()
        val targetZ = Math.max(0.3f, Math.cos(pitch.toDouble()).toFloat() * Math.cos(roll.toDouble()).toFloat())

        // Smooth using exponential moving average (prevents jittery highlights)
        _lightDirection[0] = lerp(_lightDirection[0], targetX, SMOOTHING_FACTOR)
        _lightDirection[1] = lerp(_lightDirection[1], targetY, SMOOTHING_FACTOR)
        _lightDirection[2] = lerp(_lightDirection[2], targetZ, SMOOTHING_FACTOR)

        // Normalize
        val len = Math.sqrt(
            (_lightDirection[0] * _lightDirection[0] +
             _lightDirection[1] * _lightDirection[1] +
             _lightDirection[2] * _lightDirection[2]).toDouble()
        ).toFloat()
        if (len > 0.001f) {
            _lightDirection[0] /= len
            _lightDirection[1] /= len
            _lightDirection[2] /= len
        }
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
