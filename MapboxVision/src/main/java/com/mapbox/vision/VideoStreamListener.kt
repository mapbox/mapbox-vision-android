package com.mapbox.vision

interface VideoStreamListener {

    fun onNewFrame(byteArray: ByteArray)
}
