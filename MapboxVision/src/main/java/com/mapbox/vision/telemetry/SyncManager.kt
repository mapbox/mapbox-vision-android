package com.mapbox.vision.telemetry

interface SyncManager {

    val syncManagerType: HandlerSyncMangers.SyncMangerType
    
    fun start()
    
    fun stop()
    
    fun syncSessionDir(path: String)
}