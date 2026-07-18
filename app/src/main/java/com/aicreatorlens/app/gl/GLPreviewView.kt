package com.aicreatorlens.app.gl

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.ui.screens.DebugLog

class GLPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    companion object {
        private const val TAG = "GLPreviewView"
    }

    private var renderer: GLRenderer? = null

    var onCameraSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    init {
        DebugLog.log("VIEW", ">>> GLPreviewView init (GLSurfaceView)")
        // Request GLES 3.0
        setEGLContextClientVersion(3)
        DebugLog.log("VIEW", "  setEGLContextClientVersion(3)")

        // Create and set renderer
        val r = GLRenderer()
        renderer = r
        r.onSurfaceTextureReady = { st ->
            DebugLog.log("VIEW", ">>> GLRenderer callback: SurfaceTexture ready!")
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                DebugLog.log("VIEW", ">>> posting to onCameraSurfaceTextureReady")
                onCameraSurfaceTextureReady?.invoke(st)
                DebugLog.log("VIEW", "<<< onCameraSurfaceTextureReady done")
            }
        }

        setRenderer(r)
        DebugLog.log("VIEW", "  setRenderer() called")

        // Render continuously (camera is always streaming)
        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        DebugLog.log("VIEW", "  renderMode = CONTINUOUSLY")
        DebugLog.log("VIEW", "<<< GLPreviewView init done")
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
        renderer = null
    }
}