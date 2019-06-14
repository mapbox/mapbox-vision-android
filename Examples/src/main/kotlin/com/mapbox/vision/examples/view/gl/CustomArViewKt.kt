package com.mapbox.vision.examples.view.gl

import android.content.Context
import android.graphics.PixelFormat
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import com.mapbox.vision.ar.core.VisionArEventsListener
import com.mapbox.vision.ar.core.models.ArCamera
import com.mapbox.vision.ar.core.models.ArLane
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.video.videosource.VideoSourceListener

class CustomArViewKt
@JvmOverloads
constructor(
    context: Context,
    attr: AttributeSet? = null
) : GLSurfaceView(context, attr), VideoSourceListener, VisionArEventsListener {

    private val render: CustomArGlRender

    init {
        val frameSize = ImageSize(1280, 720)
        render = CustomArGlRender(
            width = frameSize.imageWidth,
            height = frameSize.imageHeight
        )
        setEGLContextClientVersion(2)
        holder.setFormat(PixelFormat.RGBA_8888)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)

        setRenderer(render)

        renderMode = RENDERMODE_CONTINUOUSLY
    }

    // VideoSourceListener
    override fun onNewFrame(rgbaBytes: ByteArray, imageFormat: ImageFormat, imageSize: ImageSize) {
        render.onNewBackground(rgbaBytes)
    }

    override fun onNewCameraParameters(cameraParameters: CameraParameters) {
    }
    // \ VideoSourceListener

    // VisionArEventsListener
    override fun onArCameraUpdated(arCamera: ArCamera) {
        render.arCamera = arCamera
    }

    override fun onArLaneUpdated(arLane: ArLane) {
        render.arLane = arLane
    }
    // \ VisionArEventsListener
}
