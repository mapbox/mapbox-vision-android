package com.mapbox.vision.sync.visionpro

import com.google.gson.Gson
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.sync.MetaGenerator
import com.mapbox.vision.utils.VisionLogger
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.HashMap

class VisionProMetaGenerator(private val gson: Gson) : MetaGenerator {

    override fun generateMeta(
        videoClipMap: HashMap<String, VideoClip>,
        saveDirPath: String
    ) {
        for (videoPart in videoClipMap) {
            val metadata = videoPart.value.metadata ?: continue
            val json = gson.toJson(metadata)
            val videoName = videoPart.key.substringAfterLast("/")
            try {
                val file = File(saveDirPath, "$videoName.json")
                val output = BufferedWriter(FileWriter(file))
                output.write(json)
                output.close()
            } catch (e: Exception) {
                VisionLogger.e(TAG_CLASS, "Can not create Json file : " + e.localizedMessage)
            }
        }
    }
}
