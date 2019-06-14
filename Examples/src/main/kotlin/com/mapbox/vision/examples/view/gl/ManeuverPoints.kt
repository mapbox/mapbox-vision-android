package com.mapbox.vision.examples.view.gl

import android.opengl.GLES20.*
import com.mapbox.vision.ar.view.gl.GlUtils.glCheckError
import com.mapbox.vision.ar.view.gl.GlUtils.glLoadShader
import com.mapbox.vision.ar.view.gl.Matrix3
import com.mapbox.vision.ar.view.gl.Matrix4
import com.mapbox.vision.ar.view.gl.directByteBufferOf
import java.nio.FloatBuffer
import java.nio.ShortBuffer

class ManeuverPoints {

    private val trianglesNum: Int

    private val vertexBuffer: FloatBuffer
    private val indexBuffer: ShortBuffer
    private val indexArray: ShortArray

    private var mProgram: Int = 0

    private var aPositionHandle = 0
    private var aTexHandle = 0
    private var uMVPMatrixHandle = 0
    private var uModelMatrixHandle = 0
    private var uRoutePointHandle = 0

    private val vbo = IntArray(1)
    private val ibo = IntArray(1)

    companion object {
        private val VERTEX_SHADER: String = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            uniform vec3 uLaneParams[4];

            attribute vec3 aPosition;
            attribute vec3 aTex;

            varying vec3 vTexCoords;

            void main()
            {
                vec3 p0 = uLaneParams[0];
                vec3 p1 = uLaneParams[1];
                vec3 p2 = uLaneParams[2];
                vec3 p3 = uLaneParams[3];

                float t = (1.0 - aTex.z);
                float t_2 = t * t;
                float t_3 = t_2 * t;
                float t1 = 1.0 - t;
                float t1_2 = t1 * t1;
                float t1_3 = t1_2 * t1;

                vec3 basePoint = p0 * t1_3 + p1 * (3.0 * t * t1_2) + p2 * (3.0 * t_2 * t1) + p3 * t_3;
                vec3 baseDirection = 3.0 * (p1 - p0) * t1_2 + 6.0 * (p2 - p1) * t1 * t + 3.0 * (p3 - p2) * t_2;

                float d0 = dot(vec3(0, 0, -1), normalize(baseDirection));
                float d = 1.0 - d0;

                float hOffset = 0.0;
                if ((baseDirection.x > 0.0 && aPosition.x < 0.0) || (baseDirection.x < 0.0 && aPosition.x > 0.0))
                {
                  hOffset = d;
                }

                vec3 offsetVector = normalize(vec3(baseDirection.z, 0, -baseDirection.x));
                vec3 smoothedPos = basePoint - offsetVector * aPosition.x * d0;

                vec4 worldPosition = uModelMatrix * vec4(smoothedPos.x, aPosition.y + basePoint.y + hOffset * 1.8, smoothedPos.z, 1);

                vTexCoords = vec3(aTex.xy, clamp(0.0, 1.0, d));

                gl_Position = uMVPMatrix * worldPosition;
            }
        """.trimIndent()


        private val FRAGMENT_SHADER = """
            varying vec3 vTexCoords;

            void main()
            {
                gl_FragColor = vec4(0.0, 0.0, 0.5 + vTexCoords.z * 0.5, 1.0);

            }
        """.trimIndent()
    }

    init {
        val sectionsNum = 250
        trianglesNum = sectionsNum * 2
        val halfVertexNum = sectionsNum + 1
        val vertexNum = halfVertexNum * 2

        val attribsCount = 6

        indexArray = ShortArray(sectionsNum * 2 * 3)
        val vertexArray = FloatArray(size = vertexNum * attribsCount)

        for (vrtx in 0 until vertexNum) {
            val z = -0.1f * (vrtx % halfVertexNum)
            val x = if (vrtx > halfVertexNum) -1f else 1f
            val y = 0f
            val u = vrtx / 5f
            val v = if (vrtx > halfVertexNum) 1f else 0f
            val l = if (vrtx >= halfVertexNum) {
                (vrtx.toFloat() - halfVertexNum) / halfVertexNum
            } else {
                vrtx.toFloat() / halfVertexNum
            }

            val vrtxOffset = vrtx * attribsCount

            vertexArray[vrtxOffset] = x
            vertexArray[vrtxOffset + 1] = y
            vertexArray[vrtxOffset + 2] = z
            vertexArray[vrtxOffset + 3] = u
            vertexArray[vrtxOffset + 4] = v
            vertexArray[vrtxOffset + 5] = (1 - l)
        }

        var index = 0
        for (elm in 0 until sectionsNum) {
            indexArray[index++] = elm.toShort()
            indexArray[index++] = (elm + 1).toShort()
            indexArray[index++] = (elm + halfVertexNum).toShort()

            indexArray[index++] = (elm + 1).toShort()
            indexArray[index++] = (elm + halfVertexNum + 1).toShort()
            indexArray[index++] = (elm + halfVertexNum).toShort()
        }

        indexBuffer = directByteBufferOf(capacity = indexArray.size * 2).asShortBuffer() //ShortBuffer.wrap(indexArray)
        indexBuffer.put(indexArray).position(0)

        vertexBuffer = directByteBufferOf(capacity = vertexArray.size * 4).asFloatBuffer()
        vertexBuffer.put(vertexArray).position(0)
    }

    fun onSurfaceChanged() {
        // prepare shaders and OpenGL program
        val vertexShader = glLoadShader(GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = glLoadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        glCompileShader(vertexShader)
        glCompileShader(fragmentShader)

        mProgram = glCreateProgram()             // create empty OpenGL Program
        glAttachShader(mProgram, vertexShader)   // add the vertex shader to program
        glAttachShader(mProgram, fragmentShader) // add the fragment shader to program
        glLinkProgram(mProgram)                  // create OpenGL program executables
        glCheckError("ManeuverPoints -> mProgram")
    
        glGenBuffers(1, vbo, 0)
        glCheckError("ManeuverPoints -> glGenBuffers vbo")
        glGenBuffers(1, ibo, 0)
        glCheckError("ManeuverPoints -> glGenBuffers ibo")

        glBindBuffer(GL_ARRAY_BUFFER, vbo[0])
        glCheckError("ManeuverPoints -> glBindBuffer vbo")
        glBufferData(GL_ARRAY_BUFFER, vertexBuffer.capacity() * 4, vertexBuffer, GL_STATIC_DRAW)
        glCheckError("ManeuverPoints -> glBindBuffer vbo capacity")

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        glBufferData(
            GL_ELEMENT_ARRAY_BUFFER,
            indexBuffer.capacity() * 2,
            indexBuffer,
            GL_STATIC_DRAW
        )
        glCheckError("ManeuverPoints -> glBindBuffer ibo")

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)
        glCheckError("ManeuverPoints -> glBindBuffer enable")
    }

    fun draw(vpMatrix: Matrix4, modelMatrix: Matrix4, normMatrix: Matrix3, routePoint: FloatArray) {

        glBindBuffer(GL_ARRAY_BUFFER, vbo[0])
        glCheckError("ManeuverPoints glBindBuffer vbo")

        // Add program to OpenGL environment
        glUseProgram(mProgram)
        glCheckError("ManeuverPoints.glUseProgram")

        // get handle to vertex shader's vPosition member
        aPositionHandle = glGetAttribLocation(mProgram, "aPosition")
        glEnableVertexAttribArray(aPositionHandle)
        glVertexAttribPointer(aPositionHandle, 3, GL_FLOAT, false, 24, 0)
        glCheckError("ManeuverPoints -> aPositionHandle")

        aTexHandle = glGetAttribLocation(mProgram, "aTex")
        glEnableVertexAttribArray(aTexHandle)
        glVertexAttribPointer(aTexHandle, 3, GL_FLOAT, false, 24, 12)
        glCheckError("ManeuverPoints -> aTexHandle")

        /// Uniforms

        uMVPMatrixHandle = glGetUniformLocation(mProgram, "uMVPMatrix")
        glUniformMatrix4fv(uMVPMatrixHandle, 1, false, vpMatrix.toFloatArray(), 0)
        glCheckError("ManeuverPoints -> uMVPMatrixHandle")

        uModelMatrixHandle = glGetUniformLocation(mProgram, "uModelMatrix")
        glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix.toFloatArray(), 0)
        glCheckError("ManeuverPoints -> uModelMatrixHandle")

        uRoutePointHandle = glGetUniformLocation(mProgram, "uLaneParams")
        glUniform3fv(uRoutePointHandle, 4, routePoint, 0)
        glCheckError("ManeuverPoints -> uRoutePointHandle")

        glCullFace(GL_FRONT_AND_BACK)
        glCheckError("ManeuverPoints.glCullFace")

        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, ibo[0])
        glCheckError("ManeuverPoints glBindBuffer ibo")

        // Draw the triangle
        glDrawElements(GL_TRIANGLES, trianglesNum * 3, GL_UNSIGNED_SHORT, 0)
        glCheckError("ManeuverPoints.glDrawArrays")

        glBindBuffer(GL_ARRAY_BUFFER, 0)
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, 0)

        // Disable vertex array
        glDisableVertexAttribArray(aPositionHandle)

    }
}
