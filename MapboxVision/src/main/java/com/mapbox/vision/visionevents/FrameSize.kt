package com.mapbox.vision.visionevents

data class FrameSize(val width: Int, val height: Int) {

    operator fun times(factor: Float) = FrameSize(
        width = (width * factor).toInt(),
        height = (height * factor).toInt()
    )
}
