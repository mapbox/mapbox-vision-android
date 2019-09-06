package com.mapbox.vision.models.video

import com.mapbox.vision.mobile.core.models.VideoClipMetadata

internal data class VideoMetadata(val url: String, val keyValue: Map<String, String>)

internal fun VideoClipMetadata.toLocal() =
    VideoMetadata(
        url = url,
        keyValue = keyValue.toMap()
    )
