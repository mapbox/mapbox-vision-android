package com.mapbox.vision.corewrapper

import android.app.Application
import com.mapbox.vision.core.events.ImageSaver
import com.mapbox.vision.core.map.HttpClient
import com.mapbox.vision.corewrapper.jni.JNIVisionCoreImpl
import com.mapbox.vision.repository.datasource.map.retrofit.RetrofitHttpClientImpl
import com.mapbox.vision.telemetry.MapboxTelemetryEventManager
import com.mapbox.vision.visionevents.events.Image

internal class JNIVisionCoreFactory(
        private val application: Application,
        private val eventManager: MapboxTelemetryEventManager,
        private val httpClient: HttpClient = RetrofitHttpClientImpl(),
        private val imageFormat: Image.Format = Image.Format.RGBA,
        private val imageSaver: ImageSaver
) {

    fun createVisionCore(width: Int, height: Int): VisionCore =
            JNIVisionCoreImpl(
                    imageWidth = width,
                    imageHeight = height,
                    imageFormat = imageFormat,
                    httpClient = httpClient,
                    externalFileDir = application.getExternalFilesDir(null)!!.absolutePath,
                    application = application,
                    mapboxTelemetryEventManager = eventManager,
                    imageSaver = imageSaver
            )

}
