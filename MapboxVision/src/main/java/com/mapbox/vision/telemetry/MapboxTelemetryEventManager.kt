package com.mapbox.vision.telemetry

import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.android.telemetry.VisionObjectDetectionEvent
import com.mapbox.vision.mobile.telemetry.TelemetryEventManager

internal class MapboxTelemetryEventManager(
    private val mapboxTelemetry: MapboxTelemetry
) : TelemetryEventManager {

    private var name: String? = null
    private val paramsMap: HashMap<String, Any> = HashMap()

    override fun setEventName(name: String) {
        this.name = name
    }

    override fun setInt(key: String, value: Int) {
        paramsMap[key] = value
    }

    override fun setDouble(key: String, value: Double) {
        paramsMap[key] = value
    }

    override fun setString(key: String, value: String) {
        paramsMap[key] = value
    }

    override fun pushEvent() {
        mapboxTelemetry.push(
            VisionObjectDetectionEvent(paramsMap["created"].toString()).apply {
                objectLatitude = paramsMap["object_lat"] as? Double ?: .0
                objectLongitude = paramsMap["object_lat"] as? Double ?: .0

                vehicleLatitude = paramsMap["vehicle_lat"] as? Double ?: .0
                vehicleLongitude = paramsMap["vehicle_lon"] as? Double ?: .0

                clazz = paramsMap["class"]?.toString() ?: ""
                signValue = paramsMap["sign_value"]?.toString() ?: ""

                objectSizeWidth = paramsMap["object_size_width"] as? Double ?: .0
                objectSizeHeight = paramsMap["object_size_height"] as? Double ?: .0

                objectPositionHeight = paramsMap["object_pos_height"] as? Double ?: .0
                distanceFromCamera = paramsMap["distance_from_camera"] as? Double ?: .0
            }
        )
    }
}
