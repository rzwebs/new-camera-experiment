package com.aicreatorlens.app.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Raw Camera2 API manager — opens front camera, delivers frames to a SurfaceTexture.
 * No CameraX dependency.
 */
class CameraManager(private val context: Context) {

    private val TAG = "CameraManager"
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private val logMessages = ConcurrentLinkedQueue<String>()

    fun getLogMessages(): List<String> = logMessages.toList()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logMessages.add(msg)
        // Keep buffer bounded
        while (logMessages.size > 50) logMessages.poll()
    }

    /**
     * Opens the front camera and starts preview into the given SurfaceTexture.
     * Must be called after GL has created the SurfaceTexture.
     */
    fun startCamera(surfaceTexture: SurfaceTexture) {
        log("startCamera() called")
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
            val cameraId = findFrontCameraId(cameraManager)
            if (cameraId == null) {
                log("ERROR: No front camera found!")
                return
            }
            log("Front camera ID: $cameraId")

            // Start handler thread for camera callbacks
            handlerThread = HandlerThread("CameraBackground").also { it.start() }
            handler = Handler(handlerThread!!.looper)

            // Set default buffer size on SurfaceTexture
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val sizes = configMap!!.getOutputSizes(SurfaceTexture::class.java)
            val previewSize = chooseOptimalSize(sizes, 720, 1280)
            log("Preview size: ${previewSize.width}x${previewSize.height}")
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)

            val surface = Surface(surfaceTexture)

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    log("Camera OPENED")
                    cameraDevice = camera
                    startPreview(camera, surface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    log("Camera DISCONNECTED")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    log("Camera ERROR: $error")
                    camera.close()
                    cameraDevice = null
                }
            }, handler)
        } catch (e: Exception) {
            log("Exception in startCamera: ${e.message}")
            Log.e(TAG, "startCamera failed", e)
        }
    }

    private fun startPreview(camera: CameraDevice, surface: Surface) {
        log("startPreview() called")
        try {
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                // Auto exposure
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                // Auto focus (continuous)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                // Auto white balance
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
            }

            camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        log("CaptureSession CONFIGURED")
                        captureSession = session
                        try {
                            val request = captureRequestBuilder.build()
                            session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureStarted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    timestamp: Long,
                                    frameNumber: Long
                                ) {
                                    if (frameNumber == 1L) {
                                        log("First CAPTURE frame delivered!")
                                    }
                                }
                            }, handler)
                            log("setRepeatingRequest SUCCESS")
                        } catch (e: Exception) {
                            log("setRepeatingRequest FAILED: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        log("CaptureSession CONFIGURE FAILED")
                    }
                },
                handler
            )
        } catch (e: Exception) {
            log("startPreview exception: ${e.message}")
        }
    }

    private fun findFrontCameraId(manager: android.hardware.camera2.CameraManager): String? {
        for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return null
    }

    private fun chooseOptimalSize(sizes: Array<Size>, targetWidth: Int, targetHeight: Int): Size {
        // Find the closest size to target
        var bestSize = sizes[0]
        var bestDiff = Int.MAX_VALUE
        for (size in sizes) {
            val diff = Math.abs(size.width - targetWidth) + Math.abs(size.height - targetHeight)
            if (diff < bestDiff) {
                bestDiff = diff
                bestSize = size
            }
        }
        return bestSize
    }

    fun stopCamera() {
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            log("Camera stopped")
        } catch (e: Exception) {
            log("Error stopping camera: ${e.message}")
        }
    }
}