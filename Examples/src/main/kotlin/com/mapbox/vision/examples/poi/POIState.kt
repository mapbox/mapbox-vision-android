package com.mapbox.vision.examples.poi

import com.mapbox.vision.mobile.core.models.world.WorldCoordinate

data class POIState(val poi: POI, val distanceToVehicle: Int, val worldCoordinate: WorldCoordinate)
