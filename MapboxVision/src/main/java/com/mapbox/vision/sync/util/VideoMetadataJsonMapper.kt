package com.mapbox.vision.sync.util

import com.mapbox.vision.models.video.VideoMetadata

internal interface VideoMetadataJsonMapper {
    fun serialize(metadata: VideoMetadata, name: String, path: String): Boolean

    fun deserialize(filePath: String): VideoMetadata?

    class Impl(private val jsonMapper: JsonMapper) : VideoMetadataJsonMapper {

        override fun serialize(metadata: VideoMetadata, name: String, path: String): Boolean =
            jsonMapper.serialize(metadata, name, path)

        override fun deserialize(filePath: String): VideoMetadata? =
            jsonMapper.deserialize(filePath)
    }
}
