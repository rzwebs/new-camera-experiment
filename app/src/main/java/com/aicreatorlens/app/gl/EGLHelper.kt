package com.aicreatorlens.app.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import android.util.Log

object EGLHelper {
    private const val TAG = "EGLHelper"
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private val logMessages = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun getLogMessages(): List<String> = logMessages.toList()

    private fun log(msg: String) {
        Log.d(TAG, msg)
        logMessages.add(msg)
        while (logMessages.size > 30) logMessages.poll()
    }

    fun init(surface: Surface): Boolean {
        log("EGL init starting...")

        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            log("ERROR: eglGetDisplay returned NO_DISPLAY")
            return false
        }
        log("eglGetDisplay OK")

        val versions = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            log("ERROR: eglInitialize failed")
            return false
        }
        log("EGL initialized: ${versions[0]}.${versions[1]}")

        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            // EGL_OPENGL_ES3_BIT = 0x40 — use literal to avoid compile issues
            EGL14.EGL_RENDERABLE_TYPE, 0x40,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)) {
            log("ERROR: eglChooseConfig failed")
            return false
        }
        if (numConfigs[0] == 0) {
            log("ERROR: no EGL configs found")
            return false
        }
        log("EGL config chosen (${numConfigs[0]} available)")

        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )

        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0
        )
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            log("ERROR: eglCreateContext failed: ${EGL14.eglGetError()}")
            return false
        }
        log("EGL context created (GLES 3.0)")

        val surfAttribs = intArrayOf(
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, configs[0], surface, surfAttribs, 0
        )
        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            log("ERROR: eglCreateWindowSurface failed: ${EGL14.eglGetError()}")
            return false
        }
        log("EGL surface created")

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            log("ERROR: eglMakeCurrent failed: ${EGL14.eglGetError()}")
            return false
        }
        log("EGL context made current — READY")

        return true
    }

    fun swapBuffers(): Boolean {
        return eglDisplay?.let { EGL14.eglSwapBuffers(it, eglSurface) } ?: false
    }

    fun release() {
        eglDisplay?.let { d ->
            eglSurface?.let { s ->
                EGL14.eglDestroySurface(d, s)
            }
            eglContext?.let { c ->
                EGL14.eglDestroyContext(d, c)
            }
            EGL14.eglTerminate(d)
        }
        eglDisplay = null
        eglContext = null
        eglSurface = null
    }

    fun getContext(): EGLContext? = eglContext
}