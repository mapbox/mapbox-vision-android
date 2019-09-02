package com.mapbox.vision.video.videoprocessor

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import com.mapbox.vision.models.videoclip.VideoClipStartStop
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.threads.WorkThreadHandler
import java.io.IOException
import java.nio.ByteBuffer
import java.util.HashMap
import java.util.Locale

internal interface VideoProcessor {

    fun attach(videoProcessorListener: VideoProcessorListener.MultipleClips)
    fun detach()

    fun splitVideoClips(
        clips: Array<VideoClipStartStop>,
        videoPath: String,
        outputPath: String,
        coreSessionStartMillis: Long,
        onVideoClipReady:VideoProcessorListener.SingleClip?,
        onVideoClipsReady: VideoProcessorListener.MultipleClips?
    )

    class Impl : VideoProcessor {

        private var videoProcessorListener: VideoProcessorListener.MultipleClips? = null

        private val workThreadHandler = WorkThreadHandler()

        override fun attach(videoProcessorListener: VideoProcessorListener.MultipleClips) {
            this.videoProcessorListener = videoProcessorListener
            workThreadHandler.start()
        }

        override fun detach() {
            workThreadHandler.stop()
            videoProcessorListener = null
        }

        override fun splitVideoClips(
            clips: Array<VideoClipStartStop>,
            videoPath: String,
            outputPath: String,
            coreSessionStartMillis: Long,
            onVideoClipReady: VideoProcessorListener.SingleClip?,
            onVideoClipsReady: VideoProcessorListener.MultipleClips?
        ) {
            workThreadHandler.post {
                val clipsResult = HashMap<String, VideoClipStartStop>()
                for (part in clips) {
                    val absoluteCoreClipStartMillis = part.startSecond * 1000
                    val absoluteCoreClipVideoEndMillis = part.endSecond * 1000
                    val relativeClipStartMillis = absoluteCoreClipStartMillis - coreSessionStartMillis
                    val relativeClipEndMillis = absoluteCoreClipVideoEndMillis - coreSessionStartMillis
                    if (relativeClipStartMillis < 0 || relativeClipEndMillis < 0) {
                        continue
                    }
                    val timespan = "${part.startSecond.formatSeconds()}_${part.endSecond.formatSeconds()}"
                    val outputClipPath = "$outputPath/$timespan.mp4"
                    val videoClip = genVideoUsingMuxer(
                        srcPath = videoPath,
                        dstPath = outputClipPath,
                        startMillis = relativeClipStartMillis.toLong(),
                        endMillis = relativeClipEndMillis.toLong(),
                        absoluteSessionStartMillis = coreSessionStartMillis
                    )
                    clipsResult[outputClipPath] = videoClip

                    onVideoClipReady?.onVideoClipReady(outputClipPath, videoClip)
                }

                onVideoClipsReady?.onVideoClipsReady(clipsResult, outputPath)

                videoProcessorListener?.onVideoClipsReady(
                    videoClips = clipsResult,
                    videoDir = outputPath
                )
            }
        }

        private fun Float.formatSeconds() = "%.2f".format(Locale.ENGLISH, this)

        // TODO refactor
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Throws(IOException::class)
        private fun genVideoUsingMuxer(
            srcPath: String,
            dstPath: String,
            startMillis: Long,
            endMillis: Long,
            absoluteSessionStartMillis: Long
        ): VideoClipStartStop {
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
                if (mime.startsWith("video/")) {
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
            if (startMillis > 0) {
                extractor.seekTo((startMillis * 1000), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            val realRelativeStartSeconds = extractor.sampleTime / 1_000_000f
            val realRelativeEndSeconds: Float

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
                        VisionLogger.d(TAG, "Saw input EOS.")
                        bufferInfo.size = 0
                        realRelativeEndSeconds = bufferInfo.presentationTimeUs / 1_000_000f
                        break
                    } else {
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        if (endMillis > 0 && bufferInfo.presentationTimeUs > endMillis * 1000) {
                            VisionLogger.d(TAG, "The current sample is over the trim end time.")
                            realRelativeEndSeconds = bufferInfo.presentationTimeUs / 1_000_000f
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
                VisionLogger.d(TAG, "The source video file is malformed")
                return VideoClipStartStop(0f, 0f)
            } finally {
                try {
                    muxer.release()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    VisionLogger.d(TAG, "Cannot release MediaMuxer. Exception : ${e.message}")
                    return VideoClipStartStop(0f, 0f)
                }
            }
            return VideoClipStartStop(
                startSecond = realRelativeStartSeconds + absoluteSessionStartMillis / 1000f,
                endSecond = realRelativeEndSeconds + absoluteSessionStartMillis / 1000f
            )
        }

        companion object {
            private const val TAG = "VideoProcessorImpl"
        }
    }
}