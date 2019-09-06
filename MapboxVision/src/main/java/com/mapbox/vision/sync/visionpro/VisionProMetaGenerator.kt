package com.mapbox.vision.sync.visionpro

import com.mapbox.vision.models.video.VideoMetadata
import com.mapbox.vision.models.video.VideoStartStop
import com.mapbox.vision.sync.MetaGenerator
import com.mapbox.vision.sync.util.VideoMetadataJsonMapper
import java.io.File
import java.util.HashMap

internal class VisionProMetaGenerator(
    private val videoMetadataJsonMapper: VideoMetadataJsonMapper
) : MetaGenerator {

    override fun generateMeta(
        clipPath: String,
        clip: VideoStartStop,
        videoMetadata: VideoMetadata
    ) {
        val video = File(clipPath)

        videoMetadataJsonMapper.serialize(
            videoMetadata,
            VisionProUtil.provideJsonNameByVideoName(video.name),
            video.parent
        )
    }

    override fun generateMeta(videoClipMap: HashMap<String, VideoStartStop>, saveDirPath: String) =
        Unit
}
