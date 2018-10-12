package com.mapbox.vision.utils.drawer.detections

import android.graphics.Bitmap
import com.mapbox.vision.visionevents.events.detection.Detection

interface DetectionDrawer {
    
    fun draw(bitmap: Bitmap, detections: List<Detection>)
}