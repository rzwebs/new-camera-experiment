package com.aicreatorlens.app.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.EGL14
import android.util.Log
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.ui.screens.DebugLog
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
    private val flipXRef = AtomicInteger(1)

    private val running = AtomicBoolean(false)
    private var renderThread: Thread? = null
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private val logMessages = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun getLogMessages(): List<String> = logMessages.toList()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logMessages.add(msg)
        DebugLog.log("GL", msg)
        while (logMessages.size > 50) logMessages.poll()
    }

    var onSurfaceTextureAvailable: ((SurfaceTexture) -> Unit)? = null

    fun setEngineParams(params: CreatorEngine) { paramsRef.set(params) }
    fun setComparisonMode(enabled: Boolean) { comparisonModeRef.set(enabled) }
    fun setComparisonSplit(position: Float) { comparisonSplitRef.set(position.coerceIn(0f, 1f)) }
    fun setFlipX(flip: Int) { flipXRef.set(flip) }
    fun getCameraSurfaceTexture(): SurfaceTexture? = cameraSurfaceTexture

    fun start(surface: android.view.Surface, width: Int, height: Int) {
        log(">>> start() BEGIN surface=${width}x${height}")
        surfaceWidth = width
        surfaceHeight = height
        running.set(true)
        log("creating GLRenderThread...")
        renderThread = Thread({
            log(">>> GLRenderThread run() ENTERED")
            initGL(surface)
            log(">>> GLRenderThread calling renderLoop()")
            renderLoop()
            log("<<< GLRenderThread EXITED")
        }, "GLRenderThread").apply {
            start()
            log("GLRenderThread.start() called")
        }
        log("<<< start() returned (thread is async)")
    }

    fun stop() {
        log("stop() called")
        running.set(false)
        renderThread?.join(3000)
        renderThread = null
    }

    fun requestRender() {}

    private fun initGL(surface: android.view.Surface) {
        log(">>> initGL() BEGIN")

        log("step 1: EGLHelper.init()")
        if (!EGLHelper.init(surface)) {
            log("ERROR: EGLHelper.init() FAILED")
            return
        }
        log("step 1 done: EGL ready")

        log("step 2: reading GL strings")
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"
        val vendor = GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"
        val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"
        log("GL_VERSION: $version")
        log("GL_VENDOR: $vendor")
        log("GL_RENDERER: $renderer")

        log("step 3: enabling GL_TEXTURE_EXTERNAL_OES")
        GLES30.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

        log("step 4: glGenTextures")
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        log("step 4 done: camera texture ID = $cameraTextureId")

        log("step 5: binding & setting texture params")
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        log("step 5 done: texture params set")

        log("step 6: creating SurfaceTexture from GL texture")
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener({ _ ->
                // frame available
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        log("step 6 done: SurfaceTexture created")

        log("step 7: pipeline.init() — compiling shaders")
        pipeline.init()
        val progCount = pipeline.getProgramCount()
        log("step 7 done: $progCount shader programs compiled")

        if (progCount == 0) {
            log("ERROR: ZERO shader programs compiled!")
        }

        log("step 8: invoking onSurfaceTextureAvailable callback")
        onSurfaceTextureAvailable?.invoke(cameraSurfaceTexture!!)
        log("step 8 done: callback invoked")
        log("<<< initGL() COMPLETE")
    }

    private fun renderLoop() {
        log(">>> renderLoop() BEGIN")
        var frameCount = 0L
        while (running.get()) {
            if (surfaceWidth <= 0 || surfaceHeight <= 0) {
                SystemClock.sleep(16)
                continue
            }

            cameraSurfaceTexture?.let { st ->
                st.updateTexImage()
                st.getTransformMatrix(textureMatrix)
            }

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
                log(">>> FIRST FRAME RENDERED!")
            }
            if (frameCount % 300L == 0L) {
                log("frames: $frameCount")
            }

            SystemClock.sleep(8)
        }
        log("<<< renderLoop() EXIT. Total frames: $frameCount")
        cameraSurfaceTexture?.release()
        pipeline.release()
        EGLHelper.release()
    }
}