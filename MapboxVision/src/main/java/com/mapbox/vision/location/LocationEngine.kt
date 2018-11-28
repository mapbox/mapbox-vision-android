package com.mapbox.vision.location

import com.mapbox.vision.mobile.interfaces.LocationEngineListener

internal interface LocationEngine {

    fun attach(locationEngineListener: LocationEngineListener)

    fun detach()
}
