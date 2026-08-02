package com.zanghai.liquidglass.lib

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.TextureView
import android.view.View
import android.view.ViewOutlineProvider

/**
 * A true physically-based liquid glass effect view for Android.
 *
 * This view captures the content behind it in real-time and applies a true
 * Snell's Law refraction shader via OpenGL ES 2.0, along with chromatic
 * aberration, procedural liquid animation, and sensor-driven specular highlights.
 *
 * It is NOT a simple blur overlay.
 */
class LiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    private var config = LiquidGlassConfig()
    
    private val backgroundCapture = BackgroundCaptureEngine(this)
    private val sensorTracker = SensorTracker(context)
    private val physicsSimulator = PhysicsSimulator()
    private val renderer = LiquidGlassRenderer(
        context,
        backgroundCapture,
        sensorTracker,
        physicsSimulator
    ) { config } // Lambda to always provide latest config to renderer

    init {
        // Must be transparent so we don't draw a black background before GL initializes
        isOpaque = false
        surfaceTextureListener = this
        
        // Parse XML attributes
        if (attrs != null) {
            val a = context.obtainStyledAttributes(attrs, R.styleable.LiquidGlassView, defStyleAttr, 0)
            
            val builder = LiquidGlassConfig.Builder()
            
            if (a.hasValue(R.styleable.LiquidGlassView_lg_indexOfRefraction))
                builder.indexOfRefraction(a.getFloat(R.styleable.LiquidGlassView_lg_indexOfRefraction, config.indexOfRefraction))
            
            if (a.hasValue(R.styleable.LiquidGlassView_lg_chromaticAberration))
                builder.chromaticAberration(a.getFloat(R.styleable.LiquidGlassView_lg_chromaticAberration, config.chromaticAberration))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_fresnelPower))
                builder.fresnelPower(a.getFloat(R.styleable.LiquidGlassView_lg_fresnelPower, config.fresnelPower))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_shininess))
                builder.shininess(a.getFloat(R.styleable.LiquidGlassView_lg_shininess, config.shininess))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_specularIntensity))
                builder.specularIntensity(a.getFloat(R.styleable.LiquidGlassView_lg_specularIntensity, config.specularIntensity))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_liquidSpeed))
                builder.liquidSpeed(a.getFloat(R.styleable.LiquidGlassView_lg_liquidSpeed, config.liquidSpeed))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_liquidScale))
                builder.liquidScale(a.getFloat(R.styleable.LiquidGlassView_lg_liquidScale, config.liquidScale))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_liquidAmplitude))
                builder.liquidAmplitude(a.getFloat(R.styleable.LiquidGlassView_lg_liquidAmplitude, config.liquidAmplitude))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_edgeGlowColor))
                builder.edgeGlowColor(a.getColor(R.styleable.LiquidGlassView_lg_edgeGlowColor, config.edgeGlowColor))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_edgeGlowIntensity))
                builder.edgeGlowIntensity(a.getFloat(R.styleable.LiquidGlassView_lg_edgeGlowIntensity, config.edgeGlowIntensity))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_tintColor))
                builder.tintColor(a.getColor(R.styleable.LiquidGlassView_lg_tintColor, config.tintColor))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_tintIntensity))
                builder.tintIntensity(a.getFloat(R.styleable.LiquidGlassView_lg_tintIntensity, config.tintIntensity))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_blurRadius))
                builder.blurRadius(a.getFloat(R.styleable.LiquidGlassView_lg_blurRadius, config.blurRadius))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_rippleAmplitude))
                builder.rippleAmplitude(a.getFloat(R.styleable.LiquidGlassView_lg_rippleAmplitude, config.rippleAmplitude))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_rippleDuration))
                builder.rippleDuration(a.getFloat(R.styleable.LiquidGlassView_lg_rippleDuration, config.rippleDuration))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_cornerRadius)) {
                val radius = a.getDimension(R.styleable.LiquidGlassView_lg_cornerRadius, 0f)
                // Convert pixels back to dp for the config
                val dp = radius / context.resources.displayMetrics.density
                builder.cornerRadius(dp)
            }
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_captureDownscale))
                builder.captureDownscale(a.getFloat(R.styleable.LiquidGlassView_lg_captureDownscale, config.captureDownscale))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_captureFrameRate))
                builder.captureFrameRate(a.getInt(R.styleable.LiquidGlassView_lg_captureFrameRate, config.captureFrameRate))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_sensorEnabled))
                builder.sensorEnabled(a.getBoolean(R.styleable.LiquidGlassView_lg_sensorEnabled, config.sensorEnabled))
                
            if (a.hasValue(R.styleable.LiquidGlassView_lg_sensorSensitivity))
                builder.sensorSensitivity(a.getFloat(R.styleable.LiquidGlassView_lg_sensorSensitivity, config.sensorSensitivity))
                
            a.recycle()
            
            setConfig(builder.build())
        } else {
            applyConfig()
        }
    }

    /**
     * Programmatically update the configuration of the liquid glass.
     */
    fun setConfig(newConfig: LiquidGlassConfig) {
        config = newConfig
        applyConfig()
    }
    
    /**
     * Get the current configuration.
     */
    fun getConfig(): LiquidGlassConfig = config

    private fun applyConfig() {
        // Update components with new config values
        backgroundCapture.updateConfig(config.captureDownscale, config.captureFrameRate)
        sensorTracker.setSensitivity(config.sensorSensitivity)
        physicsSimulator.updateConfig(config.rippleAmplitude, config.rippleDuration)
        
        if (config.sensorEnabled) {
            sensorTracker.start()
        } else {
            sensorTracker.stop()
            sensorTracker.reset()
        }
        
        // Apply corner radius via ViewOutlineProvider
        if (config.cornerRadius > 0) {
            val radiusPx = config.cornerRadius * context.resources.displayMetrics.density
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
                }
            }
            clipToOutline = true
        } else {
            outlineProvider = null
            clipToOutline = false
        }
    }

    /**
     * Programmatically trigger a ripple effect at the given local coordinates.
     */
    fun addRipple(x: Float, y: Float) {
        if (width > 0 && height > 0) {
            val uvX = x / width
            val uvY = y / height
            physicsSimulator.addRipple(uvX, uvY)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || 
            (event.action == MotionEvent.ACTION_MOVE && Math.random() > 0.8)) { // Throttle move ripples
            addRipple(event.x, event.y)
        }
        return super.onTouchEvent(event) || isClickable
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        backgroundCapture.start()
        if (config.sensorEnabled) {
            sensorTracker.start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backgroundCapture.stop()
        sensorTracker.stop()
    }
    
    /**
     * Call this when the containing activity/fragment pauses to save battery.
     */
    fun pause() {
        backgroundCapture.pause()
        renderer.pause()
        sensorTracker.stop()
    }
    
    /**
     * Call this when the containing activity/fragment resumes.
     */
    fun resume() {
        backgroundCapture.resume()
        renderer.resume()
        if (config.sensorEnabled) {
            sensorTracker.start()
        }
    }

    // =========================================================================================
    // TextureView.SurfaceTextureListener
    // =========================================================================================

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.onSurfaceCreated(surface, width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        renderer.onSurfaceChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer.onSurfaceDestroyed()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Called when a new frame is drawn. We don't need to do anything here.
    }
}
