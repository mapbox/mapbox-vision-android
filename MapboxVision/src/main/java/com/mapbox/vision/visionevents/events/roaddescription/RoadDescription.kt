package com.mapbox.vision.visionevents.events.roaddescription

import com.mapbox.vision.core.buffers.RoadDescriptionDataBuffer
import java.util.*

/**
 * @property identifier unique identifier
 * @property lines list of lines
 * @property currentLane number of current lane
 * @property currentLaneRelativePosition relative position of car in current line. 0 means left line border, 1 - right line border.
 */
data class RoadDescription(
        val identifier: Long,
        val lines: List<Line>,
        val currentLane: Int,
        val currentLaneRelativePosition: Double
) {

    companion object {

        internal fun fromRoadDescriptionBuffer(roadDescriptionDataBuffer: RoadDescriptionDataBuffer): RoadDescription {

            val roadDescriptionArray = roadDescriptionDataBuffer.roadDescriptionArray
            val egoOffset = roadDescriptionArray[0]
            val visibleLeftLanes = roadDescriptionArray[1].toInt()
            val visibleRightLanes = roadDescriptionArray[2].toInt()
            val visibleReverseLanes = roadDescriptionArray[3].toInt()
            val seeLeftBorder = roadDescriptionArray[4] > 0
            val seeRightBorder = roadDescriptionArray[5] > 0
            val isValid = roadDescriptionArray[6] > 0
            val width = roadDescriptionArray[7]

            // FIXME handle me
//            if (!isValid) {
//                return null OR RoodDescription.Invalid
//            }

            val sameDirectionVisibleLanes = visibleLeftLanes + 1 + visibleRightLanes
            val allVisibleLanes = sameDirectionVisibleLanes + visibleReverseLanes
            val lines = ArrayList<Line>(allVisibleLanes)

            for (laneIndex in (0..visibleReverseLanes)) {
                val leftMarkingType = when {
                    laneIndex != 0 -> MarkingType.DASHES // not left border
                    seeLeftBorder -> MarkingType.CURB
                    else -> MarkingType.UNKNOWN
                }

                val rightMarkingType = when {
                    laneIndex != visibleReverseLanes - 1 -> MarkingType.DASHES
                    else -> MarkingType.SOLID
                }

                lines.add(
                        Line(
                                width = width,
                                direction = Direction.BACKWARD,
                                leftMarking = Marking(
                                        leftMarkingType,
                                        worldPoints = emptyList() // TODO fill lane points
                                ),
                                rightMarking = Marking(
                                        rightMarkingType,
                                        worldPoints = emptyList()// TODO fill lane points
                                )
                        )
                )
            }

            for (laneIndex in (0..sameDirectionVisibleLanes)) {
                val leftMarkingType = when {
                    laneIndex != 0 -> MarkingType.DASHES
                    visibleReverseLanes != 0 -> MarkingType.DOUBLE_SOLID
                    seeLeftBorder -> MarkingType.CURB
                    else -> MarkingType.UNKNOWN
                }

                val rightMarkingType = when {
                    laneIndex != sameDirectionVisibleLanes - 1 -> MarkingType.DASHES
                    seeRightBorder -> MarkingType.CURB
                    else -> MarkingType.UNKNOWN
                }

                lines.add(
                        Line(
                                width = width,
                                direction = Direction.FORWARD,
                                leftMarking = Marking(
                                        leftMarkingType,
                                        worldPoints = emptyList()// TODO fill lane points
                                ),
                                rightMarking = Marking(
                                        rightMarkingType,
                                        worldPoints = emptyList()// TODO fill lane points
                                )
                        )
                )
            }

            return RoadDescription(
                    identifier = roadDescriptionDataBuffer.roadDescriptionIdentifier,
                    lines = lines,
                    currentLane = visibleLeftLanes,
                    currentLaneRelativePosition = egoOffset
            )
        }
    }
}
