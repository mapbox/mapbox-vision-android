package com.mapbox.vision.sync.syncmanager

internal interface SyncManager {

    fun start()

    fun stop()

    fun syncSessionDir(path: String)
}
