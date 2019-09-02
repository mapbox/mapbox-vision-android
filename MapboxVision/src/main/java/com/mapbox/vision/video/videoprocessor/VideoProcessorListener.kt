package com.mapbox.vision.video.videoprocessor

import com.mapbox.vision.models.videoclip.VideoClipStartStop

internal interface VideoProcessorListener {

    interface MultipleClips {
        fun onVideoClipsReady(videoClips: HashMap<String, VideoClipStartStop>, videoDir: String)
    }
    interface SingleClip {
        fun onVideoClipReady(clipPath: String, clip: VideoClipStartStop)
    }
}
