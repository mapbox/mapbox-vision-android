package com.mapbox.vision.models.videoclip

import com.mapbox.vision.mobile.core.models.VideoClipMetadata

internal data class VideoClipMetadata(val url: String, val keyValue: Map<String, String>)

internal fun VideoClipMetadata.toLocal() =
    VideoClipMetadata(
        url = url,
        keyValue = keyValue.toMap()
    )
