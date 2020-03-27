package com.mapbox.vision.examples

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.os.Environment
import android.view.View.GONE
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.vision.VisionReplayManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoLocation
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import kotlin.math.min
import kotlinx.android.synthetic.main.activity_main.vision_view
import kotlinx.android.synthetic.main.activity_poi.*

class POIActivityKt : BaseActivity() {

    companion object {

        // POI will start to appear at this distance, starting with transparent and appearing gradually
        private const val MIN_DISTANCE_METERS_FOR_DRAW_LABEL = 400

        // POI will start to appear from transparent to non-transparent during this first meters of showing distance
        private const val DISTANCE_FOR_ALPHA_APPEAR_METERS = 150
        private const val LABEL_SIZE_METERS = 16
        private const val LABEL_ABOVE_GROUND_METERS = 8

        // Download session from tutorial and push to device
        private val SESSION_PATH = "${Environment.getExternalStorageDirectory().absolutePath}/session"
    }

    private val poiList: List<POI> by lazy { providePOIList() }

    private var visionReplayManagerWasInit = false

    private val visionEventsListener = object : VisionEventsListener {

        private var cameraCalibrated = false
        private val paint = Paint()
        private var bitmapCameraFrame = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        private var canvasCameraFrame = Canvas()

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {}

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onCameraUpdated(camera: Camera) {
            if (camera.calibrationProgress == 1.0f && !cameraCalibrated) {
                cameraCalibrated = true
                bitmapCameraFrame = Bitmap.createBitmap(camera.frameWidth, camera.frameHeight, Bitmap.Config.ARGB_8888)
                canvasCameraFrame = Canvas(bitmapCameraFrame)
                runOnUiThread {
                    camera_calibration_text.visibility = GONE
                }
            } else {
                runOnUiThread {
                    camera_calibration_text.text = getString(R.string.camera_calibration_progress, (camera.calibrationProgress * 100).toInt())
                }
            }
        }

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            if (cameraCalibrated) {
                updatePOIStateAndDraw(vehicleState.geoLocation)
            }
        }

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}

        private fun updatePOIStateAndDraw(newVehicleGeoLocation: GeoLocation) {
            if (poiList.isEmpty()) {
                return
            }

            val poiStateList = calculatePOIStateListRegardingVehicle(newVehicleGeoLocation.geoCoordinate)
            val poiStateListToShow = filterPOIByDistance(poiStateList)
            if (poiStateListToShow.isEmpty()) {
                return
            }
            val poiDrawDataList = preparePOIDrawData(poiStateListToShow)
            updateBitmapByPOIList(bitmapCameraFrame, poiDrawDataList)
            runOnUiThread {
                poi_view.setImageBitmap(bitmapCameraFrame)
            }
        }

        // Calculate POI distance to vehicle and WorldCoordinates regarding the vehicle
        private fun calculatePOIStateListRegardingVehicle(currentVehicleGeoCoordinate: GeoCoordinate): List<POIState> {
            val currentVehicleLatLng = LatLng(currentVehicleGeoCoordinate.latitude, currentVehicleGeoCoordinate.longitude)
            return poiList.mapNotNull {
                val latLng = LatLng(it.latitude, it.longitude)
                val geoCoordinate = GeoCoordinate(latLng.latitude, latLng.longitude)
                val worldCoordinate = VisionReplayManager.geoToWorld(geoCoordinate) ?: return@mapNotNull null
                val distanceToVehicle = latLng.distanceTo(currentVehicleLatLng).toInt()
                POIState(it, distanceToVehicle, worldCoordinate)
            }
        }

        // Show only POI which is close enough and behind the car
        private fun filterPOIByDistance(poiStateList: List<POIState>) = poiStateList.filter {
            val x = it.worldCoordinate.x
            // Check if POI is behind vehicle and close enough to start appearing
            (x > 0) && (it.distanceToVehicle < MIN_DISTANCE_METERS_FOR_DRAW_LABEL)
        }

        private fun preparePOIDrawData(poiStateList: List<POIState>): List<POIDrawData> = poiStateList.map { poiState ->
            // Prepare bounding rect for POI in mobile screen coordinates
            val poiBitmapRect = calculatePOIScreenRect(poiState.worldCoordinate)
            val poiLabelAlpha = calculatePOILabelAlpha(poiState)
            POIDrawData(poiState.poi.bitmap, poiBitmapRect, poiLabelAlpha)
        }

        private fun calculatePOIScreenRect(poiWorldCoordinate: WorldCoordinate): Rect {
            // Calculate left top coordinate of POI in real world using POI world coordinate
            val worldLeftTop = poiWorldCoordinate.copy(
                    y = poiWorldCoordinate.y + LABEL_SIZE_METERS / 2,
                    z = poiWorldCoordinate.z + LABEL_ABOVE_GROUND_METERS + LABEL_SIZE_METERS
            )

            // Calculate right bottom coordinate of POI in real world using POI world coordinate
            val worldRightBottom = poiWorldCoordinate.copy(
                    y = poiWorldCoordinate.y - LABEL_SIZE_METERS / 2,
                    z = poiWorldCoordinate.z + LABEL_ABOVE_GROUND_METERS
            )
            val poiBitmapRect = Rect(0, 0, 0, 0)

            // Calculate POI left top position on camera frame from real word coordinates
            VisionReplayManager.worldToPixel(worldLeftTop)?.run {
                poiBitmapRect.left = x
                poiBitmapRect.top = y
            }

            // Calculate POI right bottom position on camera frame from real word coordinates
            VisionReplayManager.worldToPixel(worldRightBottom)?.run {
                poiBitmapRect.right = x
                poiBitmapRect.bottom = y
            }
            return poiBitmapRect
        }

        private fun calculatePOILabelAlpha(poiState: POIState): Int {
            val minDistance = min(MIN_DISTANCE_METERS_FOR_DRAW_LABEL - poiState.distanceToVehicle, DISTANCE_FOR_ALPHA_APPEAR_METERS)
            return ((minDistance / DISTANCE_FOR_ALPHA_APPEAR_METERS.toFloat()) * 255).toInt()
        }

        private fun updateBitmapByPOIList(bitmap: Bitmap, poiDrawDataList: List<POIDrawData>) {
            canvasCameraFrame.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
            for (p in poiDrawDataList) {
                with(p) {
                    paint.alpha = poiBitmapAlpha
                    canvasCameraFrame.drawBitmap(poiBitmap, null, poiBitmapRect, paint)
                }
            }
        }
    }

    override fun onPermissionsGranted() {
        startVisionManager()
    }

    override fun initViews() {
        setContentView(R.layout.activity_poi)
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
            VisionReplayManager.create(SESSION_PATH)
            VisionReplayManager.visionEventsListener = visionEventsListener
            vision_view.setVisionManager(VisionReplayManager)
            VisionReplayManager.start()
            visionReplayManagerWasInit = true
        }
    }

    private fun stopVisionManager() {
        if (visionReplayManagerWasInit) {
            VisionReplayManager.stop()
            VisionReplayManager.destroy()
            visionReplayManagerWasInit = false
        }
    }

    private fun providePOIList(): List<POI> {

        val poiGasStation = POI(
                longitude = 27.674764394760132,
                latitude = 53.9405971055192,
                bitmap = getBitmapFromAssets("ic_gas_station.png"))

        val poiCarWash = POI(
                longitude = 27.675944566726685,
                latitude = 53.94105180084251,
                bitmap = getBitmapFromAssets("ic_car_wash.png"))

        return arrayListOf(poiGasStation, poiCarWash)
    }

    private fun getBitmapFromAssets(asset: String): Bitmap {
        val assetManager = this.assets
        val stream = assetManager.open(asset)
        return BitmapFactory.decodeStream(stream)
    }

    data class POI(val longitude: Double, val latitude: Double, val bitmap: Bitmap)

    data class POIDrawData(val poiBitmap: Bitmap, val poiBitmapRect: Rect, val poiBitmapAlpha: Int)

    data class POIState(val poi: POI, val distanceToVehicle: Int, val worldCoordinate: WorldCoordinate)
}
