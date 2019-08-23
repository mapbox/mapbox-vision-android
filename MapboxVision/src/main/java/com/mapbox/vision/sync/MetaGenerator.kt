package com.mapbox.vision.sync

import com.mapbox.vision.mobile.core.models.VideoClip
import java.util.HashMap

interface MetaGenerator {

    fun generateMeta(
        videoClipMap: HashMap<String, VideoClip>,
        saveDirPath: String
    )
}
