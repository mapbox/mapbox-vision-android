package com.mapbox.vision.utils

import android.location.Location
import android.os.Build

fun Location.verticalAccuracyMetersIfSupported() =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        verticalAccuracyMeters
    } else {
        0.0F
    }
