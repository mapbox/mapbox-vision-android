package com.mapbox.vision.video.videosource

interface Progress {
    fun setProgress(timestampMillis: Long)
    fun getProgress(): Long

    fun pause()
    fun resume()
}

interface VideoSource {
    fun attach(videoSourceListener: VideoSourceListener)

    fun detach()

    interface WithProgress : VideoSource, Progress
}
