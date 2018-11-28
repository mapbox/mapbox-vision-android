package com.mapbox.vision.video.videosource.camera

import android.util.Size

internal fun chooseOptimalCameraResolution(
        supportedSizes: List<Size>,
        desiredSize: Size
): Size {
    val minDimension = Math.min(desiredSize.width, desiredSize.height)

    val bigEnough = mutableListOf<Size>()
    for (option in supportedSizes) {
        if (option == desiredSize) {
            return desiredSize
        }

        if (option.height >= minDimension && option.width >= minDimension) {
            bigEnough.add(option)
        }
    }

    // Pick the smallest of those, assuming we found any
    return bigEnough.minBy { it.width.toLong() * it.height }
           ?: supportedSizes.first()
}
