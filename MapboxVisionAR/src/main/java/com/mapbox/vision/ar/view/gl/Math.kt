package com.mapbox.vision.ar.view.gl

import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class Rotation(
    var pitch: Float,
    var roll: Float,
    var yaw: Float
)

internal class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
)

internal class Matrix3 {
    private val data: FloatArray

    constructor() {
        data = FloatArray(9)
    }

    constructor(_data: FloatArray) {
        assert(_data.size == 9)
        this.data = _data
    }

    fun transposed(): Matrix3 {
        val m = this
        val kTranspose = Matrix3()
        for (iRow in 0..2) {
            for (iCol in 0..2)
                kTranspose[iRow, iCol] = m[iCol, iRow]
        }
        return kTranspose
    }

    fun inverted(): Matrix3 {
        val rkInverse = Matrix3()
        val m = this

        rkInverse[0, 0] = m[1, 1] * m[2, 2] - m[1, 2] * m[2, 1]
        rkInverse[0, 1] = m[0, 2] * m[2, 1] - m[0, 1] * m[2, 2]
        rkInverse[0, 2] = m[0, 1] * m[1, 2] - m[0, 2] * m[1, 1]
        rkInverse[1, 0] = m[1, 2] * m[2, 0] - m[1, 0] * m[2, 2]
        rkInverse[1, 1] = m[0, 0] * m[2, 2] - m[0, 2] * m[2, 0]
        rkInverse[1, 2] = m[0, 2] * m[1, 0] - m[0, 0] * m[1, 2]
        rkInverse[2, 0] = m[1, 0] * m[2, 1] - m[1, 1] * m[2, 0]
        rkInverse[2, 1] = m[0, 1] * m[2, 0] - m[0, 0] * m[2, 1]
        rkInverse[2, 2] = m[0, 0] * m[1, 1] - m[0, 1] * m[1, 0]

        val fDet = m[0, 0] * rkInverse[0, 0] + m[0, 1] * rkInverse[1, 0] + m[0, 2] * rkInverse[2, 0]

        if (Math.abs(fDet) <= 1e-06)
            return m

        val fInvDet = 1.0f / 1e-06
        for (iRow in 0 until 3) {
            for (iCol in 0 until 3)
                rkInverse[iRow, iCol] = rkInverse[iRow, iCol] * fInvDet.toFloat()
        }

        return rkInverse
    }

    fun toFloatArray(): FloatArray {
        return data.clone()
    }

    operator fun times(v: Vector3): Vector3 {
        val m = this
        return Vector3(
                m[0, 0] * v.x + m[1, 0] * v.y + m[2, 0] * v.z,
                m[0, 1] * v.x + m[1, 1] * v.y + m[2, 1] * v.z,
                m[0, 2] * v.x + m[1, 2] * v.y + m[2, 2] * v.z)
    }

    operator fun get(row: Int, col: Int): Float {
        assert(col in 0..2)
        assert(row in 0..2)
        return data[col * 3 + row]
    }

    operator fun set(row: Int, col: Int, value: Float) {
        assert(col in 0..2)
        assert(row in 0..2)
        data[col * 3 + row] = value
    }

    operator fun unaryMinus(): Matrix3 {
        val newData = FloatArray(9)
        for (i in 0 until 9) {
            newData[i] = -data[i]
        }
        return Matrix3(newData)
    }
}

internal class Matrix4 {
    private val data: FloatArray

    constructor() {
        data = FloatArray(16)
        val m = this

        m[0, 0] = 1f
        m[1, 1] = 1f
        m[2, 2] = 1f
        m[3, 3] = 1f
    }

    constructor(_data: FloatArray) {
        assert(_data.size == 16)
        this.data = _data
    }

    fun toMatrix3(): Matrix3 {
        val upperLeft = Matrix3()
        val m = this
        upperLeft[0, 0] = m[0, 0]
        upperLeft[0, 1] = m[0, 1]
        upperLeft[0, 2] = m[0, 2]

        upperLeft[1, 0] = m[1, 0]
        upperLeft[1, 1] = m[1, 1]
        upperLeft[1, 2] = m[1, 2]

        upperLeft[2, 0] = m[2, 0]
        upperLeft[2, 1] = m[2, 1]
        upperLeft[2, 2] = m[2, 2]

        return upperLeft.transposed().inverted()
    }

    fun toFloatArray(): FloatArray {
        return data.clone()
    }

    operator fun get(row: Int, col: Int): Float {
        assert(col in 0..3)
        assert(row in 0..3)
        return data[col * 4 + row]
    }

    operator fun set(row: Int, col: Int, value: Float) {
        assert(col in 0..3)
        assert(row in 0..3)
        data[col * 4 + row] = value
    }

    operator fun times(m2: Matrix4): Matrix4 {
        val result = FloatArray(16)
        Matrix.multiplyMM(result, 0, data, 0, m2.data, 0)
        return Matrix4(result)
    }
}

internal fun directByteBufferOf(capacity: Int) = ByteBuffer.allocateDirect(capacity).order(ByteOrder.nativeOrder())
