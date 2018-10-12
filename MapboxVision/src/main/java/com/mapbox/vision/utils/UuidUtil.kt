package com.mapbox.vision.utils

import android.content.Context
import java.util.*

internal class UuidUtil(private val context: Context) {

    companion object {
        private const val PREF_UNIQUE_ID_KEY = "prefs"
    }

    val uniqueId: String by lazy {
        context.getSharedPreferences(PREF_UNIQUE_ID_KEY, Context.MODE_PRIVATE).run {
            var uniqueID = getString(PREF_UNIQUE_ID_KEY, null)
            if (uniqueID.isNullOrEmpty()) {
                uniqueID = UUID.randomUUID().toString()
                with(edit()) {
                    putString(PREF_UNIQUE_ID_KEY, uniqueID)
                    apply()
                }
            }
            uniqueID
        }
    }
}
