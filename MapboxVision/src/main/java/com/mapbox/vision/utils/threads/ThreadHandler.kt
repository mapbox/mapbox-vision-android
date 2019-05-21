package com.mapbox.vision.utils.threads

interface ThreadHandler {

    fun post(task: () -> Unit)

    fun postDelayed(task: () -> Unit, delayMillis: Long)

    fun start()

    fun stop()

    fun removeAllTasks()

    fun isStarted(): Boolean
}

