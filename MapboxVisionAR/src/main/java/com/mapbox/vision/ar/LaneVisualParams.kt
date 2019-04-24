package com.mapbox.vision.ar

import com.mapbox.vision.ar.core.models.Color
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate

/**
 * Parameters used by [com.mapbox.vision.ar.view.gl.VisionArView] to draw AR lane.
 *
 * @property color RGBA color of a lane
 * @property width width of lane in meters
 * @property light position of light source
 * @property lightColor RGBA color of a light source
 * @property ambientColor ambient color
 */
class LaneVisualParams(
    val color: Color = INVALID_COLOR,
    val width: Double = -1.0,
    val light: WorldCoordinate? = null,
    val lightColor: Color = INVALID_COLOR,
    val ambientColor: Color = INVALID_COLOR
) {
    companion object {

        private val INVALID_COLOR = Color(-1f, -1f, -1f, -1f)

        @JvmStatic
        fun isValid(color: Color): Boolean {
            if (color.a !in (0f..1f)) return false
            if (color.r !in (0f..1f)) return false
            if (color.g !in (0f..1f)) return false
            if (color.b !in (0f..1f)) return false

            return true
        }

        @JvmStatic
        fun isValid(width: Double): Boolean = width >= 0.0
    }
}
