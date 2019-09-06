package com.mapbox.vision.sync

import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.session.EnvironmentSettings
import com.mapbox.vision.sync.util.FeatureEnvironment
import com.mapbox.vision.sync.util.TelemetryEnvironment
import com.mapbox.vision.sync.util.VisionProEnvironment
import okhttp3.OkHttpClient

internal interface SyncClient<T, R : FeatureEnvironment> : EnvironmentSettings.CountryChange {

    val client: T

    class Telemetry(
        override val client: MapboxTelemetry,
        private val environment: TelemetryEnvironment
    ) : SyncClient<MapboxTelemetry, TelemetryEnvironment> {

        override fun newCountry(country: Country) {
            // TODO remove when fix is no more necessary
            client.setBaseUrl(environment.getHost(country))

            client.updateDebugLoggingEnabled(BuildConfig.DEBUG)
        }
    }

    class VisionPro(
        override val client: OkHttpClient,
        private val environment: VisionProEnvironment
    ): SyncClient<OkHttpClient, VisionProEnvironment> {

        override fun newCountry(country: Country) {
            // do nothing
        }
    }
}