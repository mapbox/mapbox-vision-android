package com.mapbox.vision.utils

import android.content.Context
import java.util.*

internal interface UuidHolder {

    companion object {
        private const val PREFS_UUID = "prefs_uuid"
        private const val UUID_KEY = "uuid"
        private const val BACKGROUND_TIMESTAMP_KEY = "timestamp"
        private const val MAX_BACKGROUND_TO_REFRESH_UUID_MILLIS = 60 * 1000L
    }

    fun start()
    fun stop()

    class Impl(context: Context) : UuidHolder {
        private val prefs = context.getSharedPreferences(PREFS_UUID, Context.MODE_PRIVATE)

        var uniqueId: String = ""
            private set

        override fun start() {
            val backgroundTimestamp = prefs.getLong(BACKGROUND_TIMESTAMP_KEY, 0L)
            val currentTimestamp = System.currentTimeMillis()
            if (currentTimestamp - backgroundTimestamp > MAX_BACKGROUND_TO_REFRESH_UUID_MILLIS) {
                uniqueId = UUID.randomUUID().toString()
                prefs.edit().putString(UUID_KEY, uniqueId).apply()
            } else {
                uniqueId = prefs.getString(UUID_KEY, "")!!
            }
        }

        override fun stop() {
            prefs.edit().putLong(BACKGROUND_TIMESTAMP_KEY, System.currentTimeMillis()).apply()
        }
    }
}
