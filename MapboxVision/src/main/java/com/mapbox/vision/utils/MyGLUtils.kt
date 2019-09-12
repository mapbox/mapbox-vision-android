package com.mapbox.vision.utils

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

internal object MyGLUtils {

    private val TAG = "GLUtils"

    private val GL_TEXTURE_INDEX_0 = 0
    private val GL_TEXTURE_INDEX_1 = 1
    private val GL_TEXTURE_INDEX_2 = 2
    private val GL_TEXTURE_INDEX_3 = 3
    private val GL_TEXTURE_INDEX_4 = 4
    private val GL_TEXTURE_INDEX_5 = 5
    private val GL_TEXTURE_INDEX_6 = 6
    private val GL_TEXTURE_INDEX_7 = 7

    fun checkGlError(op: String) {
        val error = GLES20.glGetError()
        if (error != GLES20.GL_NO_ERROR) {
            val msg = op + ": glError 0x" + Integer.toHexString(error)
            throw RuntimeException(msg)
        }
    }

    fun loadProgram(vertShaderSrc: String, fragShaderSrc: String): Int {
        val vertexShader: Int = loadShader(GLES20.GL_VERTEX_SHADER, vertShaderSrc)
        val fragmentShader: Int = loadShader(GLES20.GL_FRAGMENT_SHADER, fragShaderSrc)
        val programObject: Int = GLES20.glCreateProgram()
        val linked = IntArray(1)

        // Load the vertex/fragment shaders
        if (vertexShader == 0) {
            return 0
        }

        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        // Create the program object

        if (programObject == 0) {
            return 0
        }

        GLES20.glAttachShader(programObject, vertexShader)
        GLES20.glAttachShader(programObject, fragmentShader)

        // Link the program
        GLES20.glLinkProgram(programObject)

        // Check the link status
        GLES20.glGetProgramiv(programObject, GLES20.GL_LINK_STATUS, linked, 0)

        if (linked[0] == 0) {
            VisionLogger.e(TAG, " Error log = " + GLES20.glGetProgramInfoLog(programObject))
            GLES20.glDeleteProgram(programObject)
            return 0
        }

        // Free up no longer needed shader resources
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return programObject
    }

    private fun loadShader(type: Int, shaderSrc: String): Int {
        val shader: Int = GLES20.glCreateShader(type)
        val compiled = IntArray(1)

        // Create the shader object

        if (shader == 0) {
            return 0
        }

        // Load the shader source
        GLES20.glShaderSource(shader, shaderSrc)

        // Compile the shader
        GLES20.glCompileShader(shader)

        // Check the compile status
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)

        if (compiled[0] == 0) {
            VisionLogger.e(TAG, " Error log = " + GLES20.glGetShaderInfoLog(shader))
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    fun createTexture(w: Int, h: Int): Int {
        val textureHandle = IntArray(1)

        GLES20.glGenTextures(1, textureHandle, 0)

        if (textureHandle[0] != 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0])

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE.toFloat())
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE.toFloat())

            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
        }

        if (textureHandle[0] == 0) {
            throw RuntimeException("Error loading texture.")
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        return textureHandle[0]
    }

    fun setupBlend() {
        try {
            // based on http://www.andersriggelsen.dk/glblendfunc.php
            GLES20.glBlendFuncSeparate(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA, GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            GLES20.glBlendEquationSeparate(GLES20.GL_FUNC_ADD, GLES20.GL_FUNC_ADD)
            //int srcRGB = GL_SRC_ALPHA
            //int dstRGB = GL_ONE_MINUS_SRC_ALPHA
            //int srcAlpha = GL_ONE
            //int dstAlpha = GL_ONE_MINUS_SRC_ALPHA
            // http://learningwebgl.com/blog/?p=859
        } catch (e: Exception) {
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        }

    }

    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(coords.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(coords)
        fb.rewind()
        return fb
    }

    fun setupVertexTextureBuffers(vertexArray: FloatArray, textureArray: FloatArray): IntArray {

        val vertexBuffer = createFloatBuffer(vertexArray)
        val textureBuffer = createFloatBuffer(textureArray)

        val vbo = IntArray(2)
        GLES20.glGenBuffers(2, vbo, 0)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, vertexArray.size * 4, vertexBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, textureArray.size * 4, textureBuffer, GLES20.GL_STATIC_DRAW)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        return vbo
    }

    fun setupSampler(samplerIndex: Int, location: Int, texture: Int, external: Boolean) {

        var glTexture = GLES20.GL_TEXTURE0
        var glUniformX = GL_TEXTURE_INDEX_0

        when (samplerIndex) {
            1 -> {
                glTexture = GLES20.GL_TEXTURE1
                glUniformX = GL_TEXTURE_INDEX_1
            }
            2 -> {
                glTexture = GLES20.GL_TEXTURE2
                glUniformX = GL_TEXTURE_INDEX_2
            }
            3 -> {
                glTexture = GLES20.GL_TEXTURE3
                glUniformX = GL_TEXTURE_INDEX_3
            }
            4 -> {
                glTexture = GLES20.GL_TEXTURE4
                glUniformX = GL_TEXTURE_INDEX_4
            }
            5 -> {
                glTexture = GLES20.GL_TEXTURE5
                glUniformX = GL_TEXTURE_INDEX_5
            }
            6 -> {
                glTexture = GLES20.GL_TEXTURE6
                glUniformX = GL_TEXTURE_INDEX_6
            }
            7 -> {
                glTexture = GLES20.GL_TEXTURE7
                glUniformX = GL_TEXTURE_INDEX_7
            }
        }

        val target = if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D

        GLES20.glActiveTexture(glTexture)
        GLES20.glBindTexture(target, texture)
        GLES20.glUniform1i(location, glUniformX)

    }

    fun calculateMvpMatrix(matrix: FloatArray, angle: Int, flip: Flip, translation: Float,
                           scaleX: Float = 1.0f, scaleY : Float = 1.0f): FloatArray {
        val rotate = FloatArray(16)
        val transPos = FloatArray(16)
        val transNeg = FloatArray(16)
        val temp = FloatArray(16)
        val temp2 = FloatArray(16)
        val scale = FloatArray(16)

        Matrix.setIdentityM(scale, 0)
        when (flip) {
            is Flip.FlipHorizontal -> Matrix.scaleM(scale, 0, -scaleX, scaleY, 1.0f)
            is Flip.FlipVertical -> Matrix.scaleM(scale, 0, scaleX, -scaleY, 1.0f)
            is Flip.FlipBoth -> Matrix.scaleM(scale, 0, -scaleX, -scaleY, 1.0f)
        }

        Matrix.setIdentityM(transPos, 0)
        Matrix.setIdentityM(transNeg, 0)
        Matrix.setIdentityM(rotate, 0)

        Matrix.translateM(transPos, 0, translation, translation, 0f)
        Matrix.translateM(transNeg, 0, -translation, -translation, 0f)

        Matrix.setRotateM(rotate, 0, angle.toFloat(), 0f, 0f, 1f)

        Matrix.multiplyMM(temp, 0, transPos, 0, rotate, 0)
        Matrix.multiplyMM(temp2, 0, temp, 0, scale, 0)
        Matrix.multiplyMM(matrix, 0, temp2, 0, transNeg, 0)

        return matrix
    }


    sealed class Flip {
        object FlipHorizontal : Flip()
        object FlipVertical : Flip()
        object FlipBoth : Flip()
        object FlipNone : Flip()
    }

}