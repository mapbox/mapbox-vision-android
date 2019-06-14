package com.mapbox.vision.ar.view.gl

import android.opengl.GLES20.*
import android.opengl.Matrix
import com.mapbox.vision.ar.view.gl.GlUtils.glCheckError
import com.mapbox.vision.ar.view.gl.GlUtils.glLoadShader
import java.nio.ByteBuffer
import java.nio.FloatBuffer

class Background(
    private val texWidth: Int,
    private val texHeight: Int
) : GlRender.Renderer {

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

        private const val TRIANGLES_NUM = 2
        private const val TRIANGLE_SIZE = 3
        private const val BYTES_PER_PIXEL = 4
        private const val TEX_SIZE = 2
        private const val VERTICES_SIZE = 3
    }

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var aTexHandle: Int = 0

    private val vertexBuffer: FloatBuffer = directByteBufferOf(capacity = VERTICES_SIZE * TRIANGLE_SIZE * TRIANGLES_NUM * BYTES_PER_PIXEL).asFloatBuffer()
    private val texBuffer: FloatBuffer = directByteBufferOf(capacity = TEX_SIZE * TRIANGLE_SIZE * TRIANGLES_NUM * BYTES_PER_PIXEL).asFloatBuffer()
    private val textureBytes: ByteBuffer = directByteBufferOf(capacity = texWidth * texHeight * 4)
    private var textureHandler: Int = 0
    private var updateTexture: Boolean = false

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

    override fun onSurfaceCreated() {
        val vertexShader = glLoadShader(GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = glLoadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        program = glCreateProgram()
        glAttachShader(program, vertexShader)
        glAttachShader(program, fragmentShader)
        glLinkProgram(program)
        glCheckError("ArBackground -> program")

        textureHandler = createTexture()
    }

    private fun createTexture(): Int {
        val texIds = IntArray(1)
        glGenTextures(1, texIds, 0)
        return texIds[0]
    }

    fun draw() {
        glUseProgram(program)
        glCheckError("ArBackground -> glUseProgram")

        val aPositionHandle = glGetAttribLocation(program, "aPosition")
        glVertexAttribPointer(aPositionHandle, VERTICES_SIZE, GL_FLOAT, false, VERTICES_SIZE * BYTES_PER_PIXEL, vertexBuffer)
        glEnableVertexAttribArray(aPositionHandle)
        glCheckError("ArBackground -> aPositionHandle")

        val projMatrix = FloatArray(16)
        Matrix.orthoM(projMatrix, 0, 0f, 1f, 0f, 1f, -1f, 1f)

        val uMVPMatrixHandle = glGetUniformLocation(program, "uMVPMatrix")
        glUniformMatrix4fv(uMVPMatrixHandle, 1, false, projMatrix, 0)
        glCheckError("ArBackground -> uMVPMatrix")

        val aTexHandle = glGetAttribLocation(program, "aTex")
        glVertexAttribPointer(aTexHandle, TEX_SIZE, GL_FLOAT, false, TEX_SIZE * BYTES_PER_PIXEL, texBuffer)
        glEnableVertexAttribArray(aTexHandle)
        glCheckError("ArBackground -> aTex")

        val uTextureHandle = glGetUniformLocation(program, "uTexture")
        glUniform1i(uTextureHandle, 0)
        glCheckError("ArBackground -> uTexture")

        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, textureHandler)

        if (updateTexture) {
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, texWidth, texHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, textureBytes)

            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)

            updateTexture = false
        }

        glDrawArrays(GL_TRIANGLES, 0, 6)

        glBindTexture(GL_TEXTURE_2D, 0)

        glDisableVertexAttribArray(aPositionHandle)
        glDisableVertexAttribArray(aTexHandle)
    }

    fun updateTexture(byteArray: ByteArray) {
        updateTexture = true
        textureBytes.put(byteArray).position(0)
    }
}
