package com.mapbox.vision.telemetry

import android.content.Context
import com.mapbox.android.telemetry.MapboxTelemetry

interface HandlerSyncMangers {

    enum class SyncMangerType(dirName: String) {
        Telemetry("Telemetry"),
        VisionPro("VisionPro");
    }

    class Impl(
        private val context: Context,
        private val mapboxTelemetry: MapboxTelemetry
    ) : HandlerSyncMangers {

//        private val syncManagers by lazy { arrayOf(telemetrySyncManager, visionProSyncManager) }

//        private val telemetrySyncManager: TelemetrySyncManager = TelemetrySyncManager.Impl(mapboxTelemetry, context)
//        private val visionProSyncManager: VisionProSyncManager = VisionProSyncManager.Impl(context)


        companion object {
//            const val DIR_TELEMETRY = "Telemetry"
//            const val DIR_VISION_PRO = "VisionPro"
        }
    }
}