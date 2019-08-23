package com.mapbox.vision.sync.util

import com.mapbox.android.telemetry.MapboxTelemetryConstants.DEFAULT_CHINA_EVENTS_HOST
import com.mapbox.android.telemetry.MapboxTelemetryConstants.DEFAULT_COM_EVENTS_HOST
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.Country.China
import com.mapbox.vision.mobile.core.models.Country.Other
import com.mapbox.vision.mobile.core.models.Country.UK
import com.mapbox.vision.mobile.core.models.Country.USA
import com.mapbox.vision.mobile.core.models.Country.Unknown

internal sealed class FeatureEnvironment {

    abstract fun getHost(country: Country): String?

    abstract fun getBasePath(country: Country): String?
}

internal object TelemetryEnvironment : FeatureEnvironment() {
    override fun getHost(country: Country): String? {
        return when (country) {
            China -> DEFAULT_CHINA_EVENTS_HOST
            USA, UK, Other -> DEFAULT_COM_EVENTS_HOST
            Unknown -> null
        }
    }

    override fun getBasePath(country: Country): String? {
        return when (country) {
            China -> "Recordings_China"
            USA, UK, Other -> "Recordings_Other"
            Unknown -> null
        }
    }
}
