package com.mapbox.vision.visionevents

/**
 * Vehicle state relative road lanes.
 */
enum class LaneDepartureState {
    /**
     * Vehicle does not cross any lanes
     */
    Normal,
    /**
     * Vehicle is near to cross a lane
     */
    Warning,
    /**
     * Vehicle is crossing the lane
     */
    Alert
}