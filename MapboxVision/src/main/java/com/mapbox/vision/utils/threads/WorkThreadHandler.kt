package com.mapbox.vision.utils.threads

import android.os.Handler
import android.os.HandlerThread
import com.mapbox.vision.utils.VisionLogger

internal class WorkThreadHandler(private val handleThreadName: String = HANDLE_THREAD_NAME) : ThreadHandler {

    private lateinit var handlerThread: HandlerThread
    lateinit var handler: Handler

    private var started = false

    override fun post(task: () -> Unit) {
        if (!started) {
            VisionLogger.d(TAG, "The thread was not started")
            return
        }
        handler.post { task.invoke() }
    }

    override fun postDelayed(task: () -> Unit, delayMillis: Long) {
        if (!started) {
            VisionLogger.d(TAG, "The thread was not started")
            return
        }
        handler.postDelayed({ task.invoke() }, delayMillis)
    }

    override fun start() {
        handlerThread = HandlerThread(handleThreadName)
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        started = true
    }

    override fun stop() {
        started = false
        handler.removeCallbacksAndMessages(null)
        handlerThread.quitSafely()
        try {
            handlerThread.join()
        } catch (e: InterruptedException) {
            VisionLogger.e(e, TAG, "")
        }
    }

    override fun removeAllTasks() {
        handler.removeCallbacksAndMessages(null)
    }

    override fun isStarted(): Boolean = started

    companion object {

        private const val HANDLE_THREAD_NAME = "WorkingThread"
        private const val TAG = "WorkThreadHandler"
    }
}
