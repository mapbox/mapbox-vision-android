package com.mapbox.vision.video.videosource

import com.mapbox.vision.utils.observable.Observable

interface Progress {
    fun setProgress(timestampMillis: Long)
    fun getProgress(): Long

    fun pause()
    fun resume()
}

interface VideoSource : Observable<VideoSourceListener> {
    fun attach(videoSourceListener: VideoSourceListener)

    fun detach()

    interface WithProgress : VideoSource, Progress
}
