package com.aicreatorlens.app.ui.screens

import android.util.Log

/**
 * Global debug log — any thread, any component can write here.
 * CameraScreen polls this every 300ms and displays on screen.
 */
object DebugLog {
    private const val TAG = "DebugLog"
    private val logs = java.util.concurrent.ConcurrentLinkedQueue<String>()

    fun log(tag: String, msg: String) {
        val line = "[$tag] $msg"
        Log.d(TAG, line)
        logs.add(line)
        // Keep last 80 lines
        while (logs.size > 80) logs.poll()
    }

    fun getLogs(): List<String> = logs.toList()

    fun clear() { logs.clear() }
}