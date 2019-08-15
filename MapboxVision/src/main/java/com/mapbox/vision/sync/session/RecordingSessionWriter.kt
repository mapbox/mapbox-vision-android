package com.mapbox.vision.sync.session

import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.utils.file.RotatedBuffers
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import java.io.File

internal class RecordingSessionWriter(
    private val nativeVisionManager: NativeVisionManager,
    private val buffers: RotatedBuffers,
    private val sessionDir: String,
    private val videoRecorder: VideoRecorder
) : SessionWriter {

    override fun start() {
        File(sessionDir).mkdirs()
        nativeVisionManager.startTelemetrySavingSession(sessionDir)
        buffers.rotate()
        videoRecorder.startRecording(buffers.getBuffer())
    }

    override fun stop() {
        nativeVisionManager.stopTelemetrySavingSession()
        videoRecorder.stopRecording()
    }
}
