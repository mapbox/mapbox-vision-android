package com.mapbox.vision.ar.view.gl

import android.content.Context
import android.opengl.GLES20
import com.mapbox.vision.ar.LaneVisualParams
import com.mapbox.vision.ar.R
import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import java.nio.FloatBuffer

internal class Lane(context: Context) : GlRender.OnSurfaceChangedListener {

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
    private var uNormMatrixHandle: Int = 0
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
        val obj = ObjUtils.convertToRenderable(ObjReader.read(stream))

        // transform to plain triangles to avoid using of indices buffer
        val objIndices = ObjData.getFaceVertexIndices(obj)
        val objVertices = ObjData.getVertices(obj)
        val objTexCoords = ObjData.getTexCoords(obj, 2)
        val objNormals = ObjData.getNormals(obj)

        trianglesNum = obj.numFaces
        val vertexesNum = trianglesNum * 3
        val vertexes = FloatArray(vertexesNum * 3)
        val texCoords = FloatArray(vertexesNum * 2)
        val normals = FloatArray(vertexesNum * 3)
        var vertexIndex = 0
        for (index in 0 until trianglesNum) {
            for (vi in 0 until 3) {
                val srcIndex = objIndices[index * 3 + vi]
                vertexes[vertexIndex * 3] = objVertices[srcIndex * 3]
                texCoords[vertexIndex * 2] = objTexCoords[srcIndex * 2]
                normals[vertexIndex * 3] = objNormals[srcIndex * 3]

                vertexes[vertexIndex * 3 + 1] = objVertices[srcIndex * 3 + 1]
                texCoords[vertexIndex * 2 + 1] = objTexCoords[srcIndex * 2 + 1]
                normals[vertexIndex * 3 + 1] = objNormals[srcIndex * 3 + 1]

                vertexes[vertexIndex * 3 + 2] = objVertices[srcIndex * 3 + 2]
                normals[vertexIndex * 3 + 2] = objNormals[srcIndex * 3 + 2]

                vertexIndex += 1
            }
        }

        assert(vertexIndex == vertexesNum)

        vertexBuffer = directByteBufferOf(capacity = vertexes.size * 4).asFloatBuffer()
        vertexBuffer.put(vertexes).position(0)

        normalsBuffer = directByteBufferOf(capacity = normals.size * 4).asFloatBuffer()
        normalsBuffer.put(normals).position(0)

        texBuffer = directByteBufferOf(capacity = texCoords.size * 4).asFloatBuffer()
        texBuffer.put(texCoords).position(0)
    }

    override fun onSurfaceChanged() {
        // prepare shaders and OpenGL program
        val vertexShader = GlRender.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = GlRender.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        mProgram = GLES20.glCreateProgram() // create empty OpenGL Program
        GLES20.glAttachShader(mProgram, vertexShader) // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader) // add the fragment shader to program
        GLES20.glLinkProgram(mProgram) // create OpenGL program executables
        GlRender.checkGlError("ArLane -> mProgram")
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
            laneLightPosition[0] = light.x.toFloat()
            laneLightPosition[1] = light.y.toFloat()
            laneLightPosition[2] = light.z.toFloat()
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
    fun draw(mvpMatrix: Matrix4, modelMatrix: Matrix4, normMatrix: Matrix3, laneParams: FloatArray) {
        // Add program to OpenGL environment
        GLES20.glUseProgram(mProgram)
        GlRender.checkGlError("ArLane.glUseProgram")

        // get handle to vertex shader's vPosition member
        aPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition")
        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false, 12, vertexBuffer)
        GlRender.checkGlError("ArLane -> aPositionHandle")

        aTexHandle = GLES20.glGetAttribLocation(mProgram, "aTex")
        GLES20.glEnableVertexAttribArray(aTexHandle)
        GLES20.glVertexAttribPointer(aTexHandle, 2, GLES20.GL_FLOAT, false, 8, texBuffer)
        GlRender.checkGlError("ArLane -> aTex")

        aNormalHandle = GLES20.glGetAttribLocation(mProgram, "aNormal")
        GLES20.glEnableVertexAttribArray(aNormalHandle)
        GLES20.glVertexAttribPointer(aNormalHandle, 3, GLES20.GL_FLOAT, false, 12, normalsBuffer)
        GlRender.checkGlError("ArLane -> aNormal")

        // / Uniforms

        // get handle to fragment shader's vColor member
        uColorHandle = GLES20.glGetUniformLocation(mProgram, "uColor")
        GLES20.glUniform4fv(uColorHandle, 1, laneColor, 0)
        GlRender.checkGlError("ArLane -> uColorHandle")

        uAmbientLightColorHandle = GLES20.glGetUniformLocation(mProgram, "uAmbientLightColor")
        GLES20.glUniform3fv(uAmbientLightColorHandle, 1, laneAmbientColor, 0)
        GlRender.checkGlError("ArLane -> uAmbientLightColorHandle")

        uSpecularColorHandle = GLES20.glGetUniformLocation(mProgram, "uSpecularColor")
        GLES20.glUniform4fv(uSpecularColorHandle, 1, laneSpecularColor, 0)
        GlRender.checkGlError("ArLane -> uSpecularColorHandle")

        uLightColorHandle = GLES20.glGetUniformLocation(mProgram, "uLightColor")
        GLES20.glUniform3fv(uLightColorHandle, 1, laneLightColor, 0)
        GlRender.checkGlError("ArLane -> uLightColorHandle")

        uCameraWorldPosHandle = GLES20.glGetUniformLocation(mProgram, "uCameraWorldPos")
        GLES20.glUniform3fv(uCameraWorldPosHandle, 1, cameraPosition, 0)
        GlRender.checkGlError("ArLane -> uCameraWorldPosHandle")

        uLightWorldPosHandle = GLES20.glGetUniformLocation(mProgram, "uLightWorldPos")
        GLES20.glUniform3fv(uLightWorldPosHandle, 1, laneLightPosition, 0)
        GlRender.checkGlError("ArLane -> uLightWorldPosHandle")

        uMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix.toFloatArray(), 0)
        GlRender.checkGlError("ArLane -> uMVPMatrixHandle")

        uModelMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uModelMatrix")
        GLES20.glUniformMatrix4fv(uModelMatrixHandle, 1, false, modelMatrix.toFloatArray(), 0)
        GlRender.checkGlError("ArLane -> uModelMatrixHandle")

        uNormMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uNormMatrixHandle")
        GLES20.glUniformMatrix3fv(uNormMatrixHandle, 1, false, normMatrix.toFloatArray(), 0)
        GlRender.checkGlError("ArLane -> uNormMatrixHandle")

        uLaneParamsHandle = GLES20.glGetUniformLocation(mProgram, "uLaneParams")
        GLES20.glUniform3fv(uLaneParamsHandle, 4, laneParams, 0)
        GlRender.checkGlError("ArLane -> uLaneParamsHandle")

        uLaneWidthHandler = GLES20.glGetUniformLocation(mProgram, "uLaneWidthRatio")
        GLES20.glUniform1f(uLaneWidthHandler, laneWidthRatio)
        GlRender.checkGlError("ArLane -> uLaneWidthHandler")

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, trianglesNum * 3)

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(aPositionHandle)
        GLES20.glDisableVertexAttribArray(aTexHandle)
        GLES20.glDisableVertexAttribArray(aNormalHandle)

        GLES20.glDisable(GLES20.GL_BLEND)
    }
}
