package com.mapbox.vision.examples

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.Detection
import com.mapbox.vision.mobile.core.models.detection.DetectionClass
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.Image
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import java.nio.ByteBuffer
import kotlin.math.pow
import kotlin.math.sqrt
import kotlinx.android.synthetic.main.activity_custom_detection.*

class CustomDetectionActivityKt : BaseActivity() {

    private var visionManagerWasInit = false
    private lateinit var paint: Paint

    // VisionEventsListener handles events from Vision SDK on background thread.
    private val visionEventsListener = object : VisionEventsListener {

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {

            fun convertImageToBitmap(originalImage: Image): Bitmap {
                val bitmap = Bitmap.createBitmap(
                    originalImage.size.imageWidth,
                    originalImage.size.imageHeight,
                    Bitmap.Config.ARGB_8888
                )
                // prepare direct ByteBuffer that will hold camera frame data
                val buffer = ByteBuffer.allocateDirect(originalImage.sizeInBytes())
                // associate underlying native ByteBuffer with our buffer
                originalImage.copyPixels(buffer)
                buffer.rewind()
                // copy ByteBuffer to bitmap
                bitmap.copyPixelsFromBuffer(buffer)
                return bitmap
            }

            fun drawSingleDetection(canvas: Canvas, detection: Detection) {
                // first thing we get coordinates of bounding box
                val relativeBbox = detection.boundingBox
                // we need to transform them from relative (range [0, 1]) to absolute in terms of canvas(frame) size
                // we do not care about screen resolution at all - we will use cropCenter mode
                val absoluteBbox = RectF(
                    relativeBbox.left * canvas.width,
                    relativeBbox.top * canvas.height,
                    relativeBbox.right * canvas.width,
                    relativeBbox.bottom * canvas.height
                )
                // we want to draw circle bounds, we need radius and center for that
                val radius = sqrt(
                    (absoluteBbox.centerX() - absoluteBbox.left).pow(2) +
                            (absoluteBbox.centerY() - absoluteBbox.top).pow(2)
                )
                canvas.drawCircle(
                    absoluteBbox.centerX(),
                    absoluteBbox.centerY(),
                    radius,
                    paint
                )
            }

            val frameBitmap = convertImageToBitmap(frameDetections.frame.image)
            // now we will draw current detections on canvas with frame bitmap
            val canvas = Canvas(frameBitmap)
            for (detection in frameDetections.detections) {
                // we will draw only detected cars
                // and filter detections which we are not confident with
                if (detection.detectionClass == DetectionClass.Car && detection.confidence > 0.6) {
                    drawSingleDetection(canvas, detection)
                }
            }
            runOnUiThread {
                // finally we update our image view on main thread
                detections_view.setImageBitmap(frameBitmap)
            }
        }

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {}

        override fun onCameraUpdated(camera: Camera) {}

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preparePaint()
    }

    override fun onPermissionsGranted() {
        startVisionManager()
    }

    override fun initViews() {
        setContentView(R.layout.activity_custom_detection)
    }

    override fun onStart() {
        super.onStart()
        startVisionManager()
    }

    override fun onStop() {
        super.onStop()
        stopVisionManager()
    }

    private fun startVisionManager() {
        if (allPermissionsGranted() && !visionManagerWasInit) {
            VisionManager.create()
            VisionManager.setModelPerformance(
                ModelPerformance.On(
                    ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH
                )
            )
            VisionManager.visionEventsListener = visionEventsListener
            VisionManager.start()
            visionManagerWasInit = true
        }
    }

    private fun stopVisionManager() {
        if (visionManagerWasInit) {
            VisionManager.stop()
            VisionManager.destroy()
            visionManagerWasInit = false
        }
    }

    private fun preparePaint() {
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.GREEN
        paint.strokeWidth = 5f
        paint.style = Paint.Style.STROKE
    }
}
