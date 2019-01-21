package com.mapbox.vision.video.videosource

interface VideoSource {
    fun getSourceWidth(): Int

    fun getSourceHeight(): Int

    fun attach(videoSourceListener: VideoSourceListener)

    fun detach()

    interface WithProgress {
        fun setProgress(timestampMillis: Long)

        fun getProgress(): Long
    }
}
