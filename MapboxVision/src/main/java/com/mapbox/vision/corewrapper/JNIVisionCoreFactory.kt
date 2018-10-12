package com.mapbox.vision.corewrapper

import android.app.Application
import com.mapbox.vision.core.map.MapDataSource
import com.mapbox.vision.corewrapper.jni.JNICoreImpl
import com.mapbox.vision.repository.datasource.map.retrofit.RetrofitMapDataSourceImpl
import com.mapbox.vision.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.visionevents.events.Image

internal class JNIVisionCoreFactory(
        private val application: Application,
        private val mapboxTelemetryEventManager: MapboxTelemetryEventManager,
        private val mapDataSource: MapDataSource = RetrofitMapDataSourceImpl(),
        private val imageFormat: Image.Format = Image.Format.RGBA
) {

    fun createVisionCore(width: Int, height: Int): VisionCore =
            JNICoreImpl(
                    width,
                    height,
                    imageFormat,
                    mapDataSource,
                    application.getExternalFilesDir(null)!!.absolutePath,
                    application,
                    mapboxTelemetryEventManager
            )

}
