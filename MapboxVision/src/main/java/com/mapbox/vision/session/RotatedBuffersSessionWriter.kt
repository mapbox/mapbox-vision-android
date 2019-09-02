package com.mapbox.vision.session

import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.models.videoclip.VideoClipsCombined
import com.mapbox.vision.models.videoclip.mapToTelemetry
import com.mapbox.vision.models.videoclip.mapToVisionPro
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
    private var coreSessionStartMillis = 0L

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
        generateCacheDirForCurrentTime()
        videoRecorder.startRecording(buffers.getBuffer())
        nativeVisionManager.startTelemetrySavingSession(sessionCacheDir)
        telemetryImageSaverImpl.start(sessionCacheDir)

        coreSessionStartMillis =
            TimeUnit.SECONDS.toMillis(nativeVisionManager.getCoreTimeSeconds().toLong())

        workingHandler.postDelayed({
            stopSession()
            startSession()
        }, SESSION_LENGTH_MILLIS)
    }

    private fun stopSession() {
        videoRecorder.stopRecording()
        nativeVisionManager.stopTelemetrySavingSession()
        telemetryImageSaverImpl.stop()

        val clipsCombined = nativeVisionManager.getClips().toClipsCombined()

        nativeVisionManager.resetClips()

        sessionWriterListener.onSessionStop(
            clips = clipsCombined,
            videoPath = buffers.getBuffer(),
            cachedTelemetryPath = sessionCacheDir,
            coreSessionStartMillis = coreSessionStartMillis
        )
        buffers.rotate()
    }

    private fun Array<VideoClip>.toClipsCombined(): VideoClipsCombined = this.let { clips ->
        val telemetryProClips =
            clips.filter { it.metadata == null }.map { it.mapToTelemetry() }.ifEmpty { null }
                ?.toTypedArray()
        val visionProClips =
            clips.filter { it.metadata != null }.mapNotNull { it.mapToVisionPro() }.ifEmpty { null }
                ?.toTypedArray()
        VideoClipsCombined(telemetryClips = telemetryProClips, visionProClips = visionProClips)
    }

    private fun generateCacheDirForCurrentTime() {
        sessionCacheDir =
            "${FileUtils.getAbsoluteDir(
                File(
                    rootCacheDir,
                    System.currentTimeMillis().toString()
                ).absolutePath
            )}/"
    }
}
