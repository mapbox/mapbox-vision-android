package com.mapbox.vision.utils

import android.content.Context
import com.mapbox.vision.mobile.core.utils.extentions.inTransaction
import java.util.UUID

internal interface UuidHolder {

    companion object {
        private const val PREFS_UUID = "prefs_uuid"
        private const val UUID_KEY = "uuid"
    }

    class Impl(context: Context) : UuidHolder {
        private val prefs = context.getSharedPreferences(PREFS_UUID, Context.MODE_PRIVATE)

        val uniqueId: String by lazy {
            prefs.getString(UUID_KEY, null) ?: UUID.randomUUID().toString().also { uuid ->
                prefs.inTransaction {
                    putString(UUID_KEY, uuid)
                }
            }
        }
    }
}
