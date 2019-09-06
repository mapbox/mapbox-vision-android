package com.mapbox.vision.sync.visionpro

import com.google.gson.Gson
import com.mapbox.vision.models.video.VideoMetadata
import com.mapbox.vision.sync.SyncClient
import com.mapbox.vision.sync.SyncManager
import com.mapbox.vision.sync.filemanager.SyncFileHandler
import com.mapbox.vision.sync.util.TotalBytesCounter
import com.mapbox.vision.sync.util.VisionProEnvironment
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.prefs.TotalBytesCounterPrefs
import com.mapbox.vision.utils.threads.WorkThreadHandler
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

internal class VisionProSyncManager(
    private val gson: Gson,
    private val syncClient: SyncClient<OkHttpClient, VisionProEnvironment>,
    private val syncQueue: VisionProQueue,
    syncFileHandler: SyncFileHandler<VisionProEnvironment>
) : SyncManager.SyncManagerBase<VisionProEnvironment>(syncQueue, syncFileHandler, MAX_DIR_SIZE) {

    private val httpClient = syncClient.client
        .newBuilder()
        .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.HEADERS))
        .build()

    private val totalBytesCounter = TotalBytesCounter.Impl(
        sessionMaxBytes = 70 * 1024 * 1024 /* 70 mb */,
        totalBytesCounterPrefs = TotalBytesCounterPrefs.Impl("visionPro")
    )

    private val threadHandler = WorkThreadHandler()

    private val isInProgress = AtomicBoolean(false)

    @Volatile
    private var countOfUnsuccessfulRequests = 0

    companion object {
        private const val TAG = "VisionProSyncManager"

        private const val MAX_DIR_SIZE = 300 * 1024 * 1024L // 300 MB
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

    override fun onNewElement() {
        super.onNewElement()
        processQueue()
    }

    private fun createRequest(
        videoPath: String,
        videoFile: File,
        metadata: VideoMetadata
    ): Request {
        val fileName = videoPath.substringAfter("/")
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                RequestBody.create(MediaType.parse("video/mp4"), File(videoPath))
            )
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
        syncQueue.nextForSync()?.let { sync(it) }
    }

    private fun sync(item: Pair<String, VideoMetadata>) {
        val (videoPath, metadata) = item

        if (isInProgress.get()) {
            return
        }
        val videoFile = File(item.first)

        videoFile.let {
            if (!it.exists()) {
                syncQueue.removeFromQueue(item)
                processQueue()
                return
            }
        }

        val request = createRequest(videoPath, videoFile, metadata)
        val contentLength = request.body()?.contentLength() ?: 0

        if (!totalBytesCounter.fitInLimitCurrentSession(contentLength)) {
            threadHandler.postDelayed({
                sync(item)
            }, totalBytesCounter.millisToNextSession())
            return
        } else {
            totalBytesCounter.trackSentBytes(contentLength)
        }

        if (isInProgress.get()) {
            return
        }
        isInProgress.set(true)

        processRequest(request, item)
    }

    private fun processRequest(request: Request, item: Pair<String, VideoMetadata>) {
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                VisionLogger.e(e, TAG)

                isInProgress.set(false)

                threadHandler.postDelayed({
                    processQueue()
                }, TimeUnit.SECONDS.toMillis(3))
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    countOfUnsuccessfulRequests = 0
                    syncQueue.removeFromQueue(item)
                } else {
                    if (++countOfUnsuccessfulRequests > MAX_COUNT_OF_UNSUCCESSFUL_REQUEST) {
                        countOfUnsuccessfulRequests = 0

                        syncQueue.removeFromQueue(item)

                        VisionLogger.e(TAG, "Cannot sync vision pro: ${response.body()?.string()}")
                    }
                }
                isInProgress.set(false)
                processQueue()
            }
        })
    }
}
