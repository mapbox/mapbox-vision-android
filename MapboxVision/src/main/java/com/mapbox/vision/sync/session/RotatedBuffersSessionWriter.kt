package com.mapbox.vision.sync.session

import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.sync.SessionWriterListener
import com.mapbox.vision.sync.telemetry.TelemetryImageSaverImpl
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.file.RotatedBuffers
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import java.io.File
import java.util.concurrent.TimeUnit

internal class RotatedBuffersSessionWriter(
    private val nativeVisionManager: NativeVisionManager,
    private val buffers: RotatedBuffers,
    private val rootCacheDir: String,
    private val videoRecorder: VideoRecorder,
    private val telemetryImageSaverImpl: TelemetryImageSaverImpl,
    private val sessionWriterListener: SessionWriterListener
) : SessionWriter {

    companion object {
        private val SESSION_LENGTH_MILLIS = TimeUnit.MINUTES.toMillis(5)
    }

    private val handler = WorkThreadHandler("Session")
    private var sessionCacheDir: String = ""
    private var startRecordCoreMillis = 0L

    override fun start() {
        if (!handler.isStarted()) {
            handler.start()
            generateCacheDirForCurrentTime()
            startSession()
        }
    }

    override fun stop() {
        if (handler.isStarted()) {
            handler.stop()
            stopSession()
        }
    }

    private fun startSession() {
        videoRecorder.startRecording(buffers.getBuffer())
        nativeVisionManager.startTelemetrySavingSession(sessionCacheDir)
        telemetryImageSaverImpl.start(sessionCacheDir)

        startRecordCoreMillis = (nativeVisionManager.getCoreTimeSeconds() * 1000).toLong()

        handler.postDelayed({
            stopSession()
            startSession()
        }, SESSION_LENGTH_MILLIS)
    }

    private fun stopSession() {
        videoRecorder.stopRecording()
        nativeVisionManager.stopTelemetrySavingSession()
        telemetryImageSaverImpl.stop()

        val clips = nativeVisionManager.getClips()
        nativeVisionManager.resetClips()

        sessionWriterListener.onSessionStop(
            clips = clips,
            videoPath = buffers.getBuffer(),
            outputPath = sessionCacheDir,
            sessionStartMillis = startRecordCoreMillis
        )
        buffers.rotate()
    }

    private fun generateCacheDirForCurrentTime() {
        sessionCacheDir =
            "${FileUtils.getAbsoluteDir(File(rootCacheDir, System.currentTimeMillis().toString()).absolutePath)}/"
    }
}
