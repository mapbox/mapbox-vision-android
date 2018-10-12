package com.mapbox.vision.utils.drawer.distance

import android.graphics.Bitmap
import com.mapbox.vision.models.DistanceToCar

interface DistanceDrawer {

    fun draw(bitmap: Bitmap, distanceToCar: DistanceToCar)
}