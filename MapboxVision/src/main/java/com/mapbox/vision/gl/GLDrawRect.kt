package com.mapbox.vision.gl

import android.opengl.GLES20
import com.mapbox.vision.utils.MyGLUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class GLDrawRect : GLReleasable {

    private val SHADER_VEC = " " +
            "  uniform mat4 uMatrix;                                                        \n" +
            "  attribute vec2 a_position;                                                   \n" +
            "  void main()                                                                  \n" +
            "  {                                                                            \n" +
            "     gl_Position = uMatrix * vec4(a_position.xy, 0.0, 1.0);                    \n" +
            "  }                                                                            \n"


    private val SHADER_FRAG = " " +
            "  precision mediump float;                                                        \n" +
            "  uniform vec4 uBorderColor;                                                      \n" +
            "  void main()                                                                     \n" +
            "  {                                                                               \n" +
            "     gl_FragColor = uBorderColor;                                                 \n" +
            "  }                                                                               \n"


    private val mProgramHandle: Int
    private val mAttributePosition: Int
    private val mUniformMatrix: Int
    private val mUniformBorderColor: Int
    private val mVBO: IntArray
    private val mDefaultLineWidth = FloatArray(1)
    private val mVerticesBuffer: FloatBuffer

    init {

        val byteBuffer = ByteBuffer.allocateDirect(8 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        mVerticesBuffer = byteBuffer.asFloatBuffer()

        mVBO = IntArray(1)
        GLES20.glGenBuffers(1, mVBO, 0)

        GLES20.glGetFloatv(GLES20.GL_LINE_WIDTH, mDefaultLineWidth, 0)
        GLES20.glLineWidth(5f)

        mProgramHandle = MyGLUtils.loadProgram(SHADER_VEC, SHADER_FRAG)

        // Vertex shader
        mAttributePosition = GLES20.glGetAttribLocation(mProgramHandle, "a_position")
        mUniformMatrix = GLES20.glGetUniformLocation(mProgramHandle, "uMatrix")

        // Fragment shader
        mUniformBorderColor = GLES20.glGetUniformLocation(mProgramHandle, "uBorderColor")
    }


    fun draw(vertices: FloatArray, mvpMat : FloatArray, borderColor : Int) {

        val r = (borderColor shr 16 and 0xFF).toFloat() / 255.0f
        val g = (borderColor shr 8 and 0xFF).toFloat() / 255.0f
        val b = (borderColor and 0xFF).toFloat() / 255.0f

        GLES20.glUseProgram(mProgramHandle)
        GLES20.glUniformMatrix4fv(mUniformMatrix, 1, false, mvpMat, 0)
        GLES20.glUniform4f(mUniformBorderColor, r, g, b, 1.0f)

        mVerticesBuffer.rewind()
        mVerticesBuffer.put(vertices)
        mVerticesBuffer.rewind()

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVBO[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 8 * 4, mVerticesBuffer, GLES20.GL_DYNAMIC_DRAW)

        GLES20.glVertexAttribPointer(mAttributePosition, 2, GLES20.GL_FLOAT, false, 4 * 2, 0)
        GLES20.glEnableVertexAttribArray(mAttributePosition)

        // Drawing
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4)

        // Clearing
        GLES20.glDisableVertexAttribArray(mAttributePosition)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glUseProgram(0)

    }

    override fun release() {
        GLES20.glLineWidth(mDefaultLineWidth[0])
        GLES20.glDeleteProgram(mProgramHandle)
        GLES20.glDeleteBuffers(mVBO.size, mVBO, 0)
    }
}