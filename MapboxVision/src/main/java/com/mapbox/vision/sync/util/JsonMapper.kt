package com.mapbox.vision.sync.util

import com.google.gson.stream.JsonReader
import com.google.gson.Gson
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.utils.VisionLogger
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

internal class JsonMapper(private val gson: Gson) {

    inline fun <reified T> serialize(pojo: T, name: String, path: String): Boolean {
        try {
            val json = gson.toJson(pojo)

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


    inline fun <reified T> deserialize(filePath: String): T? =
        gson.fromJson<T>(
                JsonReader(FileReader(filePath)),
                T::class.java
            )
}
