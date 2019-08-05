package com.mapbox.vision.video.videoprocessor

import android.annotation.TargetApi
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.os.Build
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.threads.WorkThreadHandler
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.nio.ByteBuffer
import java.util.HashMap
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

internal interface VideoProcessor {

    fun attach(videoProcessorListener: VideoProcessorListener)
    fun detach()

    fun splitVideoClips(
        clips: Array<VideoClip>,
        videoPath: String,
        outputPath: String,
        sessionStartMillis: Long
    )

    class Impl : VideoProcessor {

        private var videoProcessorListener: VideoProcessorListener? = null

        private val workThreadHandler = WorkThreadHandler()

        override fun attach(videoProcessorListener: VideoProcessorListener) {
            this.videoProcessorListener = videoProcessorListener
            workThreadHandler.start()
        }

        override fun detach() {
            workThreadHandler.stop()
            videoProcessorListener = null
        }

        override fun splitVideoClips(
            clips: Array<VideoClip>,
            videoPath: String,
            outputPath: String,
            sessionStartMillis: Long
        ) {
            workThreadHandler.post {
                val clipsResult = HashMap<String, VideoClip>()
                for (part in clips) {
                    val relativeStartMillis = part.startSeconds * 1000 - sessionStartMillis
                    val relativeEndMillis = part.endSeconds * 1000 - sessionStartMillis
                    if (relativeStartMillis < 0 || relativeEndMillis < 0) {
                        VisionLogger.d(
                            TAG,
                            "Video clip bounds does not belong to current video, ignoring : $relativeStartMillis - $relativeEndMillis"
                        )
                        continue
                    }
                    val timespan = "${"%.2f".format(Locale.ENGLISH, relativeStartMillis / 1000f)}_${"%.2f".format(
                        Locale.ENGLISH,
                        relativeEndMillis / 1000f
                    )}"
                    val outputClipPath = "$outputPath/$timespan.mp4"
                    val videoPart = genVideoUsingMuxer(
                        srcPath = videoPath,
                        dstPath = outputClipPath,
                        startMillis = relativeStartMillis.toLong(),
                        endMillis = relativeEndMillis.toLong()
                    )
                    clipsResult[outputClipPath] = videoPart
                }
                videoProcessorListener?.onVideoClipsReady(
                    videoClips = clipsResult,
                    videoDir = outputPath,
                    jsonFile = createJsonFileByParts(clipsResult, outputPath, sessionStartMillis)
                )
            }
        }

        private fun createJsonFileByParts(
            videoClipMap: HashMap<String, VideoClip>,
            saveDirPath: String,
            startRecordCoreMillis: Long
        ): String {
            val arr = JSONArray()
            for (videoPart in videoClipMap) {
                val jsonPath = JSONObject()
                val paths = videoPart.key.split("/")
                val name = paths[paths.size - 1]

                val startSeconds = startRecordCoreMillis / 1000f + videoPart.value.startSeconds
                val endSeconds = startRecordCoreMillis / 1000f + videoPart.value.endSeconds
                jsonPath.put("name", name)
                jsonPath.put("start", startSeconds)
                jsonPath.put("end", endSeconds)
                arr.put(jsonPath)
            }

            return try {
                val file = File(saveDirPath, "videos.json")
                val output = BufferedWriter(FileWriter(file))
                output.write(arr.toString())
                output.close()

                file.absolutePath
            } catch (e: Exception) {
                VisionLogger.d(TAG, "Can not create Json file : " + e.localizedMessage)
                ""
            }
        }

        // TODO refactor
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Throws(IOException::class)
        private fun genVideoUsingMuxer(
            srcPath: String,
            dstPath: String,
            startMillis: Long,
            endMillis: Long
        ): VideoClip {
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

            val realStartSeconds = extractor.sampleTime / 1_000_000f
            val realEndSeconds: Float

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
                        realEndSeconds = bufferInfo.presentationTimeUs / 1_000_000f
                        break
                    } else {
                        bufferInfo.presentationTimeUs = extractor.sampleTime
                        if (endMillis > 0 && bufferInfo.presentationTimeUs > endMillis * 1000) {
                            VisionLogger.d(TAG, "The current sample is over the trim end time.")
                            realEndSeconds = bufferInfo.presentationTimeUs / 1_000_000f
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
                return VideoClip(0f, 0f)
            } finally {
                try {
                    muxer.release()
                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    VisionLogger.d(TAG, "Cannot release MediaMuxer. Exception : ${e.message}")
                    return VideoClip(0f, 0f)
                }
            }
            return VideoClip(
                startSeconds = realStartSeconds,
                endSeconds = realEndSeconds
            )
        }

        companion object {
            private const val TAG = "VideoProcessorImpl"
        }
    }
}
