package com.zanghai.liquidglass.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import com.zanghai.liquidglass.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Custom EGL context and OpenGL ES 2.0 rendering pipeline for the liquid glass effect.
 *
 * Runs on a dedicated [HandlerThread] so it doesn't block the main UI thread.
 * Handles background texture uploading, optional multi-pass Gaussian blur, and
 * the final liquid glass shader pass.
 */
internal class LiquidGlassRenderer(
    private val context: Context,
    private val backgroundCapture: BackgroundCaptureEngine,
    private val sensorTracker: SensorTracker,
    private val physicsSimulator: PhysicsSimulator,
    private val configProvider: () -> LiquidGlassConfig
) {
    companion object {
        private const val TAG = "LiquidGlass:Renderer"
        private const val FLOAT_SIZE_BYTES = 4
        
        // Fullscreen quad for post-processing shaders
        private val VERTEX_DATA = floatArrayOf(
            // X, Y, U, V
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f
        )
    }

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var surface: Surface? = null

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    // GL Objects
    private var vertexBuffer: FloatBuffer = ByteBuffer
        .allocateDirect(VERTEX_DATA.size * FLOAT_SIZE_BYTES)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(VERTEX_DATA)
        .apply { position(0) }

    // Textures
    private var bgTextureId = 0
    private var blurTexture1Id = 0
    private var blurTexture2Id = 0

    // FBOs for blur pass
    private var blurFbo1Id = 0
    private var blurFbo2Id = 0

    // Programs
    private var blurProgram = 0
    private var liquidProgram = 0

    // Animation
    private var startTimeMs = 0L

    // View dimensions
    private var width = 0
    private var height = 0
    
    // Texture dimensions
    private var textureWidth = 0
    private var textureHeight = 0

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get() || isPaused.get()) return

            try {
                if (width > 0 && height > 0 && eglSurface != EGL14.EGL_NO_SURFACE) {
                    drawFrame()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in render loop", e)
            }

            // Schedule next frame (~60fps)
            renderHandler?.postDelayed(this, 16)
        }
    }

    /**
     * Called when the TextureView's surface is available.
     * Initializes EGL context and starts the render loop.
     */
    fun onSurfaceCreated(surfaceTexture: SurfaceTexture, w: Int, h: Int) {
        width = w
        height = h
        surface = Surface(surfaceTexture)

        if (isRunning.getAndSet(true)) return

        renderThread = HandlerThread("LiquidGlass-GLThread").apply { start() }
        renderHandler = Handler(renderThread!!.looper)

        renderHandler?.post {
            initEGL()
            initGL()
            startTimeMs = System.currentTimeMillis()
            renderHandler?.post(renderRunnable)
        }
    }

    /**
     * Called when the TextureView's dimensions change.
     */
    fun onSurfaceChanged(w: Int, h: Int) {
        width = w
        height = h
        
        renderHandler?.post {
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                GLES20.glViewport(0, 0, width, height)
                // Reallocate FBOs if they exist, or they will be created on demand
                if (blurFbo1Id != 0) {
                    destroyFBOs()
                    createFBOs(textureWidth, textureHeight)
                }
            }
        }
    }

    /**
     * Called when the TextureView's surface is destroyed.
     */
    fun onSurfaceDestroyed() {
        if (!isRunning.getAndSet(false)) return

        renderHandler?.removeCallbacksAndMessages(null)
        
        // Clean up GL resources on the GL thread
        renderHandler?.post {
            destroyGL()
            destroyEGL()
            
            surface?.release()
            surface = null
            
            renderThread?.quitSafely()
            renderThread = null
            renderHandler = null
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        if (isPaused.getAndSet(false) && isRunning.get()) {
            renderHandler?.post(renderRunnable)
        }
    }

    // =========================================================================================
    // EGL Setup
    // =========================================================================================

    private fun initEGL() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        if (numConfigs[0] == 0) {
            throw RuntimeException("unable to find suitable EGLConfig")
        }

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        ShaderUtils.checkGlError("eglCreateContext")

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        ShaderUtils.checkGlError("eglCreateWindowSurface")

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }
    }

    private fun destroyEGL() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            EGL14.eglTerminate(eglDisplay)
            eglDisplay = EGL14.EGL_NO_DISPLAY
        }
    }

    // =========================================================================================
    // GL Setup & Render Pipeline
    // =========================================================================================

    private fun initGL() {
        // Load shaders
        blurProgram = ShaderUtils.loadProgram(context, R.raw.blur_vertex, R.raw.blur_fragment)
        liquidProgram = ShaderUtils.loadProgram(context, R.raw.liquid_glass_vertex, R.raw.liquid_glass_fragment)

        // Generate texture for background
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        bgTextureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun destroyGL() {
        val textures = intArrayOf(bgTextureId, blurTexture1Id, blurTexture2Id)
        GLES20.glDeleteTextures(3, textures, 0)
        
        destroyFBOs()

        if (blurProgram != 0) GLES20.glDeleteProgram(blurProgram)
        if (liquidProgram != 0) GLES20.glDeleteProgram(liquidProgram)
    }

    private fun createFBOs(w: Int, h: Int) {
        val fbos = IntArray(2)
        GLES20.glGenFramebuffers(2, fbos, 0)
        blurFbo1Id = fbos[0]
        blurFbo2Id = fbos[1]

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        blurTexture1Id = textures[0]
        blurTexture2Id = textures[1]

        for (i in 0..1) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[i])
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbos[i])
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, textures[i], 0)
        }
        
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    private fun destroyFBOs() {
        if (blurFbo1Id != 0) {
            val fbos = intArrayOf(blurFbo1Id, blurFbo2Id)
            GLES20.glDeleteFramebuffers(2, fbos, 0)
            blurFbo1Id = 0
            blurFbo2Id = 0
        }
    }

    private fun drawFrame() {
        val config = configProvider()
        val bgBitmap = backgroundCapture.getCurrentBitmap() ?: return
        
        // Handle texture resizing if needed
        if (textureWidth != bgBitmap.width || textureHeight != bgBitmap.height) {
            textureWidth = bgBitmap.width
            textureHeight = bgBitmap.height
            destroyFBOs()
            createFBOs(textureWidth, textureHeight)
        }

        // Upload latest background bitmap to texture
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bgBitmap, 0)

        // The texture we will finally sample from in the liquid shader
        var finalTextureId = bgTextureId

        // =====================================================================
        // PASS 1: Horizontal Blur (if blur radius > 0)
        // =====================================================================
        if (config.blurRadius > 0.1f) {
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFbo1Id)
            GLES20.glViewport(0, 0, textureWidth, textureHeight)
            
            GLES20.glUseProgram(blurProgram)
            
            val posLoc = GLES20.glGetAttribLocation(blurProgram, "a_Position")
            val texLoc = GLES20.glGetAttribLocation(blurProgram, "a_TexCoord")
            val samplerLoc = GLES20.glGetUniformLocation(blurProgram, "u_Texture")
            val dirLoc = GLES20.glGetUniformLocation(blurProgram, "u_Direction")
            val sizeLoc = GLES20.glGetUniformLocation(blurProgram, "u_BlurSize")
            
            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 4 * FLOAT_SIZE_BYTES, vertexBuffer)
            GLES20.glEnableVertexAttribArray(posLoc)
            
            vertexBuffer.position(2)
            GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 4 * FLOAT_SIZE_BYTES, vertexBuffer)
            GLES20.glEnableVertexAttribArray(texLoc)
            
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, bgTextureId)
            GLES20.glUniform1i(samplerLoc, 0)
            
            // Horizontal blur
            GLES20.glUniform2f(dirLoc, 1.0f / textureWidth, 0.0f)
            GLES20.glUniform1f(sizeLoc, config.blurRadius)
            
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            // =====================================================================
            // PASS 2: Vertical Blur
            // =====================================================================
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, blurFbo2Id)
            
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, blurTexture1Id)
            GLES20.glUniform1i(samplerLoc, 0)
            
            // Vertical blur
            GLES20.glUniform2f(dirLoc, 0.0f, 1.0f / textureHeight)
            
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            
            finalTextureId = blurTexture2Id
        }

        // =====================================================================
        // PASS 3: Liquid Glass Refraction Effect
        // =====================================================================
        // Render to screen
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
        GLES20.glViewport(0, 0, width, height)
        
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        
        GLES20.glUseProgram(liquidProgram)
        
        // Set vertex attributes
        val posLoc = GLES20.glGetAttribLocation(liquidProgram, "a_Position")
        val texLoc = GLES20.glGetAttribLocation(liquidProgram, "a_TexCoord")
        
        vertexBuffer.position(0)
        GLES20.glVertexAttribPointer(posLoc, 2, GLES20.GL_FLOAT, false, 4 * FLOAT_SIZE_BYTES, vertexBuffer)
        GLES20.glEnableVertexAttribArray(posLoc)
        
        vertexBuffer.position(2)
        GLES20.glVertexAttribPointer(texLoc, 2, GLES20.GL_FLOAT, false, 4 * FLOAT_SIZE_BYTES, vertexBuffer)
        GLES20.glEnableVertexAttribArray(texLoc)
        
        // Set texture
        val samplerLoc = GLES20.glGetUniformLocation(liquidProgram, "u_BackgroundTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, finalTextureId)
        GLES20.glUniform1i(samplerLoc, 0)
        
        // Update physics and time
        physicsSimulator.update()
        val time = (System.currentTimeMillis() - startTimeMs) / 1000f
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_Time"), time)
        GLES20.glUniform2f(GLES20.glGetUniformLocation(liquidProgram, "u_Resolution"), width.toFloat(), height.toFloat())
        
        // Set configuration uniforms
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_IOR"), config.indexOfRefraction)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_ChromaticAberration"), config.chromaticAberration)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_FresnelPower"), config.fresnelPower)
        
        // Lighting from sensor
        val lightDir = sensorTracker.lightDirection
        GLES20.glUniform3f(GLES20.glGetUniformLocation(liquidProgram, "u_LightDir"), lightDir[0], lightDir[1], lightDir[2])
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_Shininess"), config.shininess)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_SpecularIntensity"), config.specularIntensity)
        
        // Liquid animation
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_LiquidSpeed"), config.liquidSpeed)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_LiquidScale"), config.liquidScale)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_LiquidAmplitude"), config.liquidAmplitude)
        
        // Colors
        val edgeR = Color.red(config.edgeGlowColor) / 255f
        val edgeG = Color.green(config.edgeGlowColor) / 255f
        val edgeB = Color.blue(config.edgeGlowColor) / 255f
        val edgeA = Color.alpha(config.edgeGlowColor) / 255f
        GLES20.glUniform4f(GLES20.glGetUniformLocation(liquidProgram, "u_EdgeGlowColor"), edgeR, edgeG, edgeB, edgeA)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_EdgeGlowIntensity"), config.edgeGlowIntensity)
        
        val tintR = Color.red(config.tintColor) / 255f
        val tintG = Color.green(config.tintColor) / 255f
        val tintB = Color.blue(config.tintColor) / 255f
        val tintA = Color.alpha(config.tintColor) / 255f
        GLES20.glUniform4f(GLES20.glGetUniformLocation(liquidProgram, "u_TintColor"), tintR, tintG, tintB, tintA)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_TintIntensity"), config.tintIntensity)
        
        // Blur
        // We pass 0.0 to the final shader if we already pre-blurred using FBOs, 
        // to avoid double blurring.
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_BlurRadius"), 0.0f) 
        
        // Touch ripples
        val ripples = physicsSimulator.getActiveRipples()
        GLES20.glUniform1i(GLES20.glGetUniformLocation(liquidProgram, "u_RippleCount"), ripples.size)
        GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_RippleAmplitude"), config.rippleAmplitude)
        
        for (i in 0 until Math.min(ripples.size, PhysicsSimulator.MAX_RIPPLES)) {
            val r = ripples[i]
            val rTime = physicsSimulator.getElapsedSeconds(r)
            GLES20.glUniform2f(GLES20.glGetUniformLocation(liquidProgram, "u_RippleCenter$i"), r.centerX, 1.0f - r.centerY) // Flip Y for OpenGL
            GLES20.glUniform1f(GLES20.glGetUniformLocation(liquidProgram, "u_RippleTime$i"), rTime)
        }
        
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        
        // Swap buffers
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }
}
