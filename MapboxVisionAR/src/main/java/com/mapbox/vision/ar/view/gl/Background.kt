package com.mapbox.vision.ar.view.gl

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.FloatBuffer

internal class Background(
    private val texWidth: Int,
    private val texHeight: Int
) : GlRender.OnSurfaceChangedListener {

    companion object {
        private val VERTEX_SHADER = """
uniform mat4 uMVPMatrix;

attribute vec3 aPosition;
attribute vec2 aTex;

varying vec2 vTexCoords;

void main()
{
    vTexCoords = aTex;
    gl_Position = uMVPMatrix * vec4(aPosition.xyz, 1.0);
}
            """.trimIndent()
        private val FRAGMENT_SHADER = """
precision mediump float;

uniform sampler2D uTexture;

varying vec2 vTexCoords;

void main()
{
    vec4 color = texture2D(uTexture, vTexCoords);
    gl_FragColor = vec4(color.xyz, 1.0); //vec4(texture2D(uTexture, vTexCoords).x, vTexCoords.x, vTexCoords.y, 1.0);
}
            """.trimIndent()
    }

    private var mProgram: Int = 0

    private val vertexBuffer: FloatBuffer = directByteBufferOf(capacity = 18 * 4).asFloatBuffer()
    private val texBuffer: FloatBuffer = directByteBufferOf(capacity = 12 * 4).asFloatBuffer()
    private var textureHandler: Int = 0
    private val textureBytes: ByteBuffer = directByteBufferOf(capacity = texWidth * texHeight * 4)
    private var needTextureUpdate: Boolean = false

    init {
        vertexBuffer
                .put(
                        floatArrayOf(
                                0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 0f,
                                0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f
                        )
                )
                .position(0)

        texBuffer
                .put(
                        floatArrayOf(
                                0f, 1f, 1f, 1f, 1f, 0f,
                                0f, 1f, 1f, 0f, 0f, 0f
                        )
                )
                .position(0)
    }

    override fun onSurfaceChanged() {
        // prepare shaders and OpenGL program
        val vertexShader = GlRender.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = GlRender.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        mProgram = GLES20.glCreateProgram() // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader) // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader) // add the fragment shader to program
        GLES20.glLinkProgram(mProgram) // create OpenGL program executables
        GlRender.checkGlError("ArBackground -> mProgram")

        textureHandler = createTexture()
    }

    private fun createTexture(): Int {
        val texs = IntArray(1)
        GLES20.glGenTextures(1, texs, 0)
        return texs[0]
    }

    private fun updateTexture() {
        if (!needTextureUpdate) {
            return
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandler)

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, texWidth, texHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, textureBytes)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        needTextureUpdate = false
    }

    fun draw() {
        GLES20.glUseProgram(mProgram)
        GlRender.checkGlError("ArBackground.glUseProgram")

        // get handle to vertex shader's vPosition member
        val aPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        GlRender.checkGlError("ArLane -> aPositionHandle")

        val aTexHandle = GLES20.glGetAttribLocation(mProgram, "aTex")
        GLES20.glEnableVertexAttribArray(aTexHandle)
        GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 8, texBuffer)
        GlRender.checkGlError("ArLane -> aTex")

        val projMatrix = FloatArray(16)
        Matrix.orthoM(projMatrix, 0, 0f, 1f, 0f, 1f, -1f, 1f)

        val uMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, projMatrix, 0)
        GlRender.checkGlError("ArLane -> uMVPMatrixHandle")

        updateTexture()

        val uTextureHandle = GLES20.glGetUniformLocation(mProgram, "uTexture")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandler)
        GLES20.glUniform1i(uTextureHandle, 0)
        GlRender.checkGlError("ArLane -> TextureUniform")

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6)

        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexHandle)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    fun updateTexture(byteArray: ByteArray) {
        needTextureUpdate = true
        textureBytes.put(byteArray).position(0)
    }
}
