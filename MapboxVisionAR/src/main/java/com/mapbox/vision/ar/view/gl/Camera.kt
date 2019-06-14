package com.mapbox.vision.ar.view.gl

import android.opengl.Matrix

class Camera(
    private val aspectRatio: Float,
    private val viewAspectRatio: Float,
    private val verticalFOVRadians: Float,
    private val rotation: Rotation,
    private val translate: Vector3
) {
    private val nearClipPlane: Float = 1f
    private val farClipPlane: Float = 1000f

    private fun getPerspectiveProjMatrix(): Matrix4 {
        val projMatrix = FloatArray(16)
        Matrix.perspectiveM(
                projMatrix,
                0,
                Math.toDegrees(verticalFOVRadians.toDouble()).toFloat(),
                viewAspectRatio,
                nearClipPlane,
                farClipPlane
        )

        return Matrix4(projMatrix)
    }

    private fun getViewMatrix(): Matrix4 {
        val viewMatrix = FloatArray(16)
        val center = Vector3(
                x = translate.x + Math.sin(rotation.roll.toDouble()).toFloat() * Math.sin(rotation.pitch.toDouble()).toFloat(),
                y = translate.y - Math.cos(rotation.roll.toDouble()).toFloat(),
                z = -(translate.z + Math.sin(rotation.roll.toDouble()).toFloat() * Math.cos(rotation.pitch.toDouble()).toFloat())
        )

        Matrix.setLookAtM(
                viewMatrix,
                0,
                translate.x,
                translate.y,
                translate.z,
                center.x,
                center.y,
                center.z,
                0f,
                1f,
                0f
        )
        return Matrix4(viewMatrix)
    }

    fun getViewProjectionMatrix(): Matrix4 {
        return getPerspectiveProjMatrix() * getViewMatrix()
    }
}
