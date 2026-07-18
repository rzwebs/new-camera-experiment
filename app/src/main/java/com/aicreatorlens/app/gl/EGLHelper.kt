package com.aicreatorlens.app.gl

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.view.Surface
import android.util.Log
import com.aicreatorlens.app.ui.screens.DebugLog

object EGLHelper {
    private const val TAG = "EGLHelper"
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    fun init(surface: Surface): Boolean {
        DebugLog.log("EGL", ">>> init() BEGIN")
        Log.d(TAG, "init starting...")

        DebugLog.log("EGL", "step 1: eglGetDisplay")
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay === EGL14.EGL_NO_DISPLAY) {
            DebugLog.log("EGL", "ERROR: eglGetDisplay = NO_DISPLAY")
            return false
        }
        DebugLog.log("EGL", "step 1 done")

        DebugLog.log("EGL", "step 2: eglInitialize")
        val versions = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            DebugLog.log("EGL", "ERROR: eglInitialize failed")
            return false
        }
        DebugLog.log("EGL", "step 2 done: EGL ${versions[0]}.${versions[1]}")

        DebugLog.log("EGL", "step 3: eglChooseConfig")
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, 0x40, // EGL_OPENGL_ES3_BIT
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, numConfigs, 0)) {
            DebugLog.log("EGL", "ERROR: eglChooseConfig failed")
            return false
        }
        if (numConfigs[0] == 0) {
            DebugLog.log("EGL", "ERROR: 0 configs found")
            return false
        }
        DebugLog.log("EGL", "step 3 done: ${numConfigs[0]} config(s)")

        DebugLog.log("EGL", "step 4: eglCreateContext (GLES 3.0)")
        val ctxAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
        if (eglContext === EGL14.EGL_NO_CONTEXT) {
            DebugLog.log("EGL", "ERROR: eglCreateContext failed, err=${EGL14.eglGetError()}")
            return false
        }
        DebugLog.log("EGL", "step 4 done: context created")

        DebugLog.log("EGL", "step 5: eglCreateWindowSurface")
        val surfAttribs = intArrayOf(EGLExt.EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfAttribs, 0)
        if (eglSurface === EGL14.EGL_NO_SURFACE) {
            DebugLog.log("EGL", "ERROR: eglCreateWindowSurface failed, err=${EGL14.eglGetError()}")
            return false
        }
        DebugLog.log("EGL", "step 5 done: surface created")

        DebugLog.log("EGL", "step 6: eglMakeCurrent")
        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            DebugLog.log("EGL", "ERROR: eglMakeCurrent failed, err=${EGL14.eglGetError()}")
            return false
        }
        DebugLog.log("EGL", "step 6 done: context CURRENT")
        DebugLog.log("EGL", "<<< init() COMPLETE — all OK")
        return true
    }

    fun swapBuffers(): Boolean {
        return eglDisplay?.let { EGL14.eglSwapBuffers(it, eglSurface) } ?: false
    }

    fun release() {
        eglDisplay?.let { d ->
            eglSurface?.let { s -> EGL14.eglDestroySurface(d, s) }
            eglContext?.let { c -> EGL14.eglDestroyContext(d, c) }
            EGL14.eglTerminate(d)
        }
        eglDisplay = null
        eglContext = null
        eglSurface = null
    }

    fun getContext(): EGLContext? = eglContext
}