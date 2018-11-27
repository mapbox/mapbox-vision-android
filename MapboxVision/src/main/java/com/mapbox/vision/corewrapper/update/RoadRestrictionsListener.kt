package com.mapbox.vision.corewrapper.update

import com.mapbox.vision.visionevents.events.roadrestrictions.SpeedLimit

interface RoadRestrictionsListener {

    fun speedLimitUpdated(speedLimit: SpeedLimit)
}
