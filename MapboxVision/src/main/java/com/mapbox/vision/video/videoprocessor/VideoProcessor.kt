package com.mapbox.vision.video.videoprocessor

import android.util.Log
import com.mapbox.vision.utils.VideoUtils
import com.mapbox.vision.utils.threads.WorkThreadHandler
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

internal interface VideoProcessor {

    fun setVideoProcessorListener(videoProcessorListener: VideoProcessorListener)
    fun splitVideoToParts(
            parts: List<VideoPart>,
            fullVideoPath: String,
            saveDirPath: String,
            startRecordCoreMillis: Long
    )
    fun stop()

    data class VideoPart(val start: Long, val end: Long)

    class Impl : VideoProcessor {

        private var videoProcessorListener: VideoProcessorListener? = null

        private val workThreadHandler = WorkThreadHandler().also { it.start() }

        override fun setVideoProcessorListener(videoProcessorListener: VideoProcessorListener) {
            this.videoProcessorListener = videoProcessorListener
        }

        override fun splitVideoToParts(
                parts: List<VideoProcessor.VideoPart>,
                fullVideoPath: String,
                saveDirPath: String,
                startRecordCoreMillis: Long
        ) {
            workThreadHandler.post {
                val partsMap = HashMap<String, VideoProcessor.VideoPart>()
                for (part in parts) {
                    val relativeStart = part.start - startRecordCoreMillis
                    val relativeEnd = part.end - startRecordCoreMillis
                    if (relativeStart < 0 || relativeEnd < 0) {
                        Log.w(TAG, "Video clip bounds does not belong to current video, ignoring : $relativeStart - $relativeEnd")
                        continue
                    }
                    val outputClipPath = "$saveDirPath${relativeStart}_$relativeEnd.mp4"
                    val finalPart = VideoUtils.genVideoUsingMuxer(fullVideoPath, outputClipPath, relativeStart, relativeEnd)
                    partsMap[outputClipPath] = finalPart
                }
                videoProcessorListener?.onVideoPartsReady(
                        partsMap,
                        saveDirPath,
                        createJsonFileByParts(partsMap, saveDirPath, startRecordCoreMillis)
                )
            }
        }

        override fun stop() {
            workThreadHandler.stop()
        }

        private fun createJsonFileByParts(
                videoPartMap: HashMap<String, VideoProcessor.VideoPart>,
                saveDirPath: String,
                startRecordCoreMillis: Long
        ): String {
            val arr = JSONArray()
            for (videoPart in videoPartMap) {
                val jsonPath = JSONObject()
                val paths = videoPart.key.split("/")
                val name = paths[paths.size - 1]

                val start = ((videoPart.value.start + startRecordCoreMillis).toFloat() / 1000)
                val end = ((videoPart.value.end + startRecordCoreMillis).toFloat() / 1000)
                jsonPath.put("name", name)
                jsonPath.put("start", start)
                jsonPath.put("end", end)
                arr.put(jsonPath)
            }

            return try {
                val file = File(saveDirPath, "videos.json")
                val output = BufferedWriter(FileWriter(file))
                output.write(arr.toString())
                output.close()

                file.absolutePath
            } catch (e: Exception) {
                Log.e(TAG, "Can not create Json file : " + e.localizedMessage)
                ""
            }

        }

        companion object {
            private const val TAG = "VideoProcessorImpl"
        }
    }
}
