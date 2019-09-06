package com.mapbox.vision.sync.visionpro

object VisionProUtil {
    private const val JSON_TYPE = "json"

    fun provideJsonNameByVideoName(videoName: String) = "$videoName.$JSON_TYPE"
}
