package com.aicreatorlens.app.gl

import android.graphics.SurfaceTexture
import android.opengl.GLES11Ext
import android.opengl.GLES30
import android.opengl.GLSurfaceView
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

    // Callback to notify when camera SurfaceTexture is ready
    var onSurfaceTextureReady: ((SurfaceTexture) -> Unit)? = null

    fun getLogMessages(): List<String> = logMessages.toList()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logMessages.add(msg)
        DebugLog.log("GL", msg)
        while (logMessages.size > 200) logMessages.poll()
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
            if (framesWithCameraData < 2) {
                DebugLog.log("FRAME", "onFrameAvailable FIRED (dataSoFar=$framesWithCameraData)")
            }
        }
    }

    // --- GLSurfaceView.Renderer ---

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        log(">>> onSurfaceCreated() START")
        log("  thread: ${Thread.currentThread().name}")

        log("  [1/8] reading GL strings...")
        val version = GLES30.glGetString(GLES30.GL_VERSION) ?: "unknown"
        val vendor = GLES30.glGetString(GLES30.GL_VENDOR) ?: "unknown"
        val rendererStr = GLES30.glGetString(GLES30.GL_RENDERER) ?: "unknown"
        log("  GL_VERSION: $version")
        log("  GL_VENDOR: $vendor")
        log("  GL_RENDERER: $rendererStr")

        log("  [2/8] enabling GL_TEXTURE_EXTERNAL_OES")
        GLES30.glEnable(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        log("  [2/8] OK")

        log("  [3/8] creating OES external texture...")
        val textures = IntArray(1)
        GLES30.glGenTextures(1, textures, 0)
        cameraTextureId = textures[0]
        log("  glGenTextures => cameraTextureId = $cameraTextureId")

        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTextureId)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        log("  [3/8] OK - texture $cameraTextureId configured")

        log("  [4/8] creating SurfaceTexture from GL texture $cameraTextureId...")
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId)
        log("  SurfaceTexture object created")

        log("  [4b/8] setting onFrameAvailableListener (NO handler = any thread)...")
        cameraSurfaceTexture!!.setOnFrameAvailableListener({ _ ->
            onCameraFrameAvailable()
        })
        log("  [4b/8] listener registered OK")

        log("  [5/8] compiling shaders...")
        pipeline.init()
        log("  [5/8] OK - ${pipeline.getProgramCount()} programs compiled")

        log("  [6/8] notifying onSurfaceTextureReady callback...")
        val st = cameraSurfaceTexture
        if (st != null && onSurfaceTextureReady != null) {
            log("  [6/8] invoking callback with SurfaceTexture...")
            onSurfaceTextureReady!!.invoke(st)
            log("  [6/8] callback returned OK")
        } else {
            log("  [6/8] WARNING: st=$st callback=$onSurfaceTextureReady")
        }

        log("  [7/8] checking GL errors...")
        var err = GLES30.glGetError()
        var errCount = 0
        while (err != GLES30.GL_NO_ERROR && errCount < 5) {
            log("  GL ERROR after init: 0x${err.toString(16)}")
            err = GLES30.glGetError()
            errCount++
        }
        if (errCount == 0) log("  [7/8] OK - no GL errors")

        log("  [8/8] GL surface created successfully!")
        log("<<< onSurfaceCreated() COMPLETE")
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        log(">>> onSurfaceChanged($width x $height)")
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)
        log("<<< onSurfaceChanged() done, viewport set")
    }

    override fun onDrawFrame(gl: GL10?) {
        drawFrameCount++
        var gotFrame = false

        // Check for camera frame
        synchronized(frameLock) {
            if (frameAvailable) {
                if (cameraSurfaceTexture != null) {
                    try {
                        cameraSurfaceTexture!!.updateTexImage()
                        cameraSurfaceTexture!!.getTransformMatrix(textureMatrix)
                        gotFrame = true
                        framesWithCameraData++
                    } catch (e: Exception) {
                        if (framesWithCameraData == 0) {
                            DebugLog.log("GL", "updateTexImage EXCEPTION: ${e.javaClass.simpleName}: ${e.message}")
                        }
                    }
                } else {
                    if (drawFrameCount < 10) {
                        DebugLog.log("GL", "frameAvailable=true but cameraSurfaceTexture is NULL!")
                    }
                }
                frameAvailable = false
            }
        }

        // Log first camera frame
        if (gotFrame && firstCameraFrameLogged == 0) {
            firstCameraFrameLogged = drawFrameCount
            log(">>> CAMERA FRAME RECEIVED on draw#$drawFrameCount! Pipeline will now show camera!")
        }

        // Periodic diagnostics every 60 frames (~2 sec)
        if (drawFrameCount % 60 == 1) {
            log("HEARTBEAT draw=$drawFrameCount w=$surfaceWidth h=$surfaceHeight tex=$cameraTextureId st=${cameraSurfaceTexture != null} data=$framesWithCameraData avail=$frameAvailable")
        }

        if (surfaceWidth <= 0 || surfaceHeight <= 0) return

        // === NORMAL PIPELINE RENDER ===
        // (test pattern removed — GL confirmed working, camera frames flowing)
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
    private var firstCameraFrameLogged = 0
}