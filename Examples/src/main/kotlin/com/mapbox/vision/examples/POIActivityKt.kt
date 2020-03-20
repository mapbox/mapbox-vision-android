package com.mapbox.vision.examples

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.view.View.GONE
import com.mapbox.vision.VisionReplayManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import kotlinx.android.synthetic.main.activity_main.vision_view
import kotlinx.android.synthetic.main.activity_poi.*

class POIActivityKt : BaseActivity() {

    private var visionReplayManagerWasInit = false

    // Download session from tutorial and push to device
    private var sessionPath = "${Environment.getExternalStorageDirectory().absolutePath}/session"

    private val visionEventsListener = object : VisionEventsListener {

        private var cameraCalibrated = false

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {}

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            if (cameraCalibrated) {
                poi_view.updatePOIState(vehicleState.geoLocation)
            }
        }

        override fun onCameraUpdated(camera: Camera) {
            if (camera.calibrationProgress == 1.0f && !cameraCalibrated) {
                cameraCalibrated = true
                poi_view.onCameraCalibrated(camera.frameWidth, camera.frameHeight)
                runOnUiThread{
                    camera_calibration_text.visibility = GONE
                }
            } else {
                runOnUiThread {
                    camera_calibration_text.text = getString(R.string.camera_calibration_progress, (camera.calibrationProgress * 100).toInt())
                }
            }
        }

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}
    }

    override fun onPermissionsGranted() {
        startVisionManager()
    }

    override fun initViews() {
        setContentView(R.layout.activity_poi)
        poi_view.poiList = buildPOIList()
    }

    override fun onStart() {
        super.onStart()
        startVisionManager()
    }

    override fun onStop() {
        super.onStop()
        stopVisionManager()
    }

    override fun onResume() {
        super.onResume()
        vision_view.onResume()
    }

    override fun onPause() {
        super.onPause()
        vision_view.onPause()
    }

    private fun startVisionManager() {
        if (allPermissionsGranted() && !visionReplayManagerWasInit) {
            VisionReplayManager.create(sessionPath)
            VisionReplayManager.visionEventsListener = visionEventsListener
            vision_view.setVisionManager(VisionReplayManager)
            VisionReplayManager.start()
            visionReplayManagerWasInit = true
        }
    }

    data class POI(
        val longitude: Double,
        val latitude: Double,
        val bitmap: Bitmap
    )

    private fun stopVisionManager() {
        if (visionReplayManagerWasInit) {
            VisionReplayManager.stop()
            VisionReplayManager.destroy()
            visionReplayManagerWasInit = false
        }
    }

    private fun buildPOIList(): List<POI> {

        val poiHamburgers = POI(
                27.68255352973938,
                53.94267477012304,
                getBitmapFromAssets("ic_hamburger.png"))

        val poiGasStation = POI(
                27.674764394760132,
                53.9405971055192,
                getBitmapFromAssets("ic_gas_station.png"))

        val poiHighWay = POI(
                27.673187255859375,
                53.940477115649095,
                getBitmapFromAssets("ic_highway.png"))

        val poiCarWash = POI(
                27.675944566726685,
                53.94105180084251,
                getBitmapFromAssets("ic_car_wash.png"))

        return arrayListOf(poiHamburgers, poiGasStation, poiHighWay, poiCarWash)
    }

    private fun getBitmapFromAssets(asset: String): Bitmap {
        val assetManager = this.assets
        val stream = assetManager.open(asset)
        return BitmapFactory.decodeStream(stream)
    }
}
