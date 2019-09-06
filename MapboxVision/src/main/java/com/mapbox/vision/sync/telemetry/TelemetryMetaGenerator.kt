package com.mapbox.vision.sync.telemetry

import com.mapbox.vision.models.video.VideoMetadata
import com.mapbox.vision.models.video.VideoStartStop
import com.mapbox.vision.sync.MetaGenerator
import com.mapbox.vision.utils.VisionLogger
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.HashMap

internal class TelemetryMetaGenerator : MetaGenerator {

    companion object {
        private const val TAG = "TelemetryMetaGenerator"
    }

    override fun generateMeta(
        clipPath: String,
        clip: VideoStartStop,
        videoMetadata: VideoMetadata
    ) = Unit

    override fun generateMeta(
        videoClipMap: HashMap<String, VideoStartStop>,
        saveDirPath: String
    ) {
        val arr = JSONArray()
        for (videoPart in videoClipMap) {
            val jsonPath = JSONObject()
            val paths = videoPart.key.split("/")
            val name = paths.last()

            val startSeconds = videoPart.value.startSecond
            val endSeconds = videoPart.value.endSecond
            jsonPath.put("name", name)
            jsonPath.put("start", startSeconds)
            jsonPath.put("end", endSeconds)
            arr.put(jsonPath)
        }

        try {
            val file = File(saveDirPath, "videos.json")
            val output = BufferedWriter(FileWriter(file))
            output.write(arr.toString())
            output.close()

            file.absolutePath
        } catch (e: Exception) {
            VisionLogger.e(TAG, "Can not create Json file : " + e.localizedMessage)
        }
    }

}
