package com.mapbox.vision.models.video

import com.mapbox.vision.mobile.core.models.video.VideoClipMetadata

internal data class VideoMetadata(val url: String, val keyValue: Map<String, String>)

internal fun VideoClipMetadata.mapToVideoMetadata(url: String) =
    VideoMetadata(
        url = url,
        keyValue = keyValue.toMap()
    )
