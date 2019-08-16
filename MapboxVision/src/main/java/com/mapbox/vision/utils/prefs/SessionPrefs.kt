package com.mapbox.vision.utils.prefs

import com.mapbox.vision.mobile.core.utils.preferences.LongAdapter
import com.mapbox.vision.mobile.core.utils.preferences.PreferenceModel

object SessionPrefs : PreferenceModel("session_prefs") {
    val sessionStartMillis by nullablePreference("session_start_millis", LongAdapter)

    val bytesSentPerSession by preference("bytes_sent_per_session", 0L, LongAdapter)
}
