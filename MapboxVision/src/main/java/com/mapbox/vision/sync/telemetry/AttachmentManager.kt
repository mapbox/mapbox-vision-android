package com.mapbox.vision.sync.telemetry

import com.mapbox.android.telemetry.AttachmentMetadata
import com.mapbox.android.telemetry.Event
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.android.telemetry.VisionEventFactory
import okhttp3.MediaType

internal class AttachmentManager(private val mapboxTelemetry: MapboxTelemetry) {

    private val visionEventFactory = VisionEventFactory()
    private val attachment = visionEventFactory.createAttachment(Event.Type.VIS_ATTACHMENT)

    fun addFileAttachment(filePath: String, mediaType: MediaType, attachmentMetadata: AttachmentMetadata) {
        attachment.addAttachment(
                visionEventFactory.createFileAttachment(
                        filePath,
                        mediaType,
                        attachmentMetadata
                )
        )
    }

    fun pushEvent() {
        mapboxTelemetry.push(attachment)
    }
}
