package com.mapbox.vision.video.videosource.camera

import android.app.Application
import android.content.Context
import android.media.MediaCodec
import android.media.MediaRecorder
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import com.mapbox.vision.utils.FileUtils
import java.io.File
import java.io.IOException

internal interface VideoRecorder {

    fun startRecording()
    fun stopRecording(): String

    fun release()
}

internal interface SurfaceVideoRecorder : VideoRecorder {

    val surface: Surface

    class MediaCodecPersistentSurfaceImpl(
            private val application: Application,
            private val buffersDir: String,
            private val sensorOrientation: Int,
            private val frameWidth: Int,
            private val frameHeight: Int
    ) : SurfaceVideoRecorder {

        private var mediaRecorder: MediaRecorder? = null

        private var nextVideoFilePath: String? = null
        private var currentBufferNum = 0

        override var surface: Surface = MediaCodec.createPersistentInputSurface()

        override fun startRecording() {
            if (mediaRecorder == null) {
                mediaRecorder = MediaRecorder()
            }
            updateNextBufferFile()
            mediaRecorder?.setup(nextVideoFilePath!!)
            mediaRecorder?.start()
        }

        override fun stopRecording(): String {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mediaRecorder?.reset()
            }

            val filePath = nextVideoFilePath
            if (filePath != null && !filePath.isBlank()) {
                return filePath
            }

            nextVideoFilePath = null
            return ""
        }

        override fun release() {
            mediaRecorder?.release()
            mediaRecorder = null
            surface.release()
        }

        @Throws(IOException::class)
        private fun MediaRecorder.setup(outputFile: String) {
            val rotation = (application.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
            when (sensorOrientation) {
                SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                    setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
                SENSOR_ORIENTATION_INVERSE_DEGREES ->
                    setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
            }

            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(outputFile)
            setVideoEncodingBitRate(6000000)
            setVideoFrameRate(30)
            setVideoSize(frameWidth, frameHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setInputSurface(this@MediaCodecPersistentSurfaceImpl.surface)
            prepare()
        }

        private fun updateNextBufferFile() {
            if (nextVideoFilePath.isNullOrEmpty()) {
                nextVideoFilePath = FileUtils.getAbsoluteFile(buffersDir, BUFFER_FILE_NAMES[currentBufferNum])
                val bufferFile = File(nextVideoFilePath)
                if (bufferFile.exists()) {
                    bufferFile.delete()
                }
                currentBufferNum++
                if (currentBufferNum >= VIDEO_BUFFERS_NUMBER) {
                    currentBufferNum = 0
                }
            }
        }

        companion object {
            private const val VIDEO_BUFFERS_NUMBER = 3

            private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
            private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270

            private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
                append(Surface.ROTATION_0, 90)
                append(Surface.ROTATION_90, 0)
                append(Surface.ROTATION_180, 270)
                append(Surface.ROTATION_270, 180)
            }

            private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
                append(Surface.ROTATION_0, 270)
                append(Surface.ROTATION_90, 180)
                append(Surface.ROTATION_180, 90)
                append(Surface.ROTATION_270, 0)
            }

            val BUFFER_FILE_NAMES = listOf(
                    "video1.mp4",
                    "video2.mp4",
                    "video3.mp4"
            )
        }
    }
}
