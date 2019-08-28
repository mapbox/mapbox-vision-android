package com.mapbox.vision.ar.view.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.mapbox.vision.ar.LaneVisualParams
import com.mapbox.vision.ar.VisionArManager
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.ArCamera
import com.mapbox.vision.ar.core.models.ArLane
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.lang.ref.WeakReference

/**
 * Draws AR navigation route on top of the video stream from camera.
 */
class VisionArView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs), VideoSourceListener, VisionArEventsListener {

    private var render: GlRender

    private var visionArManager: WeakReference<VisionArManager>? = null

    private val arViewSupporter = ArViewSupporter()

    init {
        // FIXME
        val frameSize = ImageSize(1280, 720)
        render = GlRender(
            context = context,
            width = frameSize.imageWidth,
            height = frameSize.imageHeight
        )
        setEGLContextClientVersion(2)
        holder.setFormat(PixelFormat.RGBA_8888)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        setRenderer(render)

        renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    fun setArManager(visionArManager: VisionArManager?) {
        unsubscribeWith(this.visionArManager?.get())
        this.visionArManager = null

        visionArManager?.let {
            subscribeWith(it)
            this.visionArManager = WeakReference(it)
        }
    }

    private fun subscribeWith(visionArManager: VisionArManager) {
        visionArManager.addObservable(arViewSupporter)
        visionArManager.visionManager.getVideoSource().addObservable(arViewSupporter)
    }

    private fun unsubscribeWith(visionArManager: VisionArManager?) {
        if (visionArManager == null) {
            return
        }
        visionArManager.removeObserver(arViewSupporter)
        visionArManager.visionManager.getVideoSource().removeObserver(arViewSupporter)
    }

    @Deprecated("Will be removed in 0.9.0")
    override fun onArCameraUpdated(arCamera: ArCamera) {
        render.arCamera = arCamera
    }

    @Deprecated("Will be removed in 0.9.0")
    override fun onArLaneUpdated(arLane: ArLane) {
        render.arLane = arLane
    }

    @Deprecated("Will be removed in 0.9.0")
    override fun onNewFrame(
        rgbaBytes: ByteArray,
        imageFormat: ImageFormat,
        imageSize: ImageSize
    ) {
        render.onNewBackground(rgbaBytes)
    }

    @Deprecated("Will be removed in 0.9.0")
    override fun onNewCameraParameters(cameraParameters: CameraParameters) {
        // TODO change render
    }

    fun setLaneVisualParams(laneVisualParams: LaneVisualParams) {
        render.onNewLaneVisualParams(laneVisualParams)
    }

    private inner class ArViewSupporter : VideoSourceListener, VisionArEventsListener {
        override fun onNewFrame(
            rgbaBytes: ByteArray,
            imageFormat: ImageFormat,
            imageSize: ImageSize
        ) {
            render.onNewBackground(rgbaBytes)
        }

        override fun onNewCameraParameters(cameraParameters: CameraParameters) = Unit

        override fun onArCameraUpdated(arCamera: ArCamera) {
            render.arCamera = arCamera
        }

        override fun onArLaneUpdated(arLane: ArLane) {
            render.arLane = arLane
        }
    }
}
