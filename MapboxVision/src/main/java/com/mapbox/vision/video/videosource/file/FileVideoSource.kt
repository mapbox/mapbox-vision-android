package com.mapbox.vision.video.videosource.file

import android.app.Application
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptGroup
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videosource.CompositeListenerVideoSource
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.io.File
import java.nio.ByteBuffer

/**
 * Plays list of [videoFiles] sequentially.
 */
@Suppress("DEPRECATION")
class FileVideoSource(
    private val application: Application,
    private val videoFiles: Collection<File>,
    private val onVideoStarted: (String) -> Unit,
    private val onVideosEnded: () -> Unit
) : VideoSource.WithProgress {

    private val renderscript: RenderScript by lazy {
        RenderScript.create(application)
    }

    private val compositeListenerVideoSource = CompositeListenerVideoSource()

    // Image from MediaCodec arrive in NV12 format, while ScriptIntrinsicYuvToRGB processes NV21,
    // which results in BGR format in the output.
    private val intrinsicYuvToRgb =
        ScriptIntrinsicYuvToRGB.create(renderscript, Element.U8_4(renderscript))
    // So we use additional renderscript, that swaps BGR to RGB.
    private val bgrToRgb = ScriptC_bgr_to_rgb(renderscript)
    // Scripts are used as ScriptGroup to use single memory for conversion.
    private lateinit var scriptGroup: ScriptGroup

    private var isInitialized: Boolean = false

    private var width = 0
    private var height = 0
    private var videoProgress = -1L

    private lateinit var sourceAllocation: Allocation
    private lateinit var destinationAllocation: Allocation

    private lateinit var videoDecoder: FileVideoDecoder
    private lateinit var yuvArray: ByteArray
    private lateinit var rgbaArray: ByteArray

    private val handler = WorkThreadHandler()
    private val responseHandler = WorkThreadHandler()

    private var videoSourceListener: VideoSourceListener? = null

    private var currentVideo = -1

    override fun setProgress(timestampMillis: Long) {
        handler.post {
            videoDecoder.setProgress(timestampMillis = timestampMillis)
        }
    }

    override fun pause() {
        handler.post {
            videoDecoder.pause()
        }
    }

    override fun resume() {
        handler.post {
            videoDecoder.resume()
        }
    }

    override fun getProgress(): Long = videoProgress

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener
        addListener(videoSourceListener)

        responseHandler.start()
        handler.start()
        handler.post {
            videoDecoder = try {
                FileVideoDecoder(
                    this::onFrameDecoded,
                    this::playNextVideo,
                    this::onFrameFormatChanged
                )
            } catch (e: Exception) {
                VisionLogger.e(e, "FileVideoSource")
                throw e
            }
            playNextVideo()
        }
    }

    override fun detach() {
        videoSourceListener?.let {
            compositeListenerVideoSource.removeListener(it)
            videoSourceListener = null
        }

        currentVideo = 0
        videoDecoder.stopPlayback()
        handler.stop()
        responseHandler.stop()
    }

    override fun addListener(observer: VideoSourceListener) =
        compositeListenerVideoSource.addListener(observer)

    override fun removeListener(observer: VideoSourceListener) =
        compositeListenerVideoSource.removeListener(observer)

    private fun onFrameDecoded(byteBuffer: ByteBuffer, videoProgress: Long) {
        try {
            if (!isInitialized) {
                return
            }
            this.videoProgress = videoProgress

            byteBuffer.get(yuvArray)
            responseHandler.post {
                try {
                    sourceAllocation.copyFrom(yuvArray)
                    scriptGroup.execute()
                    destinationAllocation.copyTo(rgbaArray)
                    compositeListenerVideoSource.onNewFrame(
                        rgbaArray,
                        ImageFormat.RGBA,
                        ImageSize(width, height)
                    )
                } catch (e: Exception) {
                    VisionLogger.e(e, "FileVideoSource")
                }
            }
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

    private fun onFrameFormatChanged(width: Int, height: Int) {
        if (this.width != width || this.height != height || !isInitialized) {
            isInitialized = true

            this.width = width
            this.height = height
            val pixelNum = width * height
            yuvArray = ByteArray(pixelNum * 3 / 2)
            rgbaArray = ByteArray(pixelNum * 4)

            sourceAllocation = Allocation.createSized(
                renderscript,
                Element.U8(renderscript),
                yuvArray.size
            )

            destinationAllocation = Allocation.createTyped(
                renderscript,
                Type.Builder(renderscript, Element.RGBA_8888(renderscript))
                    .setX(width)
                    .setY(height)
                    .create(),
                Allocation.USAGE_SCRIPT
            )

            ScriptGroup.Builder(renderscript).run {
                addKernel(intrinsicYuvToRgb.kernelID)
                addKernel(bgrToRgb.kernelID_bgrToRgb)

                addConnection(
                    destinationAllocation.type,
                    intrinsicYuvToRgb.kernelID,
                    bgrToRgb.kernelID_bgrToRgb
                )

                scriptGroup = create()

                scriptGroup.setOutput(bgrToRgb.kernelID_bgrToRgb, destinationAllocation)

                intrinsicYuvToRgb.setInput(sourceAllocation)
            }
        }
    }
}
