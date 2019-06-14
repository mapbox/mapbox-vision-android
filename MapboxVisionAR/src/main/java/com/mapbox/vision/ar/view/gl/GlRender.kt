package com.mapbox.vision.ar.view.gl

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import com.mapbox.vision.ar.LaneVisualParams
import com.mapbox.vision.ar.core.models.ArCamera
import com.mapbox.vision.ar.core.models.ArLane
import com.mapbox.vision.mobile.core.utils.extentions.copyFrom
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GlRender(
    private val context: Context,
    private val width: Int,
    private val height: Int
) : GLSurfaceView.Renderer {

    interface Renderer {
        fun onSurfaceCreated()
    }

    private val lane by lazy(mode = LazyThreadSafetyMode.NONE) { Lane(context) }
    private val background by lazy(mode = LazyThreadSafetyMode.NONE) { Background(width, height) }
    private var viewAspectRatio: Float? = 0f

    var arCamera: ArCamera? = null
    var arLane: ArLane? = null

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        glClearColor(0f, 0f, 0f, 0f)
        glEnable(GL_DEPTH_TEST)
        glDepthFunc(GL_LEQUAL)

        lane.onSurfaceCreated()
        background.onSurfaceCreated()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        glViewport(0, 0, width, height)
        viewAspectRatio = width.toFloat() / height
    }

    override fun onDrawFrame(unused: GL10) {
        glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT)
        glDisable(GL_DEPTH_TEST)

        background.draw()

        glEnable(GL_DEPTH_TEST)

        val arCamera = this.arCamera ?: return
        val arLane = this.arLane ?: return

        val camera = Camera(
            aspectRatio = arCamera.aspectRatio,
            verticalFOVRadians = arCamera.verticalFOV,
            viewAspectRatio = viewAspectRatio!!,
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
        laneParams.copyFrom(arLane.bezierCurve.p1.toGlCoordinate())
        laneParams.copyFrom(arLane.bezierCurve.p2.toGlCoordinate(), destinationOffset = 3)
        laneParams.copyFrom(arLane.bezierCurve.p3.toGlCoordinate(), destinationOffset = 6)
        laneParams.copyFrom(arLane.bezierCurve.p4.toGlCoordinate(), destinationOffset = 9)

        val viewProjMatrix = camera.getViewProjectionMatrix()
        val modelMatrix = Matrix4()
        lane.draw(viewProjMatrix, modelMatrix, laneParams)
    }

    fun onNewBackground(rgbaArray: ByteArray) {
        background.updateTexture(rgbaArray)
    }

    internal fun onNewLaneVisualParams(laneVisualParams: LaneVisualParams) {
        lane.setLaneVisualParams(laneVisualParams)
    }
}
