package com.mapbox.vision.visionevents.events.roaddescription

import com.mapbox.vision.visionevents.WorldCoordinate

data class Marking(val type: MarkingType, val worldPoints: List<WorldCoordinate>)