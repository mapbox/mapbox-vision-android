package com.mapbox.vision.ar.view.gl

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.ar.LaneVisualParams
import com.mapbox.vision.ar.core.models.ArCamera
import com.mapbox.vision.ar.core.models.ArLane
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.utils.VisionLogger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

internal class GlRender(
    private val context: Context,
    private val width: Int,
    private val height: Int
) : GLSurfaceView.Renderer {

    interface OnSurfaceChangedListener {
        fun onSurfaceChanged()
    }

    private val lane by lazy(mode = LazyThreadSafetyMode.NONE) { Lane(context) }
    private val background by lazy(mode = LazyThreadSafetyMode.NONE) { Background(width, height) }
    private var viewAspectRatio: Float? = 0f

    internal var arCamera: ArCamera? = null
    internal var arLane: ArLane? = null

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)

        lane.onSurfaceChanged()
        background.onSurfaceChanged()
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        // Adjust the viewport based on geometry changes, such as screen rotation
        GLES20.glViewport(0, 0, width, height)
        viewAspectRatio = width.toFloat() / height
    }

    private fun FloatArray.addPointToLaneParams(index: Int, worldCoordinate: WorldCoordinate) {
        // `x` points right
        set(index * 3, -worldCoordinate.y.toFloat())
        // `y` points top
        set(index * 3 + 1, worldCoordinate.z.toFloat())
        // `z` points back
        set(index * 3 + 2, -worldCoordinate.x.toFloat())
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        background.draw()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

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
        laneParams.addPointToLaneParams(0, arLane.bezierCurve.p1)
        laneParams.addPointToLaneParams(1, arLane.bezierCurve.p2)
        laneParams.addPointToLaneParams(2, arLane.bezierCurve.p3)
        laneParams.addPointToLaneParams(3, arLane.bezierCurve.p4)

        val viewProjMatrix = camera.getViewProjectionMatrix()
        val modelMatrix = Matrix4()
        val normMatrix = modelMatrix.toMatrix3()

        lane.draw(viewProjMatrix, modelMatrix, normMatrix, laneParams)
    }

    internal fun onNewBackground(rgbaArray: ByteArray) {
        background.updateTexture(rgbaArray)
    }

    internal fun onNewLaneVisualParams(laneVisualParams: LaneVisualParams) {
        lane.setLaneVisualParams(laneVisualParams)
    }

    companion object {

        private const val TAG = "GlArRender"

        fun loadShader(type: Int, shaderCode: String): Int {

            // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
            // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
            val shader = GLES20.glCreateShader(type)

            // add the source code to the shader and compile it
            GLES20.glShaderSource(shader, shaderCode)
            checkGlError("load shader")
            GLES20.glCompileShader(shader)
            checkGlError("compile shader")

            return shader
        }

        fun checkGlError(glOperation: String) {
            if (BuildConfig.DEBUG) {
                var error = 0
                while ({ error = GLES20.glGetError(); error }() != GLES20.GL_NO_ERROR) {
                    VisionLogger.d(TAG, "$glOperation: glError $error")
                    throw RuntimeException("$glOperation: glError $error")
                }
            }
        }
    }
}
