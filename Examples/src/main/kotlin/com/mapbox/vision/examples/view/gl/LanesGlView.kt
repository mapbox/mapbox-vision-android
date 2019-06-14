
import android.opengl.GLES20.*
import com.mapbox.vision.ar.view.gl.GlRender
import com.mapbox.vision.ar.view.gl.GlUtils.glCheckError
import com.mapbox.vision.ar.view.gl.GlUtils.glLoadShader
import com.mapbox.vision.ar.view.gl.Matrix4
import com.mapbox.vision.ar.view.gl.directByteBufferOf
import com.mapbox.vision.ar.view.gl.toGlCoordinate
import com.mapbox.vision.mobile.core.models.world.WorldCoordinate
import java.nio.FloatBuffer

class LanesGlView : GlRender.Renderer {

    companion object {

        private val VERTEX_SHADER = """
                attribute vec4 aPosition;
                uniform mat4 uMVPMatrix;

                void main()
                {
                    gl_Position = uMVPMatrix * aPosition;
                }
            """.trimIndent()

        private val FRAGMENT_SHADER = """
                precision mediump float;
                uniform vec4 uColor;
                void main()
                {
                    gl_FragColor = uColor;
                }
            """.trimIndent()

        private const val BYTES_PER_PIXEL = 4
        private const val VERTICES_SIZE = 3
        private const val VERTICES_COUNT = 2
        private const val LINES_COUNT = 2
    }

    private var program: Int = 0
    private var uMVPMatrixHandle: Int = 0
    private var aPositionHandle: Int = 0
    private var uColorHandle: Int = 0

    private val dataBuffer: FloatBuffer = directByteBufferOf(capacity = LINES_COUNT * VERTICES_COUNT * VERTICES_SIZE * BYTES_PER_PIXEL)
        .asFloatBuffer()

    override fun onSurfaceCreated() {
        val vertexShader = glLoadShader(GL_VERTEX_SHADER,
            VERTEX_SHADER
        )
        val fragmentShader = glLoadShader(GL_FRAGMENT_SHADER,
            FRAGMENT_SHADER
        )

        program = glCreateProgram()
        glAttachShader(program, vertexShader)
        glAttachShader(program, fragmentShader)
        glLinkProgram(program)
        glCheckError("Lanes -> program")
    }

    fun draw(
        mvpMatrix: Matrix4,
        srcLeft: WorldCoordinate,
        dstLeft: WorldCoordinate,
        srcRight: WorldCoordinate,
        dstRight: WorldCoordinate
    ) {
        glUseProgram(program)
        glCheckError("Lanes -> glUseProgram")

        aPositionHandle = glGetAttribLocation(program, "aPosition")
        glEnableVertexAttribArray(aPositionHandle)
        glVertexAttribPointer(aPositionHandle,
            VERTICES_SIZE, GL_FLOAT, false, 0, dataBuffer)
        glCheckError("Lanes -> aPosition")

        uMVPMatrixHandle = glGetUniformLocation(program, "uMVPMatrix")
        glCheckError("Lanes -> uMVPMatrix")
        glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix.toFloatArray(), 0)

        dataBuffer.position(0)
        dataBuffer.put(srcLeft.toGlCoordinate())
        dataBuffer.put(dstLeft.toGlCoordinate())
        dataBuffer.put(srcRight.toGlCoordinate())
        dataBuffer.put(dstRight.toGlCoordinate())
        dataBuffer.position(0)

        uColorHandle = glGetUniformLocation(program, "uColor")
        glCheckError("Lanes -> uColor")

        glUniform4f(uColorHandle, 1f, 1f, 1f, 1f)
        glLineWidth(5f)
        glDrawArrays(GL_LINES, 0, VERTICES_COUNT * LINES_COUNT)

        glDisableVertexAttribArray(aPositionHandle)
    }
}
