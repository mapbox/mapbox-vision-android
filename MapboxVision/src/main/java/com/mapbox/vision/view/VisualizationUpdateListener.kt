package com.mapbox.vision.view

import android.graphics.Bitmap
import com.mapbox.vision.visionevents.events.detection.Detection

internal interface VisualizationUpdateListener {

    fun getCurrentMode() : VisualizationMode

    fun getBitmapBuffer() : Bitmap

    fun onDetectionsUpdated(detections: List<Detection>)

    fun onByteArrayUpdated()
}