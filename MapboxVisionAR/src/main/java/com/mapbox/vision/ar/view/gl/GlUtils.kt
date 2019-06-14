package com.mapbox.vision.ar.view.gl

import android.opengl.GLES20.*
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import com.mapbox.vision.utils.VisionLogger

fun WorldCoordinate.toGlCoordinate() = floatArrayOf(
    // `x` points right
    -y.toFloat(),
    // `y` points top
    z.toFloat(),
    // `z` points back
    -x.toFloat()
)

object GlUtils {
    private const val TAG = "GlArRender"

    fun glLoadShader(type: Int, shaderCode: String): Int {

        // create a vertex shader type (GL_VERTEX_SHADER) or a fragment shader type (GL_FRAGMENT_SHADER)
        val shader = glCreateShader(type)

        // add the source code to the shader and compile it
        glShaderSource(shader, shaderCode)
        glCheckError("load shader")
        glCompileShader(shader)
        glCheckError("compile shader")

        // TODO add glGetShaderiv

        return shader
    }

    fun glCheckError(glOperation: String) {
        if (BuildConfig.DEBUG) {
            var error = 0
            while ({ error = glGetError(); error }() != GL_NO_ERROR) {
                VisionLogger.d(TAG, "$glOperation: glError $error")
                throw RuntimeException("$glOperation: glError $error")
            }
        }
    }
}
