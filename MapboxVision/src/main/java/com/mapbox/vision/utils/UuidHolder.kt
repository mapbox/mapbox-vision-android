package com.mapbox.vision.utils

import android.content.Context
import com.mapbox.vision.mobile.core.utils.extentions.inTransaction
import java.util.UUID

internal interface UuidHolder {

    companion object {
        private const val PREFS_UUID = "prefs_uuid"
        private const val UUID_KEY = "uuid"
    }

    fun start()
    fun stop()

    class Impl(context: Context) : UuidHolder {
        private val prefs = context.getSharedPreferences(PREFS_UUID, Context.MODE_PRIVATE)

        var uniqueId: String = ""
            private set

        override fun start() {
            if (uniqueId.isBlank()) {
                uniqueId = prefs.getString(UUID_KEY, null) ?: UUID.randomUUID().toString().also { uuid ->
                    prefs.inTransaction {
                        putString(UUID_KEY, uuid)
                    }
                }
            }
        }

        override fun stop() = Unit
    }
}
