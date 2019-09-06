package com.mapbox.vision.sync.telemetry

import com.mapbox.android.telemetry.AttachmentListener
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.sync.SyncClient
import com.mapbox.vision.sync.SyncManager
import com.mapbox.vision.sync.filemanager.SyncFileHandler
import com.mapbox.vision.sync.util.TelemetryEnvironment
import com.mapbox.vision.sync.util.TotalBytesCounter
import com.mapbox.vision.utils.prefs.TotalBytesCounterPrefs
import com.mapbox.vision.utils.threads.WorkThreadHandler
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

internal class TelemetrySyncManager(
    private val syncQueue: TelemetryQueue,
    private val syncClient: SyncClient<MapboxTelemetry, TelemetryEnvironment>,
    syncFileHandler: SyncFileHandler<TelemetryEnvironment>
) : SyncManager.SyncManagerBase<TelemetryEnvironment>(syncQueue, syncFileHandler, MAX_TELEMETRY_SIZE), AttachmentListener {

    private val threadHandler = WorkThreadHandler()

    private val bytesTracker = TotalBytesCounter.Impl(
        sessionMaxBytes = 10 * 1024 * 1024 /* 10 mb */,
        totalBytesCounterPrefs = TotalBytesCounterPrefs.Impl("telemetry")
    )

    private val uploadInProgress = AtomicBoolean(false)

    companion object {
        private const val MAX_TELEMETRY_SIZE = 300 * 1024 * 1024L // 300 MB
    }

    init {
        syncClient.client.addAttachmentListener(this)
        syncClient.client.updateDebugLoggingEnabled(BuildConfig.DEBUG)
    }

    override fun start() {
        if (threadHandler.isStarted()) {
            return
        }

        threadHandler.start()
        uploadInProgress.set(false)

        processQueues()
    }

    override fun stop() {
        if (!threadHandler.isStarted()) {
            return
        }

        threadHandler.stop()
    }

    // override fun newCountry(country: Country) {
    //     if (currentCountry == country) {
    //         return
    //     }
    //
    //     // if (isRecording) {
    //     //     currentCountry = newCountry
    //     //     return
    //     // }
    //
    //     when {
    //         currentCountry == Country.Unknown && country != Country.Unknown -> {
    //             currentCountry = country
    //             configMapboxTelemetry()
    //             // checkCountryTelemetryDir()
    //         }
    //
    //         currentCountry != Country.Unknown && country != Country.Unknown -> {
    //             stop()
    //
    //             currentCountry = country
    //
    //             configMapboxTelemetry()
    //
    //             start()
    //             // checkCountryTelemetryDir()
    //         }
    //
    //         currentCountry != Country.Unknown && country == Country.Unknown -> {
    //             currentCountry = country
    //             stop()
    //         }
    //     }
    // }

    override fun onNewElement() {
        super.onNewElement()

        processQueues()
    }

    private fun processQueues() {
        if (uploadInProgress.get()) return
        uploadInProgress.set(true)
        val attachment = syncQueue.nextAttachment()

        when {
            attachment != null -> {
                sendAttachment(attachment)
            }
            else -> uploadInProgress.set(false)
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
        AttachmentManager(syncClient.client).apply {
            addFileAttachment(
                filePath = file.absolutePath,
                mediaType = attachmentProperties.mediaType,
                attachmentMetadata = attachmentProperties.metadata
            )
            pushEvent()
        }
    }

    override fun onAttachmentResponse(message: String?, code: Int, fileIds: MutableList<String>?) {
        fileIds?.forEach { zipFileId ->
            syncQueue.removeByFileId(zipFileId)
        }
        uploadInProgress.set(false)
        processQueues()
    }

    override fun onAttachmentFailure(message: String?, fileIds: MutableList<String>?) {
        uploadInProgress.set(false)
        processQueues()
    }
}
