package com.mapbox.vision.ar.view.gl

import android.content.Context
import android.opengl.GLES20.*
import com.mapbox.vision.ar.LaneVisualParams
import com.mapbox.vision.ar.R
import com.mapbox.vision.ar.view.gl.GlUtils.glCheckError
import com.mapbox.vision.ar.view.gl.GlUtils.glLoadShader
import com.mapbox.vision.mobile.core.utils.extentions.copyFrom
import java.nio.FloatBuffer

class Lane(context: Context) : GlRender.Renderer {

    private companion object {

        internal const val LANE_DEFAULT_WIDTH = 1.0f // meters

        private val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uModelMatrix;
            uniform mat3 uNormMatrix;
            uniform vec3 uLaneParams[4];
            uniform float uLaneWidthRatio;

            attribute vec3 aPosition;
            attribute vec2 aTex;
            attribute vec3 aNormal;

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec2 vTexCoords;

            void main()
            {
                vec3 p0 = uLaneParams[0];
                vec3 p1 = uLaneParams[1];
                vec3 p2 = uLaneParams[2];
                vec3 p3 = uLaneParams[3];

                float t = (1.0 - aTex.y);
                float t_2 = t * t;
                float t_3 = t_2 * t;
                float t1 = 1.0 - t;
                float t1_2 = t1 * t1;
                float t1_3 = t1_2 * t1;

                vec3 basePoint = p0 * t1_3 + p1 * (3.0 * t * t1_2) + p2 * (3.0 * t_2 * t1) + p3 * t_3;
                vec3 baseDirection = 3.0 * (p1 - p0) * t1_2 + 6.0 * (p2 - p1) * t1 * t + 3.0 * (p3 - p2) * t_2;

                vec3 offsetVector = normalize(vec3(baseDirection.z, 0, -baseDirection.x));
                vec3 smoothedPos = basePoint - offsetVector * aPosition.x;

                float lineWidth = smoothedPos.x * uLaneWidthRatio; // smoothedPos.x = $LANE_DEFAULT_WIDTH by default

                vec4 worldPosition = uModelMatrix * vec4(lineWidth, aPosition.y + basePoint.y, smoothedPos.z, 1);

                vWorldPos = worldPosition.xyz;
                vWorldNormal = uNormMatrix * aNormal;
                vTexCoords = aTex;

                gl_Position = uMVPMatrix * worldPosition;
            }
            """.trimIndent()
        private val FRAGMENT_SHADER = """
            precision mediump float;

            uniform vec4 uColor;
            uniform vec4 uSpecularColor;
            uniform vec3 uCameraWorldPos;
            uniform vec3 uLightWorldPos;
            uniform vec3 uAmbientLightColor;
            uniform vec3 uLightColor;

            varying vec3 vWorldPos;
            varying vec3 vWorldNormal;
            varying vec2 vTexCoords;

            void main()
            {
                vec3 baseColor = uColor.xyz;

                vec3 N = normalize(vWorldNormal);
                vec3 V = normalize(uCameraWorldPos - vWorldPos);

                vec3 L = normalize(uLightWorldPos - vWorldPos);
                float diffuseIntensity = clamp(dot(N, L), 0.0, 1.0);
                vec3 H = normalize(L + V);
                float specularBase = clamp(dot(N, H), 0.0, 1.0);
                float specularIntensity = pow(specularBase, uSpecularColor.w);
                vec3 finalColor = uAmbientLightColor * baseColor +
                diffuseIntensity * uLightColor * baseColor +
                specularIntensity * uLightColor * uSpecularColor.xyz;

                gl_FragColor = vec4(finalColor.xyz, uColor.w * vTexCoords.y);
            }
            """.trimIndent()
    }

    private val laneColor = floatArrayOf(0.2745f, 0.4117f, 0.949f, 0.99f)
    private val laneAmbientColor = floatArrayOf(laneColor[0], laneColor[1], laneColor[2])
    private val laneSpecularColor = floatArrayOf(1f, 1f, 1f, 100f)
    private val laneLightColor = floatArrayOf(1f, 1f, 1f)
    private val laneLightPosition = floatArrayOf(0f, 7f, 0f)

    private val vertexBuffer: FloatBuffer
    private val normalsBuffer: FloatBuffer
    private val texBuffer: FloatBuffer
    private var mProgram: Int = 0
    private val trianglesNum: Int
    private var laneWidthRatio: Float = 1.0f

    private var aPositionHandle: Int = 0
    private var aTexHandle: Int = 0
    private var aNormalHandle: Int = 0

    private var uMVPMatrixHandle: Int = 0
    private var uModelMatrixHandle: Int = 0
    private var uLaneParamsHandle: Int = 0
    private var uColorHandle: Int = 0
    private var uSpecularColorHandle: Int = 0
    private var uCameraWorldPosHandle: Int = 0
    private var uLightWorldPosHandle: Int = 0
    private var uAmbientLightColorHandle: Int = 0
    private var uLightColorHandle: Int = 0
    private var uLaneWidthHandler: Int = 0

    private var cameraPosition: FloatArray = floatArrayOf(0f, 0f, 0f)

    init {
        val stream = context.resources.openRawResource(R.raw.lane)
        val objWrapper = ObjectWrapper(stream)

        trianglesNum = objWrapper.trianglesNum
        vertexBuffer = objWrapper.vertexBuffer
        normalsBuffer = objWrapper.normalsBuffer
        texBuffer = objWrapper.texBuffer
    }

    override fun onSurfaceCreated() {
        // prepare shaders and OpenGL program
        val vertexShader = glLoadShader(GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = glLoadShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        mProgram = glCreateProgram()             // create empty OpenGL Program
        glAttachShader(mProgram, vertexShader)   // add the vertex shader to program
        glAttachShader(mProgram, fragmentShader) // add the fragment shader to program
        glLinkProgram(mProgram)                  // create OpenGL program executables
        glCheckError("ArLane -> mProgram")
    }

    fun setLaneVisualParams(laneVisualParams: LaneVisualParams) {
        if (LaneVisualParams.isValid(laneVisualParams.color)) {
            laneColor[0] = laneVisualParams.color.r
            laneColor[1] = laneVisualParams.color.g
            laneColor[2] = laneVisualParams.color.b
            laneColor[3] = laneVisualParams.color.a
        }

        if (LaneVisualParams.isValid(laneVisualParams.ambientColor)) {
            laneAmbientColor[0] = laneVisualParams.ambientColor.r
            laneAmbientColor[1] = laneVisualParams.ambientColor.g
            laneAmbientColor[2] = laneVisualParams.ambientColor.b
        }

        if (LaneVisualParams.isValid(laneVisualParams.lightColor)) {
            laneLightColor[0] = laneVisualParams.lightColor.r
            laneLightColor[1] = laneVisualParams.lightColor.g
            laneLightColor[2] = laneVisualParams.lightColor.b
        }

        laneVisualParams.light?.let { light ->
            laneLightPosition.copyFrom(light.toGlCoordinate())
        }

        if (LaneVisualParams.isValid(laneVisualParams.width)) {
            laneWidthRatio = laneVisualParams.width.toFloat() / LANE_DEFAULT_WIDTH
        }
    }

    /**
     * Encapsulates the OpenGL ES instructions for drawing this shape.
     *
     * @param mvpMatrix - The Model View Project matrix in which to draw
     * this shape.
     */
    fun draw(mvpMatrix: Matrix4, modelMatrix: Matrix4, laneParams: FloatArray) {
        glUseProgram(mProgram)
        glCheckError("ArLane -> glUseProgram")

        aPositionHandle = glGetAttribLocation(mProgram, "aPosition")
        glEnableVertexAttribArray(aPositionHandle)
        glVertexAttribPointer(aPositionHandle, 3, GL_FLOAT, false, 12, vertexBuffer)
        glCheckError("ArLane -> aPositionHandle")

        aTexHandle = glGetAttribLocation(mProgram, "aTex")
        glEnableVertexAttribArray(aTexHandle)
        glVertexAttribPointer(aTexHandle, 2, GL_FLOAT, false, 8, texBuffer)
        glCheckError("ArLane -> aTex")

        aNormalHandle = glGetAttribLocation(mProgram, "aNormal")
        glEnableVertexAttribArray(aNormalHandle)
        glVertexAttribPointer(aNormalHandle, 3, GL_FLOAT, false, 12, normalsBuffer)
        glCheckError("ArLane -> aNormal")

        // / Uniforms

        uColorHandle = glGetUniformLocation(mProgram, "uColor")
        glUniform4fv(uColorHandle, 1, laneColor, 0)
        glCheckError("ArLane -> uColorHandle")

        uAmbientLightColorHandle = glGetUniformLocation(mProgram, "uAmbientLightColor")
        glUniform3fv(uAmbientLightColorHandle, 1, laneAmbientColor, 0)
        glCheckError("ArLane -> uAmbientLightColorHandle")

        uSpecularColorHandle = glGetUniformLocation(mProgram, "uSpecularColor")
        glUniform4fv(uSpecularColorHandle, 1, laneSpecularColor, 0)
        glCheckError("ArLane -> uSpecularColorHandle")

        uLightColorHandle = glGetUniformLocation(mProgram, "uLightColor")
        glUniform3fv(uLightColorHandle, 1, laneLightColor, 0)
        glCheckError("ArLane -> uLightColorHandle")

        uCameraWorldPosHandle = glGetUniformLocation(mProgram, "uCameraWorldPos")
        glUniform3fv(uCameraWorldPosHandle, 1, cameraPosition, 0)
        glCheckError("ArLane -> uCameraWorldPosHandle")

        uLightWorldPosHandle = glGetUniformLocation(mProgram, "uLightWorldPos")
        glUniform3fv(uLightWorldPosHandle, 1, laneLightPosition, 0)
        glCheckError("ArLane -> uLightWorldPosHandle")

        uMVPMatrixHandle = glGetUniformLocation(mProgram, "uMVPMatrix")
        glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix.toFloatArray(), 0)
        glCheckError("ArLane -> uMVPMatrixHandle")

        uModelMatrixHandle = glGetUniformLocation(mProgram, "uModelMatrix")
        glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix.toFloatArray(), 0)
        glCheckError("ArLane -> uModelMatrixHandle")

        uLaneParamsHandle = glGetUniformLocation(mProgram, "uLaneParams")
        glUniform3fv(uLaneParamsHandle, 4, laneParams, 0)
        glCheckError("ArLane -> uLaneParamsHandle")

        uLaneWidthHandler = glGetUniformLocation(mProgram, "uLaneWidthRatio")
        glUniform1f(uLaneWidthHandler, laneWidthRatio)
        glCheckError("ArLane -> uLaneWidthHandler")

        glEnable(GL_BLEND)
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)

        // Draw the triangle
        glDrawArrays(GL_TRIANGLES, 0, trianglesNum * 3)

        // Disable vertex array
        glDisableVertexAttribArray(aPositionHandle)
        glDisableVertexAttribArray(aTexHandle)
        glDisableVertexAttribArray(aNormalHandle)

        glDisable(GL_BLEND)
    }
}
