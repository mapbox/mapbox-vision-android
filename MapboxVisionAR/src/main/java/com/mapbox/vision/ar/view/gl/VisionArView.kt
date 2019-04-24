package com.mapbox.vision.ar.view.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.mapbox.vision.ar.LaneVisualParams
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.ArCamera
import com.mapbox.vision.ar.core.models.ArLane
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.video.videosource.VideoSourceListener

/**
 * Draws AR navigation route on top of the video stream from camera.
 */
class VisionArView : GLSurfaceView, VideoSourceListener, VisionArEventsListener {

    private var render: GlRender

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
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

    override fun onArCameraUpdated(arCamera: ArCamera) {
        render.arCamera = arCamera
    }

    override fun onArLaneUpdated(arLane: ArLane) {
        render.arLane = arLane
    }

    override fun onNewFrame(
        rgbaBytes: ByteArray,
        imageFormat: ImageFormat,
        imageSize: ImageSize
    ) {
        render.onNewBackground(rgbaBytes)
    }

    override fun onNewCameraParameters(cameraParameters: CameraParameters) {
        // TODO change render
    }

    fun setLaneVisualParams(laneVisualParams: LaneVisualParams) {
        render.onNewLaneVisualParams(laneVisualParams)
    }
}
