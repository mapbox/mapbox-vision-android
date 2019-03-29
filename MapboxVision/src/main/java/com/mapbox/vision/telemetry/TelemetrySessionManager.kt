package com.mapbox.vision.telemetry

import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import java.io.File

internal interface TelemetrySessionManager {

    companion object {
        private const val RESTART_SESSION_RECORDING_DELAY_MILLIS = 5 * 60 * 1000L
    }

    fun start()
    fun stop()

    class Impl(
        private val nativeVisionManager: NativeVisionManager,
        private val rootTelemetryDir: String,
        private val videoRecorder: VideoRecorder,
        private val telemetryImageSaver: TelemetryImageSaver,
        private val onSessionEnded: (String, Long, String, Array<VideoClip>) -> Unit
    ) : TelemetrySessionManager {

        private val handler = WorkThreadHandler("Session")

        private var telemetryDir: String = ""
        private var startRecordCoreMillis = 0L

        override fun start() {
            handler.start()
            startSession()
        }

        override fun stop() {
            handler.stop()
        }

        private fun startSession() {
            telemetryDir =
                FileUtils.getAbsoluteDir(File(rootTelemetryDir, System.currentTimeMillis().toString()).absolutePath)

            telemetryImageSaver.setSessionDir(telemetryDir)
            nativeVisionManager.startTelemetrySavingSession(telemetryDir)
            videoRecorder.startRecording()
            startRecordCoreMillis = (nativeVisionManager.getCoreTimeSeconds() * 1000).toLong()

            handler.postDelayed({
                stopSession()
                startSession()
            }, RESTART_SESSION_RECORDING_DELAY_MILLIS)
        }

        private fun stopSession() {
            val videoPath = videoRecorder.stopRecording()
            nativeVisionManager.stopTelemetrySavingSession()
            val clips = nativeVisionManager.getClips()
            nativeVisionManager.resetClips()

            onSessionEnded(
                telemetryDir, startRecordCoreMillis, videoPath, clips
            )
        }
    }
}
