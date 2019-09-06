package com.mapbox.vision.sync.telemetry

import android.app.Application
import com.mapbox.android.telemetry.AttachmentMetadata
import com.mapbox.vision.sync.SyncQueue
import com.mapbox.vision.sync.filemanager.SyncDirectoriesProvider
import com.mapbox.vision.sync.util.TelemetryEnvironment
import com.mapbox.vision.utils.UuidHolder
import com.mapbox.vision.utils.file.ZipFileCompressorImpl
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import okhttp3.MediaType

internal class TelemetryQueue(
    application: Application,
    syncDirectoriesProvider: SyncDirectoriesProvider<TelemetryEnvironment>,
    featureEnvironment: TelemetryEnvironment
) : SyncQueue.SyncQueueBase<TelemetryEnvironment>(application, syncDirectoriesProvider, featureEnvironment) {

    private val zipQueue = ConcurrentLinkedQueue<AttachmentProperties>()
    private val imageZipQueue = ConcurrentLinkedQueue<AttachmentProperties>()
    private val videoQueue = ConcurrentLinkedQueue<AttachmentProperties>()

    private val fileCompressor = ZipFileCompressorImpl()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ssZ", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US)

    private val uuidUtil = UuidHolder.Impl(application)
    @Suppress("DEPRECATION")
    private val language = application.resources.configuration.locale.language
    @Suppress("DEPRECATION")
    private val countryName = application.resources.configuration.locale.country

    companion object {
        private val MEDIA_TYPE_ZIP: MediaType = MediaType.parse("application/zip")!!
        private val MEDIA_TYPE_MP4: MediaType = MediaType.parse("video/mp4")!!

        private const val FORMAT_MP4 = "mp4"
        private const val FORMAT_ZIP = "zip"
        private const val TYPE_VIDEO = "video"
        private const val TYPE_ZIP = "zip"
    }

    override fun resetQueue() {
        zipQueue.clear()
        imageZipQueue.clear()
        videoQueue.clear()
    }

    override fun syncSessionDir(path: String) {
        val dirFile = File(path)

        zipDataFiles("telemetry", path) { file ->
            file.name.endsWith("bin") || file.name.endsWith("json")
        }?.let { zippedTelemetry ->
            zipQueue.add(
                zippedTelemetry.toAttachmentProperties(
                    FORMAT_ZIP,
                    TYPE_ZIP,
                    MEDIA_TYPE_ZIP
                )
            )
        }

        zipDataFiles("images", path) { file ->
            file.name.endsWith("png")
        }?.let { zippedTelemetry ->
            imageZipQueue.add(
                zippedTelemetry.toAttachmentProperties(
                    FORMAT_ZIP,
                    TYPE_ZIP,
                    MEDIA_TYPE_ZIP
                )
            )
        }

        dirFile.listFiles { file ->
            file.name.endsWith("mp4")
        }?.forEach { videoFile ->
            videoQueue.add(
                videoFile.toAttachmentProperties(
                    FORMAT_MP4,
                    TYPE_VIDEO,
                    MEDIA_TYPE_MP4
                ).also {
                    val parentTimestamp = videoFile.parentFile.name.toLong()
                    val interval = videoFile.name.substringBeforeLast(".").split("_")
                    val startMillis = (interval[0].toFloat() * 1000).toLong()
                    val endMillis = (interval[1].toFloat() * 1000).toLong()
                    it.metadata.startTime =
                        isoDateFormat.format(Date(parentTimestamp + startMillis))
                    it.metadata.endTime = isoDateFormat.format(Date(parentTimestamp + endMillis))
                }
            )
        }
    }

    fun removeByFileId(fileId: String) {
        if (zipQueue.removeByFileId(fileId))
            return
        if (imageZipQueue.removeByFileId(fileId)) {
            return
        }

        videoQueue.removeByFileId(fileId)
    }

    private fun ConcurrentLinkedQueue<AttachmentProperties>.removeByFileId(fileId: String): Boolean =
        this.firstOrNull { it.metadata.fileId == fileId }
            ?.also { attachment ->
                File(attachment.absolutePath).delete()
                remove(attachment)
            } != null

    fun nextAttachment(): AttachmentProperties? =
        zipQueue.peek() ?: imageZipQueue.peek() ?: videoQueue.peek()

    private fun File.toAttachmentProperties(
        format: String,
        type: String,
        mediaType: MediaType
    ): AttachmentProperties {
        val fileId = dateFormat.format(Date(parentFile.name.toLong()))

        return AttachmentProperties(
            absolutePath,
            AttachmentMetadata(
                name, "$fileId/$name", format, type,
                "${fileId}_${language}_${countryName}_${uuidUtil.uniqueId}_Android"
            ),
            mediaType
        )
    }

    private fun zipDataFiles(fileName: String, dirPath: String, filter: (File) -> Boolean): File? {
        val dirFile = File(dirPath)
        if (!dirFile.exists() || !dirFile.isDirectory) {
            return null
        }

        val filesToZip = dirFile.listFiles(filter)
        if (filesToZip.isEmpty()) {
            return null
        }

        val output = File(dirPath, "$fileName.$FORMAT_ZIP")
        if (!output.exists()) {
            output.createNewFile()
        }

        fileCompressor.compress(files = filesToZip, outFilePath = output.absolutePath)
        filesToZip.forEach {
            it.delete()
        }

        return output
    }
}

data class AttachmentProperties(
    val absolutePath: String,
    val metadata: AttachmentMetadata,
    val mediaType: MediaType
)
