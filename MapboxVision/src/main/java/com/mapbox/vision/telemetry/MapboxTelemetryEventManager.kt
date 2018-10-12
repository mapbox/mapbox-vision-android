package com.mapbox.vision.telemetry

import com.mapbox.android.telemetry.Event
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.android.telemetry.VisionEvent
import com.mapbox.android.telemetry.VisionEventFactory
import com.mapbox.vision.core.events.EventManager
import java.util.*

internal class MapboxTelemetryEventManager(
        private val mapboxTelemetry: MapboxTelemetry
) : EventManager {

    private var name: String? = null
    private val paramsMap: HashMap<String, Any> = HashMap()

    // Methods for JNI
    override fun setEventName(name: String) {
        this.name = name
    }

    override fun putInt(key: String, value: Int) {
        paramsMap[key] = value
    }

    override fun putDouble(key: String, value: Double) {
        paramsMap[key] = value
    }

    override fun putString(key: String, value: String) {
        paramsMap[key] = value
    }

    override fun pushEvent() {
        val event = VisionEventFactory().createVisionEvent(Event.Type.VIS_GENERAL) as VisionEvent
        event.contents = HashMap<String, Any>(paramsMap)
        event.setName(name)
        mapboxTelemetry.push(event)
        paramsMap.clear()
    }

}
