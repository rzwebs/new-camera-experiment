package com.aicreatorlens.app.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.ui.screens.DebugLog

class GLPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "GLPreviewView"
    }

    private var renderer: GLRenderer? = null
    private var surfaceReady = false

    var onCameraSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    init {
        DebugLog.log("VIEW", ">>> GLPreviewView init{} called")
        surfaceTextureListener = this
        DebugLog.log("VIEW", "surfaceTextureListener set")
    }

    fun setEngineParams(params: CreatorEngine) {
        renderer?.setEngineParams(params)
    }

    fun setComparisonMode(enabled: Boolean) {
        renderer?.setComparisonMode(enabled)
    }

    fun setComparisonSplit(position: Float) {
        renderer?.setComparisonSplit(position)
    }

    fun getCameraSurfaceTexture(): SurfaceTexture? = renderer?.getCameraSurfaceTexture()

    fun getRenderer(): GLRenderer? = renderer

    fun release() {
        DebugLog.log("VIEW", "release() called")
        renderer?.stop()
        renderer = null
        surfaceReady = false
        surfaceTextureListener = null
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        DebugLog.log("VIEW", ">>> onSurfaceTextureAvailable: ${width}x${height}")
        Log.d(TAG, "onSurfaceTextureAvailable: ${width}x${height}")
        surfaceReady = true
        startRenderer(width, height)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        DebugLog.log("VIEW", "onSurfaceTextureSizeChanged: ${width}x${height}")
        renderer?.let {
            DebugLog.log("VIEW", "stopping old renderer, restarting...")
            it.stop()
            startRenderer(width, height)
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        DebugLog.log("VIEW", "onSurfaceTextureDestroyed")
        renderer?.stop()
        renderer = null
        surfaceReady = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Required by interface
    }

    private fun startRenderer(width: Int, height: Int) {
        DebugLog.log("VIEW", ">>> startRenderer($width, $height)")
        if (!surfaceReady) {
            DebugLog.log("VIEW", "BLOCKED: surfaceReady=false")
            return
        }

        val currentSurface = surfaceTexture
        if (currentSurface == null) {
            DebugLog.log("VIEW", "ERROR: surfaceTexture is null!")
            return
        }
        DebugLog.log("VIEW", "surfaceTexture is valid")

        val callback = onCameraSurfaceTextureReady
        DebugLog.log("VIEW", "creating GLRenderer...")
        renderer = GLRenderer().apply {
            DebugLog.log("VIEW", "setting onSurfaceTextureAvailable callback on renderer")
            onSurfaceTextureAvailable = { st ->
                DebugLog.log("VIEW", ">>> GLRenderer callback FIRED — posting to main thread")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    DebugLog.log("VIEW", ">>> invoking onCameraSurfaceTextureReady on main thread")
                    callback?.invoke(st)
                    DebugLog.log("VIEW", "<<< onCameraSurfaceTextureReady done")
                }
            }
            DebugLog.log("VIEW", "calling renderer.start()...")
            start(android.view.Surface(currentSurface), width, height)
            DebugLog.log("VIEW", "renderer.start() returned")
        }
        DebugLog.log("VIEW", "<<< startRenderer done")
    }
}