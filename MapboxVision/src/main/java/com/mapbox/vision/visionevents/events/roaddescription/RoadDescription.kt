package com.mapbox.vision.visionevents.events.roaddescription

import com.mapbox.vision.core.buffers.RoadDescriptionDataBuffer
import com.mapbox.vision.visionevents.WorldCoordinate
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
            val visibleRevLanes = roadDescriptionArray[3].toInt()
            val seeLeftBorder = roadDescriptionArray[4] > 0
            val seeRightBorder = roadDescriptionArray[5] > 0
            val isValid = roadDescriptionArray[6] > 0

            val lines = ArrayList<Line>(visibleLeftLanes + 1 + visibleRightLanes)

            // Fill left lines
            for (i in 0 until visibleLeftLanes) {

                val isLeftBorder = (i == 0)

                val leftMarkingType = if (isLeftBorder && seeLeftBorder) {
                    MarkingType.CURB
                } else {
                    MarkingType.UNKNOWN
                }

                val leftLineWorldPoints = emptyList<WorldCoordinate>()
                val leftMarking = Marking(leftMarkingType, leftLineWorldPoints)

                val rightLineWorldPoints = emptyList<WorldCoordinate>()
                val rightMarking = Marking(MarkingType.SOLID, rightLineWorldPoints)

                val line = Line(0.0, Direction.BACKWARD, leftMarking, rightMarking)

                lines.add(line)
            }

            val currentTrackLeftLineWorldPoints = emptyList<WorldCoordinate>()
            val currentTrackLeftMarking = Marking(MarkingType.SOLID, currentTrackLeftLineWorldPoints)

            val currentTrackRightLineWorldPoints = emptyList<WorldCoordinate>()
            val currentTrackRightMarking = Marking(MarkingType.SOLID, currentTrackRightLineWorldPoints)

            val currentLine = Line(0.0, Direction.FORWARD, currentTrackLeftMarking, currentTrackRightMarking)
            lines.add(currentLine)


            // Fill right lines
            for (i in 0 until visibleRightLanes) {

                val isRightBorder = (i == 0)

                val leftMarkingType = if (isRightBorder && seeRightBorder) {
                    MarkingType.CURB
                } else {
                    MarkingType.UNKNOWN
                }

                val leftLineWorldPoints = emptyList<WorldCoordinate>()
                val leftMarking = Marking(leftMarkingType, leftLineWorldPoints)

                val rightLineWorldPoints = emptyList<WorldCoordinate>()
                val rightMarking = Marking(MarkingType.SOLID, rightLineWorldPoints)


                val line = Line(0.0, Direction.FORWARD, leftMarking, rightMarking)

                lines.add(line)

            }

            return RoadDescription(roadDescriptionDataBuffer.roadDescriptionIdentifier, lines, visibleLeftLanes, egoOffset)
        }
    }
}
