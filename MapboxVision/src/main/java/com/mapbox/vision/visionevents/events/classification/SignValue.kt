package com.mapbox.vision.visionevents.events.classification

import com.mapbox.vision.visionevents.events.detection.Detection

/**
 * @param type sign type
 * @param typeConfidence confidence for sign classification
 * @param number number classified (if any, eg. - speed)
 * @param numberConfidence confidence for number classification
 * @param detection source detection for sign
 */
data class SignValue(
        val type: SignType,
        val typeConfidence: Double,
        val number: Double,
        val numberConfidence: Double,
        val detection: Detection
)
