package com.mapbox.vision.view

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import com.mapbox.vision.R
import com.mapbox.vision.manager.BaseVisionManager
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize

class VisionGLView
@JvmOverloads constructor(
        context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), VisionViewListener {

    private val mRenderer : VisionGLRenderer
    internal var baseVisionManager: BaseVisionManager? = null
    private var visualizationMode = VisualizationMode.Clear

    init {

        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.VisionView, 0, 0)
        val ordinal = typedArray.getInt(R.styleable.VisionView_visualization_mode, 0)

        if (ordinal in VisualizationMode.values().indices) {
            visualizationMode = VisualizationMode.values()[ordinal]
        }
        typedArray.recycle()

        // TODO workaround fixed sizes
        mRenderer = VisionGLRenderer(1280, 720)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setEGLContextClientVersion(2)
        setRenderer(mRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun setSegmentation(frameSegmentation: FrameSegmentation) {
        if (visualizationMode != VisualizationMode.Segmentation) {
            return
        }

        val rgbaBytes = baseVisionManager?.getSegmentationImage(frameSegmentation)
        if (rgbaBytes?.isNotEmpty() == true) {
            queueEvent { mRenderer.onNewBackgroundFrame(rgbaBytes) }
        }
    }

    override fun setDetections(frameDetections: FrameDetections) {
        if (visualizationMode != VisualizationMode.Detections) {
            return
        }

        val rgbaBytes = baseVisionManager?.getDetectionsImage(frameDetections)
        if (rgbaBytes?.isNotEmpty() == true) {
            queueEvent {
                mRenderer.run {
                    onNewBackgroundFrame(rgbaBytes)
                    if (frameDetections.detections.isNotEmpty()) {
                        onNewFrameDetections(frameDetections.detections)
                    }
                }
            }
        }
    }

    override fun onNewFrame(rgbaBytes: ByteArray, imageFormat: ImageFormat, imageSize: ImageSize) {
        if (visualizationMode != VisualizationMode.Clear) {
            return
        }
        queueEvent { mRenderer.onNewBackgroundFrame(rgbaBytes) }
    }

    override fun onNewCameraParameters(cameraParameters: CameraParameters) = Unit

}