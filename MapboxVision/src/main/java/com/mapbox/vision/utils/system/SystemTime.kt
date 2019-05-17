package com.mapbox.vision.utils.system

interface SystemTime {

    fun currentTimeMillis(): Long

    object Impl : SystemTime {

        override fun currentTimeMillis(): Long = System.currentTimeMillis()
    }
}