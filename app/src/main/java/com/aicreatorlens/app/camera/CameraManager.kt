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
import com.aicreatorlens.app.ui.screens.DebugLog

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
        DebugLog.log("CAM", msg)
        while (logMessages.size > 50) logMessages.poll()
    }

    fun startCamera(surfaceTexture: SurfaceTexture) {
        log(">>> startCamera() BEGIN")
        try {
            log("step 1: getting Camera2 system service")
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager

            log("step 2: finding front camera ID")
            val cameraId = findFrontCameraId(cameraManager)
            if (cameraId == null) {
                log("ERROR: No front camera found!")
                return
            }
            log("step 2 done: front camera ID = $cameraId")

            log("step 3: starting HandlerThread")
            handlerThread = HandlerThread("CameraBackground").also { it.start() }
            handler = Handler(handlerThread!!.looper)
            log("step 3 done: handler thread started")

            log("step 4: getting camera characteristics")
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (configMap == null) {
                log("ERROR: SCALER_STREAM_CONFIGURATION_MAP is null!")
                return
            }

            log("step 5: choosing preview size")
            val sizes = configMap.getOutputSizes(SurfaceTexture::class.java)
            val previewSize = chooseOptimalSize(sizes, 720, 1280)
            log("step 5 done: preview ${previewSize.width}x${previewSize.height}")

            log("step 6: setDefaultBufferSize on SurfaceTexture")
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)

            log("step 7: creating Surface from SurfaceTexture")
            val surface = Surface(surfaceTexture)

            log("step 8: calling cameraManager.openCamera...")
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    log(">>> camera.onOpened() FIRED — device = ${camera.id}")
                    cameraDevice = camera
                    startPreview(camera, surface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    log("camera.onDisconnected()")
                    camera.close()
                    cameraDevice = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    log("ERROR camera.onError: code=$error")
                    camera.close()
                    cameraDevice = null
                }
            }, handler)
            log("step 8 done: openCamera called (async, waiting for callback)")
            log("<<< startCamera() returned — waiting for camera callbacks")

        } catch (e: Exception) {
            log("EXCEPTION in startCamera: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun startPreview(camera: CameraDevice, surface: Surface) {
        log(">>> startPreview() BEGIN")
        try {
            log("step 1: creating CaptureRequest.Builder (TEMPLATE_PREVIEW)")
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)

            log("step 2: adding surface target")
            captureRequestBuilder.addTarget(surface)

            log("step 3: setting AE mode = ON")
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)

            log("step 4: setting AF mode = CONTINUOUS_PICTURE")
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

            log("step 5: setting AWB mode = AUTO")
            captureRequestBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

            log("step 6: creating CaptureSession...")
            captureSession = camera.createCaptureSession(
                listOf(surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        log(">>> CaptureSession.onConfigured() FIRED")
                        captureSession = session
                        try {
                            log("step 7: building request")
                            val request = captureRequestBuilder.build()

                            log("step 8: calling setRepeatingRequest...")
                            session.setRepeatingRequest(request, object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureStarted(
                                    session: CameraCaptureSession,
                                    request: CaptureRequest,
                                    timestamp: Long,
                                    frameNumber: Long
                                ) {
                                    if (frameNumber <= 2L) {
                                        log(">>> onCaptureStarted: frame=$frameNumber ts=$timestamp")
                                    }
                                }
                            }, handler)
                            log("step 8 done: setRepeatingRequest SUCCESS")
                            log("<<< startPreview() COMPLETE — camera is streaming!")
                        } catch (e: Exception) {
                            log("ERROR setRepeatingRequest: ${e.javaClass.simpleName}: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        log("ERROR CaptureSession.onConfigureFailed!")
                    }
                },
                handler
            )
            log("step 6 done: createCaptureSession called (async)")

        } catch (e: Exception) {
            log("EXCEPTION in startPreview: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun findFrontCameraId(manager: android.hardware.camera2.CameraManager): String? {
        log("scanning camera IDs...")
        for (id in manager.cameraIdList) {
            val characteristics = manager.getCameraCharacteristics(id)
            val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
            log("  camera $id: facing=$facing")
            if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                return id
            }
        }
        return null
    }

    private fun chooseOptimalSize(sizes: Array<Size>, targetWidth: Int, targetHeight: Int): Size {
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
        log("stopCamera() called")
        try {
            captureSession?.close()
            captureSession = null
            cameraDevice?.close()
            cameraDevice = null
            handlerThread?.quitSafely()
            handlerThread = null
            handler = null
            log("stopCamera() done")
        } catch (e: Exception) {
            log("Error stopping camera: ${e.message}")
        }
    }
}