package com.mapbox.vision.video.videoprocessor

import com.mapbox.vision.models.video.VideoStartStop

internal interface VideoProcessorListener {

    interface MultipleClips {
        fun onVideoClipsReady(videoClips: HashMap<String, VideoStartStop>, videoDir: String)
    }
    interface SingleClip {
        fun onVideoClipReady(clipPath: String, clip: VideoStartStop)
    }
}
