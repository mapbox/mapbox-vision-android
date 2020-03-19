package com.mapbox.vision.examples

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.util.AttributeSet
import android.view.View
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.vision.VisionReplayManager
import com.mapbox.vision.examples.POIActivityKt.POI
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.position.GeoCoordinate
import com.mapbox.vision.mobile.core.models.position.GeoLocation
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import kotlin.math.max
import kotlin.math.min

class POIView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    companion object {

        // POI will start to appear at this distance, starting with transparent and appearing gradually
        private const val DRAW_LABEL_MIN_DISTANCE_METERS = 250

        // POI will start to appear from transparent to non-transparent during this first meters of showing distance
        private const val DISTANCE_FOR_ALPHA_APPEAR_METERS = 50
        private const val LABEL_SIZE_METERS = 8
        private const val LABEL_ABOVE_GROUND_METERS = 4
    }

    var poiList: List<POI> = arrayListOf()

    private val paint = Paint()
    private val uiHandler = Handler()
    private var poiDrawDataList: MutableList<POIDrawData> = mutableListOf()

    data class POIState(val poi: POI, val distanceToVehicle: Int, val worldCoordinate: WorldCoordinate?)

    private data class POIDrawData(
        val poiBitmap: Bitmap,
        val poiBitmapRect: Rect,
        val poiBitmapAlpha: Int
    )

    fun updatePOIState(newVehicleLocation: GeoLocation) {
        if (poiList.isEmpty()) {
            return
        }
        val currentVehicleLatLng = LatLng(newVehicleLocation.geoCoordinate.latitude, newVehicleLocation.geoCoordinate.longitude)
        val poiStateList = calculatePOIStateListRegardingVehicle(currentVehicleLatLng)
        val poiStateListToShow = filterPOIStateListToShow(poiStateList)
        if (poiStateListToShow.isEmpty()) {
            return
        }
        val pointsDrawDataList = preparePOIDrawData(poiStateListToShow)
        uiHandler.post {
            this.poiDrawDataList = mutableListOf<POIDrawData>().apply {
                addAll(pointsDrawDataList)
            }
            invalidate()
        }
    }

    // Calculate POI distance to vehicle and WorldCoordinates regarding the vehicle
    private fun calculatePOIStateListRegardingVehicle(currentVehicleLatLng: LatLng): List<POIState> {
        return poiList.map {
            val latLng = LatLng(it.latitude, it.longitude)
            val worldCoordinate = VisionReplayManager.geoToWorld(GeoCoordinate(latLng.latitude, latLng.longitude))
            val distanceToVehicle = latLng.distanceTo(currentVehicleLatLng).toInt()
            POIState(it, distanceToVehicle, worldCoordinate)
        }
    }

    // Show only POI which is close enough and behind the car
    private fun filterPOIStateListToShow(poiStateList: List<POIState>): List<POIState> {
        return poiStateList.filter {
            val x = it.worldCoordinate?.x ?: 0.0
            // Check if POI is behind vehicle and close enough to start appearing
            (x > 0) && (it.distanceToVehicle < DRAW_LABEL_MIN_DISTANCE_METERS)
        }
    }

    private fun preparePOIDrawData(poiStateList: List<POIState>): List<POIDrawData> {
        val poiDrawDataList = mutableListOf<POIDrawData>()
        for (poiState in poiStateList) {
            val worldCoordinate = poiState.worldCoordinate ?: continue
            val poiBitmap = poiState.poi.bitmap ?: continue

            // Prepare bounding rect for POI in mobile screen coordinates
            val poiBitmapRect = calculatePOIScreenRect(worldCoordinate)
            val poiLabelAlpha = calculatePOILabelAlpha(poiState)
            val poiDrawData = POIDrawData(poiBitmap, poiBitmapRect, poiLabelAlpha)
            poiDrawDataList.add(poiDrawData)
        }
        return poiDrawDataList
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
            poiBitmapRect.left = x.toFloat().scaleX().toInt()
            poiBitmapRect.top = y.toFloat().scaleY().toInt()
        }

        // Calculate POI right bottom position on camera frame from real word coordinates
        VisionReplayManager.worldToPixel(worldRightBottom)?.run {
            poiBitmapRect.right = x.toFloat().scaleX().toInt()
            poiBitmapRect.bottom = y.toFloat().scaleY().toInt()
        }
        return poiBitmapRect
    }

    private fun calculatePOILabelAlpha(poiState: POIState): Int {
        val minDistance = min(DRAW_LABEL_MIN_DISTANCE_METERS - poiState.distanceToVehicle, DISTANCE_FOR_ALPHA_APPEAR_METERS)
        return ((minDistance / DISTANCE_FOR_ALPHA_APPEAR_METERS.toFloat()) * 255).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        for (p in poiDrawDataList) {
            with(p) {
                paint.alpha = poiBitmapAlpha
                canvas.drawBitmap(poiBitmap, null, poiBitmapRect, paint)
            }
        }
        super.onDraw(canvas)
    }

    private var scaleFactor = 1f
    private var scaledSize = ImageSize(1, 1)

    // Scale position of camera frame coordinate on device screen like android:scaleType="centerCrop"
    private fun Float.scaleX() = this * scaleFactor - (scaledSize.imageWidth - width) / 2
    private fun Float.scaleY() = this * scaleFactor - (scaledSize.imageHeight - height) / 2

    // Calculate scale metrics which will be used to project camera frame coordinates on device screen
    fun onCameraCalibrated(frameWidth: Int, frameHeight: Int) {
        scaleFactor = max(
            width.toFloat() / frameWidth,
            height.toFloat() / frameHeight
        )
        scaledSize = ImageSize(
            imageWidth = (frameWidth * scaleFactor).toInt(),
            imageHeight = (frameHeight * scaleFactor).toInt()
        )
    }
}
