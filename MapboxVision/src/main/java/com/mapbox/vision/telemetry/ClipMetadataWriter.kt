package com.mapbox.vision.telemetry

import com.google.gson.Gson
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.utils.VisionLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.HashMap

interface ClipMetadataWriter {

    fun createJsonFileByParts(
        videoClipMap: HashMap<String, VideoClip>,
        saveDirPath: String,
        startRecordCoreMillis: Long
    ): String

    class Telemetry : ClipMetadataWriter {
        override fun createJsonFileByParts(
            videoClipMap: HashMap<String, VideoClip>,
            saveDirPath: String,
            startRecordCoreMillis: Long
        ): String {
            val arr = JSONArray()
            for (videoPart in videoClipMap) {
                val jsonPath = JSONObject()
                val paths = videoPart.key.split("/")
                val name = paths[paths.size - 1]

                val startSeconds = startRecordCoreMillis / 1000f + videoPart.value.startSeconds
                val endSeconds = startRecordCoreMillis / 1000f + videoPart.value.endSeconds
                jsonPath.put("name", name)
                jsonPath.put("start", startSeconds)
                jsonPath.put("end", endSeconds)
                arr.put(jsonPath)
            }

            return try {
                val file = File(saveDirPath, "videos.json")
                val output = BufferedWriter(FileWriter(file))
                output.write(arr.toString())
                output.close()

                file.absolutePath
            } catch (e: Exception) {
                VisionLogger.e(TAG_CLASS, "Can not create Json file : " + e.localizedMessage)
                ""
            }
        }
    }

    class VisionPro : ClipMetadataWriter {

        private val gson = Gson()

        override fun createJsonFileByParts(
            videoClipMap: HashMap<String, VideoClip>,
            saveDirPath: String,
            startRecordCoreMillis: Long
        ): String {
            for (videoPart in videoClipMap) {
                val metadata = videoPart.value.metadata ?: continue
                val json = gson.toJson(metadata)
                val videoName = videoPart.key.substringAfterLast("/")//.substringBefore(".mp4")
                try {
                    val file = File(saveDirPath, "$videoName.json")
                    val output = BufferedWriter(FileWriter(file))
                    output.write(json)
                    output.close()
                } catch (e: Exception) {
                    VisionLogger.e(TAG_CLASS, "Can not create Json file : " + e.localizedMessage)
                }
            }
            return ""
        }

    }
}