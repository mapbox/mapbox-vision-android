package com.mapbox.vision.ar.view.gl

import de.javagl.obj.ObjData
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjUtils
import java.io.InputStream
import java.nio.FloatBuffer

class ObjectWrapper(stream: InputStream) {

    val trianglesNum: Int

    val vertexBuffer: FloatBuffer
    val normalsBuffer: FloatBuffer
    val texBuffer: FloatBuffer

    init {
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
}