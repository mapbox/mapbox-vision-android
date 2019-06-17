package com.mapbox.vision.video.videosource.file

import android.app.Application
import android.media.Image
import android.renderscript.*
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.io.File

/**
 * Plays list of [videoFiles] sequentially.
 */
class FileVideoSource(
    private val application: Application,
    private val videoFiles: Collection<File>,
    private val onVideoStarted: (String) -> Unit,
    private val onVideosEnded: () -> Unit
) : VideoSource.WithProgress {

    private val renderscript: RenderScript by lazy {
        RenderScript.create(application)
    }

    private val yuvToRgba = ScriptIntrinsicYuvToRGB.create(renderscript, Element.U8_4(renderscript))

    private var width = 0
    private var height = 0
    private var yuvSize = 0
    private var videoProgress = -1L

    private lateinit var sourceAllocation: Allocation
    private lateinit var destinationAllocation: Allocation

    private lateinit var videoDecoder: FileVideoDecoder
    private lateinit var dstBuffer: ByteArray
    private lateinit var rgbaBytes: ByteArray

    private val handler = WorkThreadHandler()
    private val responseHandler = WorkThreadHandler()

    private var videoSourceListener: VideoSourceListener? = null

    private var currentVideo = -1

    override fun setProgress(timestampMillis: Long) {
        handler.post {
            videoDecoder.setProgress(timestampMillis = timestampMillis)
        }
    }

    override fun getProgress(): Long = videoProgress

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener

        responseHandler.start()
        handler.start()
        handler.post {
            videoDecoder = try {
                FileVideoDecoder(
                    this::onFrameDecoded,
                    this::playNextVideo
                )
            } catch (e: Exception) {
                VisionLogger.e(e, "FileVideoSource")
                throw e
            }
            playNextVideo()
        }
    }

    override fun detach() {
        videoSourceListener = null

        currentVideo = 0
        videoDecoder.stopPlayback()
        handler.stop()
        responseHandler.stop()
    }

    private fun onFrameDecoded(image: Image) {
        try {
            videoProgress = videoDecoder.getProgress()
            readYuv420ImageToNv21DstBuffer(image)
            responseHandler.post {
                sourceAllocation.copyFrom(dstBuffer)
                yuvToRgba.setInput(sourceAllocation)
                yuvToRgba.forEach(destinationAllocation)
                destinationAllocation.copyTo(rgbaBytes)
                videoSourceListener?.onNewFrame(rgbaBytes, ImageFormat.RGBA, ImageSize(width, height))
            }

            image.close()
        } catch (e: Exception) {
            VisionLogger.e(e, "FileVideoSource")
        }
    }

    private fun playNextVideo() {
        videoProgress = -1
        if (++currentVideo < videoFiles.size) {
            val videoFile = videoFiles.elementAt(currentVideo)
            videoDecoder.playVideo(videoFile.absolutePath)
            onVideoStarted(videoFile.nameWithoutExtension)
        } else {
            onVideosEnded()
        }
    }

    // TODO use Renderscript
    private fun readYuv420ImageToNv21DstBuffer(srcImage: Image) {
        val crop = srcImage.cropRect
        val width = crop.width()
        val height = crop.height()
        setSize(width, height)
        val planes = srcImage.planes
        val rowData = ByteArray(planes[0].rowStride)

        var channelOffset = 0
        var outputStride = 1
        for (i in planes.indices) {
            when (i) {
                0 -> {
                    channelOffset = 0
                    outputStride = 1
                }
                1 -> {
                    channelOffset = width * height + 1
                    outputStride = 2
                }
                2 -> {
                    channelOffset = width * height
                    outputStride = 2
                }
            }

            val buffer = planes[i].buffer
            val rowStride = planes[i].rowStride
            val pixelStride = planes[i].pixelStride

            val shift = if (i == 0) 0 else 1
            val w = width shr shift
            val h = height shr shift
            buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))
            for (row in 0 until h) {
                val length: Int
                if (pixelStride == 1 && outputStride == 1) {
                    length = w
                    buffer.get(dstBuffer, channelOffset, length)
                    channelOffset += length
                } else {
                    length = (w - 1) * pixelStride + 1
                    buffer.get(rowData, 0, length)
                    for (col in 0 until w) {
                        dstBuffer[channelOffset] = rowData[col * pixelStride]
                        channelOffset += outputStride
                    }
                }
                if (row < h - 1) {
                    buffer.position(buffer.position() + rowStride - length)
                }
            }
        }
    }

    private fun setSize(width: Int, height: Int) {
        if (this.width != width || this.height != height) {
            this.width = width
            this.height = height
            yuvSize = (width * height * 1.5).toInt()
            dstBuffer = ByteArray(yuvSize)
            rgbaBytes = ByteArray(width * height * 4)
            sourceAllocation = Allocation.createTyped(
                renderscript,
                Type.Builder(renderscript, Element.U8(renderscript))
                    .setX(yuvSize)
                    .create(),
                Allocation.USAGE_SCRIPT
            )
            destinationAllocation = Allocation.createTyped(
                renderscript,
                Type.Builder(renderscript, Element.RGBA_8888(renderscript))
                    .setX(width)
                    .setY(height)
                    .create(),
                Allocation.USAGE_SCRIPT
            )
        }
    }
}
