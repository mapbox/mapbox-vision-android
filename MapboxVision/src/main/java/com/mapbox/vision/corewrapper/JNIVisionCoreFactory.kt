package com.mapbox.vision.corewrapper

import android.app.Application
import com.mapbox.vision.core.events.ImageSaver
import com.mapbox.vision.core.map.MapDataSource
import com.mapbox.vision.corewrapper.jni.JNIVisionCoreImpl
import com.mapbox.vision.repository.datasource.map.retrofit.RetrofitMapDataSourceImpl
import com.mapbox.vision.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.visionevents.events.Image

internal class JNIVisionCoreFactory(
        private val application: Application,
        private val eventManager: MapboxTelemetryEventManager,
        private val mapDataSource: MapDataSource = RetrofitMapDataSourceImpl(),
        private val imageFormat: Image.Format = Image.Format.RGBA,
        private val imageSaver: ImageSaver
) {

    fun createVisionCore(width: Int, height: Int): VisionCore =
            JNIVisionCoreImpl(
                    imageWidth = width,
                    imageHeight = height,
                    imageFormat = imageFormat,
                    mapDataSource = mapDataSource,
                    externalFileDir = application.getExternalFilesDir(null)!!.absolutePath,
                    application = application,
                    mapboxTelemetryEventManager = eventManager,
                    imageSaver = imageSaver
            )

}
