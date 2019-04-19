package com.mapbox.vision.view

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.ImageView
import com.mapbox.vision.R
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.utils.drawer.detections.DetectionDrawerImpl
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.nio.ByteBuffer

class VisionView : ImageView, VideoSourceListener {

    private val detectionDrawer = DetectionDrawerImpl()

    private var imageSize = ImageSize(0, 0)
    private var bitmap: Bitmap? = null

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {

        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.VisionView, 0, 0)
        val ordinal = typedArray.getInt(R.styleable.VisionView_visualization_mode, 0)

        if (ordinal in 0 until VisualizationMode.values().size) {
            visualizationMode = VisualizationMode.values()[ordinal]
        }
        typedArray.recycle()
    }

    var visualizationMode = VisualizationMode.Clear

    private fun updateBitmap(rgbaBytes: ByteArray, imageSize: ImageSize) {
        if (this.imageSize.imageWidth != imageSize.imageWidth || this.imageSize.imageHeight != imageSize.imageHeight) {
            this.imageSize = imageSize
            bitmap = Bitmap.createBitmap(imageSize.imageWidth, imageSize.imageHeight, android.graphics.Bitmap.Config.ARGB_8888)
        }

        bitmap?.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes))
    }

    fun setDetections(frameDetections: FrameDetections) {
        if (visualizationMode != VisualizationMode.Detections) {
            return
        }

        val rgbaBytes = VisionManager.getDetectionsImage(frameDetections);
        if (rgbaBytes.isNotEmpty()) {
            updateBitmap(rgbaBytes, frameDetections.frame.image.size)

            val bitmapWithDetections = if (frameDetections.detections.isEmpty()) {
                bitmap
            } else {
                bitmap?.copy(Bitmap.Config.ARGB_8888, true)?.also {
                    detectionDrawer.draw(it, frameDetections.detections)
                }
            }

            handler.post {
                setImageBitmap(bitmapWithDetections)
            }
        }
    }

    fun setSegmentation(frameSegmentation: FrameSegmentation) {
        if (visualizationMode != VisualizationMode.Segmentation) {
            return
        }

        val rgbaBytes = VisionManager.getSegmentationImage(frameSegmentation);
        if (rgbaBytes.isNotEmpty()) {
            updateBitmap(rgbaBytes, frameSegmentation.frame.image.size)

            handler.post {
                setImageBitmap(bitmap)
            }
        }
    }

    fun setBytes(rgbaBytes: ByteArray) {
        if (visualizationMode != VisualizationMode.Clear) {
            return
        }

        updateBitmap(rgbaBytes, imageSize)
        handler.post {
            setImageBitmap(bitmap)
        }
    }

    override fun onNewFrame(
        rgbaBytes: ByteArray,
        imageFormat: ImageFormat,
        imageSize: ImageSize
    ) {
        setBytes(rgbaBytes)
    }

    override fun onNewCameraParameters(cameraParameters: CameraParameters) {
        imageSize = ImageSize(
            imageWidth = cameraParameters.width,
            imageHeight = cameraParameters.height
        )
    }
}
