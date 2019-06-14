package com.mapbox.vision.ar.view.gl

import android.opengl.GLES20
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.utils.VisionLogger

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
        while ({ error = GLES20.glGetError();error }() != GLES20.GL_NO_ERROR) {
            VisionLogger.d("Gl Error", "$glOperation: glError $error")
            throw RuntimeException("$glOperation: glError $error")
        }
    }
}