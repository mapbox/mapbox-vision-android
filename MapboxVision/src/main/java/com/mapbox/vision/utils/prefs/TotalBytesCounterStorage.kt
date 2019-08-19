package com.mapbox.vision.utils.prefs

import com.mapbox.vision.mobile.core.utils.preferences.LongAdapter
import com.mapbox.vision.mobile.core.utils.preferences.Preference
import com.mapbox.vision.mobile.core.utils.preferences.PreferenceModel

interface TotalBytesCounterStorage {
    val sessionStartMillis: Preference<Long>
    val bytesSentPerSession: Preference<Long>

    class Impl(prefPrefix: String) : TotalBytesCounterStorage, PreferenceModel("${prefPrefix}_total_bytes_counter") {
        override val sessionStartMillis by nullablePreference("session_start_millis", LongAdapter)

        override val bytesSentPerSession by preference("bytes_sent_per_session", 0L, LongAdapter)
    }
}
