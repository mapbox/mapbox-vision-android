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
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
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

    private val arViewSupporter = ArViewListener()

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
        unsubscribe(this.visionArManager?.get())
        this.visionArManager = null

        visionArManager?.let {
            subscribe(it)
            this.visionArManager = WeakReference(it)
        }
    }

    private fun subscribe(visionArManager: VisionArManager) {
        visionArManager.addListener(arViewSupporter)

        val addListener = visionArManager.visionManager::class.java.getDeclaredMethod("addListener", VisionEventsListener::class.java)
        addListener.isAccessible = true
        addListener.invoke(visionArManager.visionManager, arViewSupporter)

        val addVideoSourceListener = visionArManager.visionManager::class.java.getDeclaredMethod("addVideoSourceListener", VideoSourceListener::class.java)
        addVideoSourceListener.isAccessible = true
        addVideoSourceListener.invoke(visionArManager.visionManager, arViewSupporter)
    }

    private fun unsubscribe(visionArManager: VisionArManager?) {
        if (visionArManager == null) {
            return
        }
        visionArManager.removeListener(arViewSupporter)

        val removeListener = visionArManager.visionManager::class.java.getDeclaredMethod("removeListener", VisionEventsListener::class.java)
        removeListener.isAccessible = true
        removeListener.invoke(visionArManager.visionManager, arViewSupporter)

        val removeVideoSourceListener = visionArManager.visionManager::class.java.getDeclaredMethod("removeVideoSourceListener", VideoSourceListener::class.java)
        removeVideoSourceListener.isAccessible = true
        removeVideoSourceListener.invoke(visionArManager.visionManager, arViewSupporter)
    }

    @Deprecated("Will be removed in 0.10.0")
    override fun onArCameraUpdated(arCamera: ArCamera) {
        render.arCamera = arCamera
    }

    @Deprecated("Will be removed in 0.10.0")
    override fun onArLaneUpdated(arLane: ArLane) {
        render.arLane = arLane
    }

    @Deprecated("Will be removed in 0.10.0")
    override fun onNewFrame(
        rgbaBytes: ByteArray,
        imageFormat: ImageFormat,
        imageSize: ImageSize
    ) {
        render.onNewBackground(rgbaBytes)
    }

    @Deprecated("Will be removed in 0.10.0")
    override fun onNewCameraParameters(cameraParameters: CameraParameters) {
        // TODO change render
    }

    fun setLaneVisualParams(laneVisualParams: LaneVisualParams) {
        render.onNewLaneVisualParams(laneVisualParams)
    }

    private inner class ArViewListener : VideoSourceListener, VisionArEventsListener, VisionEventsListener {
        override fun onNewFrame(
            rgbaBytes: ByteArray,
            imageFormat: ImageFormat,
            imageSize: ImageSize
        ) {
            render.onNewBackground(rgbaBytes)
        }

        override fun onNewCameraParameters(cameraParameters: CameraParameters) = Unit

        /*VisionArEventsListener*/
        override fun onArCameraUpdated(arCamera: ArCamera) {
            render.arCamera = arCamera
        }

        override fun onArLaneUpdated(arLane: ArLane) {
            render.arLane = arLane
        }

        /*VisionArEventsListener*/
        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {
            super.onAuthorizationStatusUpdated(authorizationStatus)
        }

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {
            super.onFrameSegmentationUpdated(frameSegmentation)
        }

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {
            super.onFrameDetectionsUpdated(frameDetections)
        }

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {
            super.onFrameSignClassificationsUpdated(frameSignClassifications)
        }

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {
            super.onRoadDescriptionUpdated(roadDescription)
        }

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {
            super.onWorldDescriptionUpdated(worldDescription)
        }

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            super.onVehicleStateUpdated(vehicleState)
        }

        override fun onCameraUpdated(camera: Camera) {
            super.onCameraUpdated(camera)
        }

        override fun onCountryUpdated(country: Country) {
            super.onCountryUpdated(country)
        }

        override fun onUpdateCompleted() {
            super.onUpdateCompleted()
        }
    }
}
