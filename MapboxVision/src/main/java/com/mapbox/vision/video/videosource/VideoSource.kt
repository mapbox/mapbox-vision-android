package com.mapbox.vision.video.videosource

import com.mapbox.vision.utils.observable.CompositeListener

interface Progress {
    fun setProgress(timestampMillis: Long)
    fun getProgress(): Long

    fun pause()
    fun resume()
}

interface VideoSource : CompositeListener<VideoSourceListener> {
    fun attach(videoSourceListener: VideoSourceListener)

    fun detach()

    override fun addListener(observer: VideoSourceListener) {}
    override fun removeListener(observer: VideoSourceListener) {}

    interface WithProgress : VideoSource, Progress
}
