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

    private val workingHandler = WorkThreadHandler("Session")
    private var sessionCacheDir: String = ""
    private var startRecordCoreMillis = 0L
    private var sessionTimeMillis = 0L

    override fun start() {
        if (!workingHandler.isStarted()) {
            workingHandler.start()
            startSession()
        }
    }

    override fun stop() {
        if (workingHandler.isStarted()) {
            workingHandler.stop()
            stopSession()
        }
    }

    private fun startSession() {
        sessionTimeMillis = System.currentTimeMillis()
        generateCacheDirForCurrentTime()
        videoRecorder.startRecording(buffers.getBuffer())
        nativeVisionManager.startTelemetrySavingSession(sessionCacheDir)
        telemetryImageSaverImpl.start(sessionCacheDir)

        startRecordCoreMillis = TimeUnit.SECONDS.toMillis(nativeVisionManager.getCoreTimeSeconds().toLong())

        workingHandler.postDelayed({
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
            sessionStartMillis = sessionTimeMillis
        )
        buffers.rotate()
    }

    private fun generateCacheDirForCurrentTime() {
        sessionCacheDir =
            "${FileUtils.getAbsoluteDir(File(rootCacheDir, sessionTimeMillis.toString()).absolutePath)}/"
    }
}
