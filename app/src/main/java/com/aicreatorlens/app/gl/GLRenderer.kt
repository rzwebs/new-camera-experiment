package com.aicreatorlens.app.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.os.SystemClock
import android.util.Log
import com.aicreatorlens.app.engine.CreatorEngine
import com.aicreatorlens.app.ui.screens.DebugLog
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {

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

    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var frameAvailable = false
    private val frameLock = Object()
    private val logMessages = java.util.concurrent.ConcurrentLinkedQueue<String>()
    private var drawFrameCount = 0
    private var framesWithCameraData = 0
    @Volatile private var testPatternMode = false

    // Callback to notify when camera SurfaceTexture is ready
    var onSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    fun getLogMessages(): List<String> = logMessages.toList()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logMessages.add(msg)
        DebugLog.log("GL", msg)
        while (logMessages.size > 50) logMessages.poll()
    }

    fun setEngineParams(params: CreatorEngine) { paramsRef.set(params) }
    fun setComparisonMode(enabled: Boolean) { comparisonModeRef.set(enabled) }
    fun setComparisonSplit(position: Float) { comparisonSplitRef.set(position.coerceIn(0f, 1f)) }
    fun setFlipX(flip: Int) { flipXRef.set(flip) }
    fun getCameraSurfaceTexture(): SurfaceTexture? = cameraSurfaceTexture

    // Called from camera thread when new frame is available
    fun onCameraFrameAvailable() {
        synchronized(frameLock) {
            frameAvailable = true
            if (framesWithCameraData < 3) {
                DebugLog.log("GL", "onCameraFrameAvailable() called! total=$framesWithCameraData")
            }
        }
    }

    // --- GLSurfaceView.Renderer ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        log(">>> onSurfaceCreated()")
        log("step 1: reading GL strings")
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"
        val vendor = GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"
        val renderer = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"
        log("  GL_VERSION: $version")
        log("  GL_VENDOR: $vendor")
        log("  GL_RENDERER: $renderer")

        log("step 2: enabling GL_TEXTURE_EXTERNAL_OES")
        GLES30.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

        log("step 3: glGenTextures (OES external)")
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        log("  camera texture ID = $cameraTextureId")

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        log("step 3 done: texture params set")

        log("step 4: creating SurfaceTexture from GL texture")
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId).apply {
            setOnFrameAvailableListener({
                onCameraFrameAvailable()
            }, android.os.Handler(android.os.Looper.getMainLooper()))
        }
        log("step 4 done: SurfaceTexture created")

        log("step 5: compiling shaders")
        pipeline.init()
        log("step 5 done: ${pipeline.getProgramCount()} programs")

        log("step 6: notifying callback (camera can start)")
        onSurfaceTextureReady?.invoke(cameraSurfaceTexture!!)
        log("step 6 done: callback invoked")
        log("<<< onSurfaceCreated() COMPLETE")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        log(">>> onSurfaceChanged($width x $height)")
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)
        log("<<< onSurfaceChanged() done")
    }

    override fun onDrawFrame(gl: GL10?) {
        drawFrameCount++
        var gotFrame = false

        // Update camera texture if frame available
        synchronized(frameLock) {
            if (frameAvailable) {
                cameraSurfaceTexture?.let { st ->
                    st.updateTexImage()
                    st.getTransformMatrix(textureMatrix)
                    gotFrame = true
                    framesWithCameraData++
                }
                frameAvailable = false
            }
        }

        // Diagnostic: log every 30 frames
        if (drawFrameCount % 30 == 1) {
            val err = GLES30.glGetError()
            log("draw#$drawFrameCount frameAvail=$frameAvailable cameraTex=$cameraTextureId st=${cameraSurfaceTexture != null} err=$err dataFrames=$framesWithCameraData")
        }

        // Log first camera frame arrival
        synchronized(this) {
            if (!firstFrameLogged) {
                log(">>> FIRST FRAME RENDERED! (no camera data yet)")
                firstFrameLogged = true
            }
            if (gotFrame && firstCameraFrameLogged == 0) {
                firstCameraFrameLogged = drawFrameCount
                log(">>> FIRST CAMERA FRAME RECEIVED on draw#$drawFrameCount!")
            }
        }

        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        // TEST PATTERN: if no camera data after 90 frames, render magenta to prove GL works
        if (framesWithCameraData == 0 && drawFrameCount > 90 && !testPatternMode) {
            testPatternMode = true
            log(">>> NO CAMERA DATA after 90 frames! Switching to TEST PATTERN (magenta)")
        }

        if (testPatternMode) {
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glClearColor(1.0f, 0.0f, 1.0f, 1.0f) // MAGENTA
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            return
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
    }

    @Volatile
    private var firstFrameLogged = false
    @Volatile
    private var firstCameraFrameLogged = 0
}