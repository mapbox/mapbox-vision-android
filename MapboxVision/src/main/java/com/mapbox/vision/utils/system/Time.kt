package com.mapbox.vision.utils.system

interface Time {

    fun millis(): Long

    object SystemImpl : Time {

        override fun millis(): Long = System.currentTimeMillis()
    }
}
