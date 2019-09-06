package com.mapbox.vision.sync

import com.mapbox.vision.models.video.VideoMetadata
import com.mapbox.vision.models.video.VideoStartStop
import java.util.HashMap

internal interface MetaGenerator {

    fun generateMeta(
        videoClipMap: HashMap<String, VideoStartStop>, saveDirPath: String
    )

    fun generateMeta(
        clipPath: String, clip: VideoStartStop, videoMetadata: VideoMetadata
    )
}
