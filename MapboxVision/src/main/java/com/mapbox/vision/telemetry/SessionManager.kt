package com.mapbox.vision.telemetry

import android.app.Application
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videoprocessor.VideoProcessorListener
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import java.io.File

internal interface SessionManager {

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
        application: Application,
        private val nativeVisionManager: NativeVisionManager,
        private val rootTelemetryDir: String,
        private val videoRecorder: VideoRecorder,
        private val telemetryImageSaverImpl: TelemetryImageSaverImpl,
        private val videoProcessor: VideoProcessor,
        private val videoProcessorListener: VideoProcessorListener
    ) : SessionManager {

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
                telemetryImageSaverImpl.start(telemetryDir)
                videoProcessor.attach(videoProcessorListener)
            }
        }

        override fun stop() {
            if (handler.isStarted()) {
                handler.stop()
                stopSession()
                telemetryImageSaverImpl.stop()
                videoProcessor.detach()
            }
        }

        private fun startSession() {
            videoRecorder.startRecording(buffers.getBuffer())
            telemetryDir =
                "${FileUtils.getAbsoluteDir(File(rootTelemetryDir, System.currentTimeMillis().toString()).absolutePath)}/"
            nativeVisionManager.startTelemetrySavingSession(telemetryDir)

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

            videoProcessor.splitVideoClips(
                clips = clips,
                videoPath = buffers.getBuffer(),
                outputPath = telemetryDir,
                sessionStartMillis = startRecordCoreMillis
            )
            buffers.rotate()
        }
    }

    class RecordingImpl(
        private val nativeVisionManager: NativeVisionManager,
        private val sessionDir: String,
        private val videoRecorder: VideoRecorder
    ) : SessionManager {

        private val rotatedBuffers = RotatedBuffers(
            buffersDir = sessionDir,
            totalBuffersNumber = 1
        )

        override fun start() {
            File(sessionDir).mkdirs()
            nativeVisionManager.startTelemetrySavingSession(sessionDir)
            rotatedBuffers.rotate()
            videoRecorder.startRecording(rotatedBuffers.getBuffer())
        }

        override fun stop() {
            nativeVisionManager.stopTelemetrySavingSession()
            videoRecorder.stopRecording()
        }
    }
}
