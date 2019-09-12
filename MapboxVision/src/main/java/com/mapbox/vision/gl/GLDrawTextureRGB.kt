package com.mapbox.vision.gl

import android.opengl.GLES20
import com.mapbox.vision.utils.MyGLUtils

class GLDrawTextureRGB : GLReleasable {

    private val SHADER_VEC = " " +
            "  uniform mat4 uTexMatrix;                        \n" +
            "  attribute vec4 a_position;                      \n" +
            "  attribute vec2 a_texCoord;                      \n" +
            "  varying vec2 v_texCoord;                        \n" +
            "  void main()                                     \n" +
            "  {                                               \n" +
            "     gl_Position = a_position;                    \n" +
            "     vec4 texCoord = vec4(a_texCoord, 0.0, 1.0);  \n" +
            "     v_texCoord = (uTexMatrix * texCoord).xy;     \n" +
            "  }                                               \n"


    private val SHADER_FRAG = " " +
            "  precision mediump float;                             \n" +
            "  varying vec2 v_texCoord;                             \n" +
            "  uniform sampler2D s_baseMap;                         \n" +
            "  void main()                                          \n" +
            "  {                                                    \n" +
            "     gl_FragColor = texture2D(s_baseMap, v_texCoord);  \n" +
            "  }                                                    \n"

    private val DEPTH = 0.0f

    private val RECTANGLE_VERTEX = floatArrayOf(
            -1.0f, -1.0f, DEPTH, // 0 bottom left
            1.0f, -1.0f, DEPTH, // 1 bottom right
            -1.0f, 1.0f, DEPTH, // 2 top left
            1.0f, 1.0f, DEPTH)  // 3 top right

    private val RECTANGLE_TEXTURE_UV = floatArrayOf(
            0.0f, 0.0f, // 0 bottom left
            1.0f, 0.0f, // 1 bottom right
            0.0f, 1.0f, // 2 top left
            1.0f, 1.0f  // 3 top right
    )

    private val COORDS_PER_VERTEX = 3
    private val COORDS_UV_PER_TEXTURE = 2
    private val VERTEX_STRIDE = COORDS_PER_VERTEX * 4
    private val TEXTURE_STRIDE = COORDS_UV_PER_TEXTURE * 4

    private val mVertexCount: Int
    private val mProgramHandle: Int
    private val mAttributePosition: Int
    private val mAttributeTextureCoord: Int
    private val mUniformTextureMatrix: Int
    private val mUniformSampler: Int
    private val mVBO: IntArray

    init {

        mVertexCount = RECTANGLE_VERTEX.size / COORDS_PER_VERTEX
        mVBO = MyGLUtils.setupVertexTextureBuffers(RECTANGLE_VERTEX, RECTANGLE_TEXTURE_UV)

        mProgramHandle = MyGLUtils.loadProgram(SHADER_VEC, SHADER_FRAG)

        // Vertex shader
        mAttributePosition = GLES20.glGetAttribLocation(mProgramHandle, "a_position")
        mAttributeTextureCoord = GLES20.glGetAttribLocation(mProgramHandle, "a_texCoord")

        mUniformTextureMatrix = GLES20.glGetUniformLocation(mProgramHandle, "uTexMatrix")

        // Fragment Shader

        mUniformSampler = GLES20.glGetUniformLocation(mProgramHandle, "s_baseMap")
    }

    fun draw(textureID: Int, matrix: FloatArray) {

        GLES20.glUseProgram(mProgramHandle)

        // Vertex Shader Buffers
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBO[0])
        GLES20.glVertexAttribPointer(mAttributePosition, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, VERTEX_STRIDE, 0)
        GLES20.glEnableVertexAttribArray(mAttributePosition)

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBO[1])
        GLES20.glVertexAttribPointer(mAttributeTextureCoord, COORDS_UV_PER_TEXTURE, GLES20.GL_FLOAT, false, TEXTURE_STRIDE, 0)
        GLES20.glEnableVertexAttribArray(mAttributeTextureCoord)

        // Vertex Shader - Uniforms
        GLES20.glUniformMatrix4fv(mUniformTextureMatrix, 1, false, matrix, 0)

        // Fragment Shader - Texture

        MyGLUtils.setupSampler(0, mUniformSampler, textureID, false)

        // Drawing
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, mVertexCount)

        // Clearing
        GLES20.glDisableVertexAttribArray(mAttributePosition)
        GLES20.glDisableVertexAttribArray(mAttributeTextureCoord)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glUseProgram(0)

    }

    override fun release() {
        GLES20.glDeleteProgram(mProgramHandle)
        GLES20.glDeleteBuffers(mVBO.size, mVBO, 0)
    }

}