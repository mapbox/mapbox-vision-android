package com.mapbox.vision.models

import java.util.*

data class DistanceToCar(
        val worldPosition: DoubleArray,
        val leftRelPosition: DoubleArray,
        val rightRelPosition: DoubleArray,
        val suggestedDeceleration: Float,
        val state: CollisionAlertState
) {
    enum class CollisionAlertState {
        CollisionAlertStateNotTriggered,
        CollisionAlertStateWarning,
        CollisionAlertStateCritical
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DistanceToCar

        if (!Arrays.equals(worldPosition, other.worldPosition)) return false
        if (!Arrays.equals(leftRelPosition, other.leftRelPosition)) return false
        if (!Arrays.equals(rightRelPosition, other.rightRelPosition)) return false
        if (suggestedDeceleration != other.suggestedDeceleration) return false
        if (state != other.state) return false

        return true
    }

    override fun hashCode(): Int {
        var result = Arrays.hashCode(worldPosition)
        result = 31 * result + Arrays.hashCode(leftRelPosition)
        result = 31 * result + Arrays.hashCode(rightRelPosition)
        result = 31 * result + suggestedDeceleration.hashCode()
        result = 31 * result + state.hashCode()
        return result
    }
}
