package com.mapbox.vision.sync.syncmanager

import com.google.gson.Gson
import com.google.gson.stream.JsonReader
import com.mapbox.vision.mobile.core.models.VideoClipMetadata
import com.mapbox.vision.sync.telemetry.TotalBytesCounter
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.threads.WorkThreadHandler
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class VisionProSyncManager(private val gson: Gson) : SyncManager {

    private val httpClient = OkHttpClient
        .Builder()
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
        .build()

    private val totalBytesCounter = TotalBytesCounter.Impl(sessionMaxBytes = 1 * 1024 * 1024 * 1024 /* 1 GB */)

    private val threadHandler = WorkThreadHandler()

    private val queue = ConcurrentLinkedQueue<Pair<String, VideoClipMetadata>>()
    private val isInProgress = AtomicBoolean(false)

    @Volatile
    private var countOfUnsuccessfulRequests = 0

    companion object {
        private const val TAG = "VisionProSyncManager"

        private const val MAX_COUNT_OF_UNSUCCESSFUL_REQUEST = 2
    }

    override fun start() {
        if (threadHandler.isStarted()) {
            return
        }

        threadHandler.start()

        processQueue()
    }

    override fun stop() {
        if (!threadHandler.isStarted()) {
            return
        }

        threadHandler.stop()
    }

    override fun syncSessionDir(path: String) {
        threadHandler.post {
            val filesList = File(path).listFiles()
            val jsonFilesList = filesList.filter { it.name.endsWith(".json") }
            val videoFilesList = filesList.filter { it.name.endsWith(".mp4") }

            jsonFilesList.forEach { jsonFile ->
                videoFilesList.find { videoFile ->
                    jsonFile.name.substringBeforeLast(".json") == videoFile.name
                }?.let { findVideoFile ->
                    val metadata = gson.fromJson<VideoClipMetadata>(
                        JsonReader(FileReader(jsonFile)),
                        VideoClipMetadata::class.java
                    )
                    if (metadata != null) {
                        queue.add(findVideoFile.absolutePath to metadata)
                    }
                }
            }
            processQueue()
        }
    }

    private fun removeFiles(path: String) {
            val jsonPath = "${path.substringAfter("/")}.json"
            val jsonFile = File(jsonPath)
            val videoFile = File(path)

            jsonFile.delete()
            videoFile.delete()
    }

    private fun createRequest(videoPath: String, videoFile: File, metadata: VideoClipMetadata): Request {
        val fileName = videoPath.substringAfter("/")
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", fileName, RequestBody.create(MediaType.parse("video/mp4"), File(videoPath)))
            .let { builder ->
                metadata.keyValue.forEach { (key, value) ->
                    builder.addFormDataPart(key, value)
                }
                return@let builder
            }
            .build()

        return Request.Builder()
            .url(metadata.url)
            .post(requestBody)
            .build()
    }

    private fun processQueue() {
        val item = queue.peek() ?: return
        val (videoPath, metadata) = item

        if (isInProgress.get()) {
            return
        }
        val file = File(videoPath).let {
            if (it.exists()) {
                it
            } else {
                queue.remove(item)
                return
            }
        }
        val request = createRequest(videoPath, file, metadata)
        val contentLength = request.body()?.contentLength() ?: 0

        if (!totalBytesCounter.trackSentBytes(contentLength)) {
            threadHandler.postDelayed({
                processQueue()
            }, totalBytesCounter.millisToNextSession())
        }

        isInProgress.set(true)

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                VisionLogger.e(e, TAG)

                isInProgress.set(false)

                threadHandler.postDelayed({
                    processQueue()
                }, TimeUnit.SECONDS.toMillis(1))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    countOfUnsuccessfulRequests = 0

                    queue.remove(item)
                    removeFiles(item.first)
                } else {
                    if (++countOfUnsuccessfulRequests > MAX_COUNT_OF_UNSUCCESSFUL_REQUEST) {
                        countOfUnsuccessfulRequests = 0

                        queue.remove(item)
                        removeFiles(item.first)

                        totalBytesCounter.trackSentBytes(-contentLength)

                        VisionLogger.e(TAG, "Cannot sync vision pro: ${response.body()?.string()}")
                    }
                }
                isInProgress.set(false)
                processQueue()
            }
        })
    }
}