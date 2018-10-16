package com.mapbox.vision.utils.threads

import android.os.Handler
import android.os.Looper
import android.util.Log

class MainThreadHandler : ThreadHandler {

    private val TAG = "MainThreadHandler"

    private var started = false

    override fun isStarted(): Boolean = started

    private val uiHandler = Handler(Looper.getMainLooper())

    override fun post(task: () -> Unit) {
        if(!started) {
            Log.e(TAG, "The thread was not started")
            return
        }
        uiHandler.post { task.invoke() }
    }

    override fun postDelayed(task: () -> Unit, delayMillis: Long) {
        if(!started) {
            Log.e(TAG, "The thread was not started")
            return
        }
        uiHandler.postDelayed({ task.invoke() }, delayMillis)
    }

    override fun start() {
        // Do nothing
        started = true
    }

    override fun stop() {
        if(!started) {
            Log.e(TAG, "The thread was not started")
            return
        }
        uiHandler.removeCallbacksAndMessages(null)
        started = false
    }

    override fun removeAllTasks() {
        uiHandler.removeCallbacksAndMessages(null)
    }
}
