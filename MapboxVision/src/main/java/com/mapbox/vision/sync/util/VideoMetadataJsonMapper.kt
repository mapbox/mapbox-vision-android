package com.mapbox.vision.sync.util

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.models.video.VideoMetadata
import com.mapbox.vision.utils.VisionLogger
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

internal interface VideoMetadataJsonMapper {
    fun serialize(metadata: VideoMetadata, name: String, path: String): Boolean

    fun deserialize(filePath: String): VideoMetadata?

    class Impl(private val gson: Gson) : VideoMetadataJsonMapper {

        override fun serialize(metadata: VideoMetadata, name: String, path: String): Boolean {
            try {
                val json = gson.toJson(metadata)

                val file = File(path, name)
                val output = BufferedWriter(FileWriter(file))

                output.write(json)
                output.close()
            } catch (e: Exception) {
                VisionLogger.e(TAG_CLASS, "Can not create Json file : " + e.localizedMessage)
                return false
            }
            return true
        }

        override fun deserialize(filePath: String): VideoMetadata? =
            gson.fromJson<VideoMetadata>(
                JsonReader(FileReader(filePath)),
                VideoMetadata::class.java
            )
    }
}
