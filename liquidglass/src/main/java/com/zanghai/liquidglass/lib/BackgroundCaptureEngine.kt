package com.zanghai.liquidglass.lib

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.View
import android.view.ViewGroup
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Captures the view hierarchy content behind a [LiquidGlassView] into a bitmap
 * that can be uploaded as an OpenGL texture for the refraction shader.
 *
 * Uses a double-buffering approach (two bitmaps that swap) to avoid tearing,
 * and runs capture on a background [HandlerThread] at a configurable frame rate.
 *
 * The captured region is cropped to exactly the area behind the glass view.
 */
internal class BackgroundCaptureEngine(
    private val targetView: View,
    private var downscale: Float = 0.5f,
    private var frameRate: Int = 30
) {
    companion object {
        private const val TAG = "LiquidGlass:Capture"
    }

    // Double-buffered bitmaps
    private var bitmapA: Bitmap? = null
    private var bitmapB: Bitmap? = null
    private var useA = true

    private val currentBitmap = AtomicReference<Bitmap?>(null)
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get() || isPaused.get()) return

            try {
                captureBackground()
            } catch (e: Exception) {
                Log.e(TAG, "Background capture failed", e)
            }

            // Schedule next capture
            val intervalMs = (1000L / frameRate.coerceIn(1, 60))
            captureHandler?.postDelayed(this, intervalMs)
        }
    }

    /**
     * Get the most recently captured background bitmap.
     * Thread-safe — can be called from the GL rendering thread.
     */
    fun getCurrentBitmap(): Bitmap? = currentBitmap.get()

    /**
     * Start the background capture engine.
     */
    fun start() {
        if (isRunning.getAndSet(true)) return

        captureThread = HandlerThread("LiquidGlass-Capture").apply { start() }
        captureHandler = Handler(captureThread!!.looper)

        // Post the first capture
        captureHandler?.post(captureRunnable)
    }

    /**
     * Stop the background capture engine and release resources.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        captureHandler?.removeCallbacksAndMessages(null)
        captureThread?.quitSafely()
        captureThread = null
        captureHandler = null

        bitmapA?.recycle()
        bitmapB?.recycle()
        bitmapA = null
        bitmapB = null
        currentBitmap.set(null)
    }

    /**
     * Pause capture (e.g., when the activity is paused).
     */
    fun pause() {
        isPaused.set(true)
    }

    /**
     * Resume capture after pause.
     */
    fun resume() {
        if (isPaused.getAndSet(false) && isRunning.get()) {
            captureHandler?.post(captureRunnable)
        }
    }

    /**
     * Update capture parameters from config.
     */
    fun updateConfig(newDownscale: Float, newFrameRate: Int) {
        downscale = newDownscale.coerceIn(0.1f, 1.0f)
        frameRate = newFrameRate.coerceIn(1, 60)
    }

    /**
     * Core capture logic:
     * 1. Find the root view (the window's decor view)
     * 2. Temporarily hide our target view
     * 3. Draw the view hierarchy to a bitmap
     * 4. Crop to the region behind the glass view
     * 5. Restore visibility and swap buffers
     */
    private fun captureBackground() {
        val view = targetView
        val parent = view.rootView as? ViewGroup ?: return

        // Get the target view's position on screen
        val viewLocation = IntArray(2)
        val parentLocation = IntArray(2)
        view.getLocationOnScreen(viewLocation)
        parent.getLocationOnScreen(parentLocation)

        val relativeX = viewLocation[0] - parentLocation[0]
        val relativeY = viewLocation[1] - parentLocation[1]

        val viewWidth = view.width
        val viewHeight = view.height
        if (viewWidth <= 0 || viewHeight <= 0) return

        // Calculate downscaled dimensions
        val scaledWidth = (viewWidth * downscale).toInt().coerceAtLeast(1)
        val scaledHeight = (viewHeight * downscale).toInt().coerceAtLeast(1)

        // Select the back buffer
        val bitmap = getBackBuffer(scaledWidth, scaledHeight)

        // Draw the entire parent view hierarchy into a temporary canvas
        // We need to capture the full parent, then crop to our region
        val parentWidth = parent.width
        val parentHeight = parent.height
        if (parentWidth <= 0 || parentHeight <= 0) return

        // Create (or reuse) a full-parent canvas only if needed
        val canvas = Canvas(bitmap)
        canvas.save()

        // Scale and translate so we only render the region behind our view
        val scaleX = scaledWidth.toFloat() / viewWidth
        val scaleY = scaledHeight.toFloat() / viewHeight
        canvas.scale(scaleX, scaleY)
        canvas.translate(-relativeX.toFloat(), -relativeY.toFloat())

        // Hide our view temporarily so it doesn't render itself
        val wasVisible = view.visibility
        try {
            // Must post visibility change on UI thread
            view.post { view.visibility = View.INVISIBLE }
            // Small delay to let the visibility change take effect
            Thread.sleep(2)

            // Draw the parent hierarchy (will include everything except our hidden view)
            parent.draw(canvas)
        } catch (e: Exception) {
            Log.e(TAG, "Error during parent draw", e)
        } finally {
            // Restore visibility
            view.post { view.visibility = wasVisible }
        }

        canvas.restore()

        // Swap to front buffer
        currentBitmap.set(bitmap)
        useA = !useA
    }

    /**
     * Gets (or creates) the back buffer bitmap at the required dimensions.
     */
    private fun getBackBuffer(width: Int, height: Int): Bitmap {
        val target = if (useA) bitmapA else bitmapB

        // Reuse existing bitmap if dimensions match
        if (target != null && target.width == width && target.height == height && !target.isRecycled) {
            // Clear the bitmap for reuse
            target.eraseColor(0)
            return target
        }

        // Create new bitmap
        val newBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        if (useA) bitmapA = newBitmap else bitmapB = newBitmap
        return newBitmap
    }
}
