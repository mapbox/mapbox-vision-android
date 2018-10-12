package com.mapbox.vision.video.videosource.camera

import android.graphics.Bitmap


interface RenderActionsListener {

    fun getRgbBytesArray(): ByteArray

    fun getBitmap(): Bitmap

    fun onDataReady()
}