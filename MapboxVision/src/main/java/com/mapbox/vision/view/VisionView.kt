package com.mapbox.vision.view

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.ImageView
import com.mapbox.vision.R
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.utils.drawer.detections.DetectionDrawerImpl
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.nio.ByteBuffer

class VisionView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr), VideoSourceListener {

    private val detectionDrawer = DetectionDrawerImpl()

    internal var baseVisionManager: BaseVisionManager? = null

    @Volatile
    private var bitmap: Bitmap? = null

    var visualizationMode = VisualizationMode.Clear

    init {
        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.VisionView, 0, 0)
        val ordinal = typedArray.getInt(R.styleable.VisionView_visualization_mode, 0)

        if (ordinal in 0 until VisualizationMode.values().size) {
            visualizationMode = VisualizationMode.values()[ordinal]
        }
        typedArray.recycle()
    }

    private fun updateBitmap(rgbaBytes: ByteArray, imageSize: ImageSize) {
        val btm = bitmap
        if (btm == null || btm.width != imageSize.imageWidth || btm.height != imageSize.imageHeight) {
            bitmap = Bitmap.createBitmap(
                imageSize.imageWidth,
                imageSize.imageHeight,
                android.graphics.Bitmap.Config.ARGB_8888
            )
        }

        bitmap?.copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes))
    }

    fun setDetections(frameDetections: FrameDetections) {
        if (visualizationMode != VisualizationMode.Detections) {
            return
        }

        val rgbaBytes = baseVisionManager?.getDetectionsImage(frameDetections)
        if (rgbaBytes?.isNotEmpty() == true) {
            updateBitmap(rgbaBytes, frameDetections.frame.image.size)

            val bitmapWithDetections = if (frameDetections.detections.isEmpty()) {
                bitmap
            } else {
                bitmap?.copy(Bitmap.Config.ARGB_8888, true)?.also {
                    detectionDrawer.draw(it, frameDetections.detections)
                }
            }

            post { setImageBitmap(bitmapWithDetections) }
        }
    }

    fun setSegmentation(frameSegmentation: FrameSegmentation) {
        if (visualizationMode != VisualizationMode.Segmentation) {
            return
        }

        val rgbaBytes = baseVisionManager?.getSegmentationImage(frameSegmentation)
        if (rgbaBytes?.isNotEmpty() == true) {
            updateBitmap(rgbaBytes, frameSegmentation.frame.image.size)

            post { setImageBitmap(bitmap) }
        }
    }

    override fun onNewFrame(
        rgbaBytes: ByteArray,
        imageFormat: ImageFormat,
        imageSize: ImageSize
    ) {
        if (visualizationMode != VisualizationMode.Clear) {
            return
        }

        updateBitmap(rgbaBytes, imageSize)
        post { setImageBitmap(bitmap) }
    }

    override fun onNewCameraParameters(cameraParameters: CameraParameters) = Unit
}
