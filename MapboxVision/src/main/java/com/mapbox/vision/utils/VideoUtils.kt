package com.mapbox.vision.utils

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import java.io.IOException
import java.nio.ByteBuffer


internal object VideoUtils {

    private const val TAG = "VideoUtils"

    /**
     * @param srcPath  the path of source video file.
     * @param dstPath  the path of destination video file.
     * @param startMs  starting time in milliseconds for trimming. Set to
     * negative if starting from beginning.
     * @param endMs    end time for trimming in milliseconds. Set to negative if
     * no trimming at the end.
     * @param useAudio true if keep the audio track from the source.
     * @param useVideo true if keep the video track from the source.
     * @throws IOException
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(IOException::class)
    fun genVideoUsingMuxer(
            srcPath: String,
            dstPath: String,
            startMs: Long,
            endMs: Long,
            useAudio: Boolean = false,
            useVideo: Boolean = true
    ): VideoProcessor.VideoPart {
        val extractor = MediaExtractor()
        extractor.setDataSource(srcPath)
        val trackCount = extractor.trackCount
        val muxer = MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        // Set up the tracks and retrieve the max buffer size for selected tracks.
        val indexMap = HashMap<Int, Int>(trackCount)
        var bufferSize = -1
        for (i in 0 until trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (
                    mime.startsWith("audio/") && useAudio
                    || mime.startsWith("video/") && useVideo
            ) {
                extractor.selectTrack(i)
                val dstIndex = muxer.addTrack(format)
                indexMap[i] = dstIndex
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    val newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
                    bufferSize = if (newSize > bufferSize) newSize else bufferSize
                }
            }
        }
        if (bufferSize < 0) {
            bufferSize = DEFAULT_BUFFER_SIZE
        }
        // Set up the orientation and starting time for extractor.
        val retrieverSrc = MediaMetadataRetriever()
        retrieverSrc.setDataSource(srcPath)
        val degreesString = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
        )
        if (degreesString != null) {
            val degrees = Integer.parseInt(degreesString)
            if (degrees >= 0) {
                muxer.setOrientationHint(degrees)
            }
        }
        if (startMs > 0) {
            extractor.seekTo((startMs * 1000), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        }

        val realStart = (extractor.sampleTime / 1000)
        val realEnd: Long

        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
        val offset = 0
        var trackIndex: Int
        val dstBuf = ByteBuffer.allocate(bufferSize)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            muxer.start()
            while (true) {
                bufferInfo.offset = offset
                bufferInfo.size = extractor.readSampleData(dstBuf, offset)
                if (bufferInfo.size < 0) {
                    Log.d(TAG, "Saw input EOS.")
                    bufferInfo.size = 0
                    realEnd = (bufferInfo.presentationTimeUs / 1000)
                    break
                } else {
                    bufferInfo.presentationTimeUs = extractor.sampleTime
                    if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000) {
                        Log.d(TAG, "The current sample is over the trim end time.")
                        realEnd = (bufferInfo.presentationTimeUs / 1000)
                        break
                    } else {
                        bufferInfo.flags = extractor.sampleFlags
                        trackIndex = extractor.sampleTrackIndex
                        muxer.writeSampleData(
                                indexMap[trackIndex]!!,
                                dstBuf,
                                bufferInfo
                        )
                        extractor.advance()
                    }
                }
            }
            muxer.stop()

        } catch (e: IllegalStateException) {
            Log.w(TAG, "The source video file is malformed")
            return VideoProcessor.VideoPart(0, 0)
        } finally {
            try {
                muxer.release()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
                Log.w(TAG, "Cannot release MediaMuxer. Exception : ${e.message}")
                return VideoProcessor.VideoPart(0, 0)
            }
        }
        return VideoProcessor.VideoPart(realStart, realEnd)
    }
}
