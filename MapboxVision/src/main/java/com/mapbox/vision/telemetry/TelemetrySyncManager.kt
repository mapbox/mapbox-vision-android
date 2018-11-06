package com.mapbox.vision.telemetry

import android.content.Context
import com.mapbox.android.telemetry.AttachmentListener
import com.mapbox.android.telemetry.AttachmentMetadata
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.utils.UuidUtil
import com.mapbox.vision.utils.file.ZipFileCompressorImpl
import com.mapbox.vision.utils.threads.WorkThreadHandler
import okhttp3.MediaType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean

internal interface TelemetrySyncManager {

    fun syncDataDir(path: String)
    fun reset()

    class Impl(
            private val mapboxTelemetry: MapboxTelemetry,
            private val context: Context
    ) : TelemetrySyncManager, AttachmentListener {

        private val zipQueue = ConcurrentLinkedQueue<AttachmentProperties>()
        private val videoQueue = ConcurrentLinkedQueue<AttachmentProperties>()
        private val threadHandler = WorkThreadHandler()
        private val fileCompressor = ZipFileCompressorImpl()
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US)
        private val bytesTracker = TotalBytesSentCounter()
        private val uuidUtil = UuidUtil(context)
        @Suppress("DEPRECATION")
        private val locale = context.resources.configuration.locale.country.toUpperCase()

        private val inProgress = AtomicBoolean(false)

        init {
            mapboxTelemetry.addAttachmentListener(this)
            threadHandler.start()
        }

        override fun syncDataDir(path: String) {
            val dirFile = File(path)

            removeDirectoriesOutsideMaxSize(dirFile.parentFile)

            (zipDataFiles(path) { file ->
                file.name.endsWith("bin") || file.name.endsWith("json")
            })?.let { zippedTelemetry ->
                zipQueue.add(zippedTelemetry.toAttachmentProperties(FORMAT_ZIP, TYPE_ZIP, MEDIA_TYPE_ZIP))
            }

            dirFile.listFiles { file ->
                file.name.endsWith("mp4")
            }?.forEach { videoFile ->
                videoQueue.add(
                        videoFile.toAttachmentProperties(FORMAT_MP4, TYPE_VIDEO, MEDIA_TYPE_MP4).also {
                            val parentTimestamp = videoFile.parentFile.name.toLong()
                            val interval = videoFile.name.substringBefore(".").split("_")
                            it.metadata.startTime = isoDateFormat.format(Date(parentTimestamp + interval[0].toLong()))
                            it.metadata.endTime = isoDateFormat.format(Date(parentTimestamp + interval[1].toLong()))
                        }
                )
            }

            processQueues()
        }

        private fun removeDirectoriesOutsideMaxSize(rootTelemetryDirectory: File) {
            val totalTelemetrySize = rootTelemetryDirectory.directorySizeRecursive()
            if (totalTelemetrySize > MAX_TELEMETRY_SIZE) {
                var bytesToRemove = totalTelemetrySize - MAX_TELEMETRY_SIZE
                val sortedTelemetryDirs = rootTelemetryDirectory
                        .listFiles()
                        .sortedBy { it.name }

                for (telemetryDir in sortedTelemetryDirs) {
                    bytesToRemove -= telemetryDir.directorySizeRecursive()
                    telemetryDir.deleteRecursively()
                    zipQueue.removeAll {
                        it.absolutePath.contains(telemetryDir.path)
                    }
                    videoQueue.removeAll {
                        it.absolutePath.contains(telemetryDir.path)
                    }
                    if (bytesToRemove <= 0) {
                        break
                    }
                }
            }

            threadHandler.removeAllTasks()
            inProgress.set(false)
        }

        private fun File.directorySizeRecursive(): Long = if (!isDirectory) {
            0
        } else {
            listFiles()
                    .map {
                        (if (it.isFile) it.length() else it.directorySizeRecursive())
                    }
                    .sum()
        }

        private fun File.toAttachmentProperties(format: String, type: String, mediaType: MediaType): AttachmentProperties {
            return AttachmentProperties(
                    absolutePath,
                    AttachmentMetadata(
                            name, hashCode().toString(), format, type,
                            "${dateFormat.format(Date(parentFile.name.toLong()))}_${locale}_${uuidUtil.uniqueId}_Android"
                    ),
                    mediaType
            )
        }

        private fun zipDataFiles(dirPath: String, filter: (File) -> Boolean): File? {
            val dirFile = File(dirPath)
            if (!dirFile.exists() || !dirFile.isDirectory) {
                return null
            }

            val output = File(dirPath, "telemetry.$FORMAT_ZIP")
            if (!output.exists()) {
                output.createNewFile()
            }
            val filesToZip = dirFile.listFiles(filter)
            if (filesToZip.isEmpty()) {
                return null
            }

            fileCompressor.compress(files = filesToZip, outFilePath = output.absolutePath)
            filesToZip.forEach {
                it.delete()
            }

            return output
        }

        private fun processQueues() {
            if (inProgress.get()) return
            inProgress.set(true)

            val zip = zipQueue.peek()
            val video = videoQueue.peek()
            when {
                zip != null -> sendAttachment(zip)
                video != null -> sendAttachment(video)
                else -> inProgress.set(false)
            }
        }

        private fun sendAttachment(attachmentProperties: AttachmentProperties) {
            val file = File(attachmentProperties.absolutePath)

            if (bytesTracker.trackSentBytes(file.length())) {
                sendFile(file, attachmentProperties)
            } else {
                threadHandler.postDelayed(
                        {
                            sendAttachment(attachmentProperties)
                        },
                        bytesTracker.millisToNextSession()
                )
            }
        }

        private fun sendFile(
                file: File,
                attachmentProperties: AttachmentProperties
        ) {
            AttachmentManager(mapboxTelemetry).apply {
                addFileAttachment(
                        filePath = file.absolutePath,
                        mediaType = attachmentProperties.mediaType,
                        attachmentMetadata = attachmentProperties.metadata
                )
                pushEvent()
            }
        }

        override fun reset() {
            zipQueue.clear()
            videoQueue.clear()
            threadHandler.removeAllTasks()
            inProgress.set(false)
        }

        override fun onAttachmentResponse(message: String?, code: Int, fileIds: MutableList<String>?) {
            fileIds?.forEach { zipFileId ->
                if (!zipQueue.removeFileById(zipFileId)) {
                    videoQueue.removeFileById(zipFileId)
                }
            }
            inProgress.set(false)
            processQueues()
        }

        private fun ConcurrentLinkedQueue<AttachmentProperties>.removeFileById(fileId: String): Boolean {
            val zipFile = firstOrNull { it.metadata.fileId == fileId }
            if (zipFile != null) {
                File(zipFile.absolutePath).delete()
            }
            remove(zipFile)

            return zipFile != null
        }

        override fun onAttachmentFailure(message: String?, fileIds: MutableList<String>?) {
            inProgress.set(false)
            processQueues()
        }

        companion object {
            private const val MAX_TELEMETRY_SIZE = 300 * 1024 * 1024L // 300 MB
        }
    }

    private class TotalBytesSentCounter {

        companion object {
            private const val SESSION_LENGTH_MILLIS = 60 * 60 * 1000L // one hour
            private const val MAX_BYTES_PER_SESSION = 30 * 1024 * 1024L // 30 MB
        }

        private var timestampSessionStart: Long = 0
        private var bytesSentSession: Long = 0

        fun trackSentBytes(bytes: Long): Boolean {

            val timestamp = System.currentTimeMillis()

            return when {
                timestampSessionStart + SESSION_LENGTH_MILLIS < timestamp -> {
                    timestampSessionStart = timestamp
                    bytesSentSession = bytes
                    true
                }
                bytesSentSession + bytes < MAX_BYTES_PER_SESSION -> {
                    bytesSentSession += bytes
                    true
                }
                else -> {
                    false
                }
            }
        }

        fun millisToNextSession() = (timestampSessionStart + SESSION_LENGTH_MILLIS - System.currentTimeMillis()).let { sessionLength ->
            when {
                sessionLength < 0 -> 0
                sessionLength > SESSION_LENGTH_MILLIS -> SESSION_LENGTH_MILLIS
                else -> sessionLength
            }
        }

    }

    private data class AttachmentProperties(
            val absolutePath: String,
            val metadata: AttachmentMetadata,
            val mediaType: MediaType
    )

    companion object {
        private const val TAG = "TelemetrySyncManager"

        private val MEDIA_TYPE_ZIP: MediaType = MediaType.parse("application/zip")!!
        private val MEDIA_TYPE_MP4: MediaType = MediaType.parse("video/mp4")!!
        private const val FORMAT_MP4 = "mp4"
        private const val FORMAT_ZIP = "zip"
        private const val TYPE_VIDEO = "video"
        private const val TYPE_ZIP = "zip"
    }
}
