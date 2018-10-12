package com.mapbox.vision.location

internal interface LocationEngine {

    fun attach(locationEngineListener: LocationEngineListener)

    fun detach()

    fun isAttached() : Boolean


}
