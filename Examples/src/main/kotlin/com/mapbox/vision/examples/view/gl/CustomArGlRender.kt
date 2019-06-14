package com.mapbox.vision.examples.view.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.mapbox.vision.ar.core.models.ArCamera
import com.mapbox.vision.ar.core.models.ArLane
import com.mapbox.vision.ar.view.gl.Background
import com.mapbox.vision.ar.view.gl.Camera
import com.mapbox.vision.ar.view.gl.Matrix4
import com.mapbox.vision.ar.view.gl.Rotation
import com.mapbox.vision.ar.view.gl.Vector3
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class CustomArGlRender(
    private val width: Int,
    private val height: Int
) : GLSurfaceView.Renderer {

    private val background by lazy(mode = LazyThreadSafetyMode.NONE) { Background(width, height) }
    private val maneuverPoints by lazy(mode = LazyThreadSafetyMode.NONE) { ManeuverPoints() }

    internal var arCamera: ArCamera? = null
    internal var arLane: ArLane? = null

    private var viewAspectRatio: Float = 0f

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        background.onSurfaceChanged()
        maneuverPoints.onSurfaceChanged()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        // Adjust the viewport based on geometry changes, such as screen rotation
        GLES20.glViewport(0, 0, width, height)
        viewAspectRatio = width.toFloat() / height
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        background.draw()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        val arCamera = this.arCamera ?: return
        val arLane = this.arLane ?: return

        val camera = Camera(
            aspectRatio = arCamera.aspectRatio,
            verticalFOVRadians = arCamera.verticalFOV,
            viewAspectRatio = viewAspectRatio,
            rotation = Rotation(
                roll = arCamera.roll,
                pitch = arCamera.pitch,
                yaw = arCamera.yaw
            ),
            translate = Vector3(
                x = 0f,
                y = arCamera.height,
                z = 0f
            )
        )
        val laneParams = FloatArray(4 * 3)
        laneParams.addPointToLaneParams(0, arLane.bezierCurve.p1)
        laneParams.addPointToLaneParams(1, arLane.bezierCurve.p2)
        laneParams.addPointToLaneParams(2, arLane.bezierCurve.p3)
        laneParams.addPointToLaneParams(3, arLane.bezierCurve.p4)

        val viewProjMatrix = camera.getViewProjectionMatrix()
        val modelMatrix = Matrix4()
        val normMatrix = modelMatrix.toMatrix3()

        maneuverPoints.draw(viewProjMatrix, modelMatrix, normMatrix, laneParams)
    }

    private fun FloatArray.addPointToLaneParams(index: Int, worldCoordinate: WorldCoordinate) {
        // `x` points right
        set(index * 3, -worldCoordinate.y.toFloat())
        // `y` points top
        set(index * 3 + 1, worldCoordinate.z.toFloat())
        // `z` points back
        set(index * 3 + 2, -worldCoordinate.x.toFloat())
    }

    fun onNewBackground(rgbaArray: ByteArray) {
        background.updateTexture(rgbaArray)
    }

}