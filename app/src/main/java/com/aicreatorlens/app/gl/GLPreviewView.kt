package com.aicreatorlens.app.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import com.aicreatorlens.app.engine.CreatorEngine

class GLPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "GLPreviewView"
    }

    private var renderer: GLRenderer? = null
    private var surfaceReady = false

    /** Called from GL thread when camera SurfaceTexture is created and ready. */
    var onCameraSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    fun setEngineParams(params: CreatorEngine) {
        renderer?.setEngineParams(params)
    }

    fun setComparisonMode(enabled: Boolean) {
        renderer?.setComparisonMode(enabled)
    }

    fun setComparisonSplit(position: Float) {
        renderer?.setComparisonSplit(position)
    }

    fun getCameraSurfaceTexture(): SurfaceTexture? {
        return renderer?.getCameraSurfaceTexture()
    }

    fun getRenderer(): GLRenderer? = renderer

    fun release() {
        renderer?.stop()
        renderer = null
        surfaceReady = false
        surfaceTextureListener = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable: ${width}x${height}")
        surfaceReady = true
        startRenderer(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged: ${width}x${height}")
        renderer?.let { r ->
            r.stop()
            startRenderer(width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed")
        renderer?.stop()
        renderer = null
        surfaceReady = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Required by TextureView.SurfaceTextureListener interface
    }

    private fun startRenderer(width: Int, height: Int) {
        if (!surfaceReady) return

        val currentSurface = surfaceTexture ?: run {
            Log.e(TAG, "startRenderer: surfaceTexture is null!")
            return
        }
        Log.d(TAG, "startRenderer: ${width}x${height}, creating GLRenderer")
        val callback = onCameraSurfaceTextureReady
        renderer = GLRenderer().apply {
            onSurfaceTextureAvailable = { st ->
                Log.d(TAG, "Camera SurfaceTexture ready, notifying callback")
                // Post to main thread so Compose can react
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback?.invoke(st)
                }
            }
            start(android.view.Surface(currentSurface), width, height)
        }
    }
}