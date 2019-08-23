package com.mapbox.vision.sync

import com.mapbox.vision.mobile.core.models.Country

internal interface SyncManager {

    val baseDir: String

    fun start()

    fun stop()

    fun syncSessionDir(path: String)

    fun setCountry(country: Country)
}
