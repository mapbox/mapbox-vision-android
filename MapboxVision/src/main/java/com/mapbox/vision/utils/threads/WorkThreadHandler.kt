package com.mapbox.vision.utils.threads

import android.os.Handler
import android.os.HandlerThread
import android.util.Log

internal class WorkThreadHandler(private val handleThreadName: String = HANDLE_THREAD_NAME) : ThreadHandler {

    private lateinit var workThread: HandlerThread
    private lateinit var workThreadHandler: Handler

    private var started = false

    override fun post(task: () -> Unit) {
        if (!started) {
            Log.e(TAG, "The thread was not started")
            return
        }
        workThreadHandler.post { task.invoke() }
    }

    override fun postDelayed(task: () -> Unit, delayMillis: Long) {
        if (!started) {
            Log.e(TAG, "The thread was not started")
            return
        }
        workThreadHandler.postDelayed({ task.invoke() }, delayMillis)
    }

    override fun start() {
        workThread = HandlerThread(handleThreadName)
        workThread.start()
        workThreadHandler = Handler(workThread.looper)
        started = true
    }

    override fun stop() {
        started = false
        workThreadHandler.removeCallbacksAndMessages(null)
        workThread.quitSafely()
        try {
            workThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    override fun removeAllTasks() {
        workThreadHandler.removeCallbacksAndMessages(null)
    }

    override fun isStarted(): Boolean = started

    companion object {

        private const val HANDLE_THREAD_NAME = "WorkingThread"
        private const val TAG = "WorkThreadHandler"
    }
}
