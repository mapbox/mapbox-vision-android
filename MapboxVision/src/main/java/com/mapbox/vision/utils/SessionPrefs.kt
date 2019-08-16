package com.mapbox.vision.utils

import com.mapbox.vision.mobile.core.utils.preferences.LongAdapter
import com.mapbox.vision.mobile.core.utils.preferences.PreferenceModel

class SessionPrefs(sessionName: String) : PreferenceModel("session_prefs_$sessionName") {
    val sessionStartMillis by nullablePreference("session_start_millis", LongAdapter)

    val bytesSentPerSession by preference("bytes_sent_per_session", 0L, LongAdapter)
}
