package com.mapbox.vision.video.videosource.file

import android.media.Image
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.video.videosource.Progress

class FileVideoDecoder(
    private val onFrameDecoded: (Image) -> Unit,
    private val onFramesEnded: () -> Unit
) : Progress {

    companion object {
        private const val LOG_FPS_EVERY_MILLIS = 10000
    }

    private var counterStartTimestamp: Long = 0
    private var counterFrames: Int = 0

    private var decoder: MediaCodec? = null

    private var mediaExtractor: MediaExtractor? = null
    private var videoProgressTimestamp = 0L
    private var playStartTimestamp = 0L

    private fun startDecoder(mimeType: String, trackFormat: MediaFormat) {
        decoder = MediaCodec.createDecoderByType(mimeType).apply {
            configure(
                trackFormat,
                null, // no surface, will grab images manually with `decoder.getOutputImage`
                null,
                0 // 0:decoder 1:encoder
            )
            setCallback(
                object : MediaCodec.Callback() {
                    override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                        onOutputBuffer(codec, index, info)
                    }

                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        onInputBuffer(codec, index)
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        VisionLogger.e(e, "FileVideoDecoder", "Error in MediaCodec callback")
                    }
                }
            )
            start()
        }
    }

    /**
     * Plays video, calls [onFrameDecoded] per each frame. Calls [onFramesEnded] when video ends.
     */
    fun playVideo(absolutePath: String) {
        stopPlayback()
        mediaExtractor = MediaExtractor().apply {
            setDataSource(absolutePath)
            for (i in 0 until trackCount) {
                val trackFormat = getTrackFormat(i)
                val mimeType = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mimeType.startsWith("video/")) {
                    selectTrack(i)
                    startDecoder(mimeType, trackFormat)
                    break
                }
            }
        }

        playStartTimestamp = System.currentTimeMillis()
        counterStartTimestamp = playStartTimestamp
        counterFrames = 0
        videoProgressTimestamp = 0L
    }

    fun stopPlayback() {
        decoder?.stop()
        decoder?.release()
        decoder = null
        mediaExtractor?.release()
        mediaExtractor = null
    }

    /**
     * VideoDecoder should be started to be able to call this method.
     */
    override fun setProgress(timestampMillis: Long) {
        if (mediaExtractor == null) {
            throw IllegalStateException("Can't set progress, video decoding isn't started.")
        }
        playStartTimestamp = System.currentTimeMillis() - timestampMillis
        counterStartTimestamp = System.currentTimeMillis() - timestampMillis
        mediaExtractor!!.seekTo(timestampMillis * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        videoProgressTimestamp = timestampMillis
    }

    override fun getProgress(): Long = videoProgressTimestamp

    private fun onInputBuffer(codec: MediaCodec, index: Int) {
        try {
            val inputBuffer = codec.getInputBuffer(index)
            val sampleSize = mediaExtractor!!.readSampleData(inputBuffer!!, 0)
            if (sampleSize > 0) {
                codec.queueInputBuffer(index, 0, sampleSize, mediaExtractor!!.sampleTime, 0)
                mediaExtractor!!.advance()
            } else {
                codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                onFramesEnded()
            }
        } catch (e: Exception) {
            VisionLogger.e(e, "FileVideoDecoder", "Error in input buffer reading")
        }
    }

    private fun onOutputBuffer(codec: MediaCodec, index: Int, bufferInfo: MediaCodec.BufferInfo) {
        try {
            if (index >= 0) {
                val image = codec.getOutputImage(index)!!
                onFrameDecoded(image)
                codec.releaseOutputBuffer(
                    index,
                    false // true:render to surface
                )

                videoProgressTimestamp = bufferInfo.presentationTimeUs / 1000
                sleepUntilPresentationTime()
                countFps()
            }
        } catch (e: Exception) {
            VisionLogger.e(e, "FileVideoDecoder", "Error in output buffer processing")
        }
    }

    private fun sleepUntilPresentationTime() {
        val millisFromStart = System.currentTimeMillis() - playStartTimestamp
        if (millisFromStart < videoProgressTimestamp) {
            try {
                Thread.sleep(videoProgressTimestamp - millisFromStart)
            } catch (e: InterruptedException) {
                VisionLogger.e(e, "FileVideoDecoder")
            }
        }
    }

    private fun countFps() {
        counterFrames++
        val millisPassed = System.currentTimeMillis() - counterStartTimestamp
        if (millisPassed > LOG_FPS_EVERY_MILLIS) {
            counterStartTimestamp = System.currentTimeMillis()
            counterFrames = 0
        }
    }
}
