package com.mapbox.vision.utils.drawer.detections

import android.graphics.Bitmap
import com.mapbox.vision.mobile.models.detection.Detection

interface DetectionDrawer {
    
    fun draw(bitmap: Bitmap, detections: Array<Detection>)
}
