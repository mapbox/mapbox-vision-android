package com.mapbox.vision.visionevents.events.roaddescription

data class Line(
        val width: Double,
        val direction: Direction,
        val leftMarking: Marking,
        val rightMarking: Marking
)
