package com.mapbox.vision.manager

interface ModuleInterface {

    fun registerModule(ptr: Long)
    fun unregisterModule()
}
