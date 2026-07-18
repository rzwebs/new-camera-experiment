package com.aicreatorlens.app.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import com.aicreatorlens.app.engine.CreatorEngine
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class GLRenderer {

    companion object {
        private const val TAG = "GLRenderer"
    }

    private val pipeline = ShaderPipeline()
    private var cameraTextureId = 0
    private var cameraSurfaceTexture: SurfaceTexture? = null
    private val textureMatrix = FloatArray(16)

    private val paramsRef = AtomicReference(CreatorEngine())
    private val comparisonModeRef = AtomicBoolean(false)
    private val comparisonSplitRef = AtomicReference(0.5f)
    private val flipXRef = AtomicInteger(1) // 1 = front camera (mirrored)

    private val running = AtomicBoolean(false)
    private var renderThread: Thread? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val logMessages = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun getLogMessages(): List<String> = logMessages.toList()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logMessages.add(msg)
        while (logMessages.size > 30) logMessages.poll()
    }

    // Callbacks
    var onSurfaceTextureAvailable: ((SurfaceTexture) -> Unit)? = null

    fun setEngineParams(params: CreatorEngine) {
        paramsRef.set(params)
    }

    fun setComparisonMode(enabled: Boolean) {
        comparisonModeRef.set(enabled)
    }

    fun setComparisonSplit(position: Float) {
        comparisonSplitRef.set(position.coerceIn(0f, 1f))
    }

    fun setFlipX(flip: Int) {
        flipXRef.set(flip)
    }

    fun getCameraSurfaceTexture(): SurfaceTexture? = cameraSurfaceTexture

    fun start(surface: android.view.Surface, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        running.set(true)
        renderThread = Thread({
            initGL(surface)
            renderLoop()
        }, "GLRenderThread").apply {
            start()
        }
    }

    fun stop() {
        running.set(false)
        renderThread?.join(3000)
        renderThread = null
    }

    fun requestRender() {
        // Continuous render loop, no explicit request needed
    }

    private fun initGL(surface: android.view.Surface) {
        log("initGL() starting...")

        if (!EGLHelper.init(surface)) {
            log("ERROR: EGLHelper.init() FAILED")
            return
        }

        // Log GL info
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"
        val vendor = GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"
        val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"
        log("GL: $version / $vendor / $renderer")

        // Enable OES extension
        GLES30.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        log("OES texture external enabled")

        // Create camera texture
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        log("Camera texture ID: $cameraTextureId")

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)

        // Create SurfaceTexture from camera texture
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener({ _ ->
                // New frame available from camera
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        log("Camera SurfaceTexture created!")

        // Initialize shader pipeline
        pipeline.init()
        log("Shader pipeline initialized (${pipeline.getProgramCount()} programs)")

        // Notify camera surface texture is ready
        log("Notifying camera SurfaceTexture available...")
        onSurfaceTextureAvailable?.invoke(cameraSurfaceTexture!!)
    }

    private fun renderLoop() {
        var frameCount = 0L
        while (running.get()) {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                SystemClock.sleep(16)
                continue
            }

            // Update camera texture
            cameraSurfaceTexture?.let { st ->
                st.updateTexImage()
                st.getTransformMatrix(textureMatrix)
            }

            // Render
            val params = paramsRef.get()
            pipeline.render(
                cameraTextureId = cameraTextureId,
                textureMatrix = textureMatrix,
                width = surfaceWidth,
                height = surfaceHeight,
                params = params,
                comparisonMode = comparisonModeRef.get(),
                comparisonSplit = comparisonSplitRef.get(),
                flipX = flipXRef.get()
            )

            EGLHelper.swapBuffers()

            frameCount++
            if (frameCount == 1L) {
                log("First frame rendered!")
            }
            if (frameCount % 300L == 0L) {
                log("Frames rendered: $frameCount")
            }

            // ~60fps cap
            SystemClock.sleep(8)
        }

        log("Render loop ending. Total frames: $frameCount")
        cameraSurfaceTexture?.release()
        pipeline.release()
        EGLHelper.release()
    }
}