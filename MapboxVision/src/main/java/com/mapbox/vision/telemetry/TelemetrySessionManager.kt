package com.mapbox.vision.telemetry

import android.app.Application
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import java.io.File

internal interface TelemetrySessionManager {

    fun start()
    fun stop()

    private class RotatedBuffers(
        private val buffersDir: String,
        private val totalBuffersNumber: Int = DEFAULT_BUFFERS_NUMBER
    ) {
        companion object {
            private const val DEFAULT_BUFFERS_NUMBER = 3
        }

        private var bufferIndex = 0

        fun rotate() {
            bufferIndex++
            if (bufferIndex >= totalBuffersNumber) {
                bufferIndex = 0
            }

            val bufferFile = File(getBuffer())
            if (bufferFile.exists()) {
                bufferFile.delete()
            }
        }

        fun getBuffer() = FileUtils.getAbsoluteFile(buffersDir, generateBufferName(bufferIndex))

        private fun generateBufferName(index: Int) = "video$index.mp4"
    }

    class RotatedBuffersImpl(
        private val application: Application,
        private val nativeVisionManager: NativeVisionManager,
        private val rootTelemetryDir: String,
        private val videoRecorder: VideoRecorder,
        private val telemetryImageSaver: TelemetryImageSaver,
        private val onSessionEnded: (String, Long, String, Array<VideoClip>) -> Unit
    ) : TelemetrySessionManager {

        companion object {
            private const val VIDEO_BUFFERS_DIR = "Buffers"
            private const val SESSION_LENGTH_MILLIS = 5 * 60 * 1000L
        }

        private val handler = WorkThreadHandler("Session")
        private val buffers = RotatedBuffers(FileUtils.getAppRelativeDir(application, VIDEO_BUFFERS_DIR))
        private var telemetryDir: String = ""
        private var startRecordCoreMillis = 0L

        override fun start() {
            if (!handler.isStarted()) {
                handler.start()
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
            telemetryDir = "${FileUtils.getAbsoluteDir(
                File(rootTelemetryDir, System.currentTimeMillis().toString()).absolutePath
            )}/"
            telemetryImageSaver.setSessionDir(telemetryDir)
            nativeVisionManager.startTelemetrySavingSession(telemetryDir)

            videoRecorder.startRecording(buffers.getBuffer())
            startRecordCoreMillis = (nativeVisionManager.getCoreTimeSeconds() * 1000).toLong()

            handler.postDelayed({
                stopSession()
                startSession()
            }, SESSION_LENGTH_MILLIS)
        }

        private fun stopSession() {
            videoRecorder.stopRecording()
            nativeVisionManager.stopTelemetrySavingSession()
            val clips = nativeVisionManager.getClips()
            nativeVisionManager.resetClips()

            onSessionEnded(
                telemetryDir, startRecordCoreMillis, buffers.getBuffer(), clips
            )
            buffers.rotate()
        }
    }
}
