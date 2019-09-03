package com.mapbox.vision.video.videosource.camera

import android.app.Application
import android.content.Context
import android.media.MediaCodec
import android.media.MediaRecorder
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import java.io.IOException

internal interface VideoRecorder {

    fun init(frameWidth: Int, frameHeight: Int, sensorOrientation: Int)
    fun startRecording(path: String)
    fun stopRecording()
    fun release()

    class EmptyImpl : VideoRecorder {

        override fun init(frameWidth: Int, frameHeight: Int, sensorOrientation: Int) {
            // do nothing
        }

        override fun startRecording(path: String) {
            // do nothing
        }

        override fun stopRecording() {
            // do nothing
        }

        override fun release() {
            // do nothing
        }
    }
}

internal interface SurfaceVideoRecorder : VideoRecorder {

    val surface: Surface

    class MediaCodecPersistentSurfaceImpl(
        private val application: Application
    ) : SurfaceVideoRecorder {

        private var mediaRecorder: MediaRecorder? = null

        private var frameWidth = 0
        private var frameHeight = 0
        private var sensorOrientation = 0

        override var surface: Surface = MediaCodec.createPersistentInputSurface()

        override fun init(frameWidth: Int, frameHeight: Int, sensorOrientation: Int) {
            this.frameWidth = frameWidth
            this.frameHeight = frameHeight
            this.sensorOrientation = sensorOrientation
        }

        override fun startRecording(path: String) {
            if (mediaRecorder == null) {
                mediaRecorder = MediaRecorder()
            }
            if (frameHeight == 0 || frameWidth == 0) {
                throw IllegalStateException("Uninitialized image size, can not record!")
            }
            mediaRecorder?.setup(path, frameWidth, frameHeight)
            mediaRecorder?.start()
        }

        override fun stopRecording() {
            try {
                mediaRecorder?.stop()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mediaRecorder?.reset()
            }
        }

        override fun release() {
            mediaRecorder?.release()
            mediaRecorder = null
            surface.release()
        }

        @Throws(IOException::class)
        private fun MediaRecorder.setup(outputFile: String, frameWidth: Int, frameHeight: Int) {
            val rotation =
                (application.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
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

        companion object {
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
        }
    }
}
