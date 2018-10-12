package com.mapbox.vision.utils.extensions

import java.nio.ByteBuffer

/**
 * The inverse-logit function
 */
fun Float.expit(): Float {
    return (1.0 / (1.0 + Math.exp((-this).toDouble()))).toFloat()
}

/**
 * Generalization of the logistic function that "squashes" a K-dimensional vector z  of arbitrary real values to a K-dimensional vector sigma (z)
 * of real values in the range (0, 1) that add up to 1
 */
fun FloatArray.softmax() {
    val max = this.max()
            ?: java.lang.Float.NEGATIVE_INFINITY
    var sum = 0.0f
    for (i in this.indices) {
        this[i] = Math.exp((this[i] - max).toDouble()).toFloat()
        sum += this[i]
    }
    for (i in this.indices) {
        this[i] = this[i] / sum
    }
}

/**
 * Preprocess the image data from 0-255 int to normalized float (-1;1)
 */
fun FloatArray.fillRGBAndNormalizeByValue(byteArray: ByteArray) {
    var counter = 0
    var floatArrayIndexes = 0
    for (i in byteArray.indices) {
        if (counter == 3) {
            counter = 0
            continue
        }
        this[floatArrayIndexes] = 2 * byteArray[i] / 255.0f - 1
        counter++
        floatArrayIndexes++
    }
}

/**
 * Preprocess the image data from 0-255 int to normalized float (-1;1)
 */
fun FloatArray.fillRGBAndNormalizeByValue(intValues: IntArray) {
    for (i in intValues.indices) {
        this[i * 3 + 0] = 2 * (intValues[i] shr 16 and 0xFF) / 255.0f - 1
        this[i * 3 + 1] = 2 * (intValues[i] shr 8 and 0xFF) / 255.0f - 1
        this[i * 3 + 2] = 2 * (intValues[i] and 0xFF) / 255.0f - 1
    }
}

/**
 * Preprocess the image data from 0-255 int to normalized float (-1;1)
 */
fun ByteArray.fillRGB(intValues: IntArray) {
    for (i in intValues.indices) {
        this[i * 3 + 0] = (intValues[i] shr 16 and 0xFF).toByte()
        this[i * 3 + 1] = (intValues[i] shr 8 and 0xFF).toByte()
        this[i * 3 + 2] = (intValues[i] and 0xFF).toByte()
    }
}

/**
 * Preprocess the image data from 0-255 int to normalized float (-1;1)
 */
fun FloatArray.fillBGRAndNormalizeByValue(intValues: IntArray) {
    for (i in intValues.indices) {
        this[i * 3 + 2] = 2 * (intValues[i] shr 16 and 0xFF) / 255.0f - 1
        this[i * 3 + 1] = 2 * (intValues[i] shr 8 and 0xFF) / 255.0f - 1
        this[i * 3 + 0] = 2 * (intValues[i] and 0xFF) / 255.0f - 1
    }
}

/**
 * Preprocess the image data from 0-255 int to normalized float (-1;1)
 */
fun ByteBuffer.fillRGBAndNormalizeByValue(intValues: IntArray) {
    for (i in intValues.indices) {
        this.putFloat(2 * (intValues[i] shr 16 and 0xFF) / 255.0f - 1)
        this.putFloat(2 * (intValues[i] shr 8 and 0xFF) / 255.0f - 1)
        this.putFloat(2 * (intValues[i] and 0xFF) / 255.0f - 1)
    }
}

/**
 * Get max indexes and their confidence for 2 parts divided by borderIndex
 */
fun FloatArray.argMaxAndSoftMaxForParts(borderIndex: Int): Array<Pair<Int, Double>> {

    val classesArray = FloatArray(borderIndex)
    val valuesArray = FloatArray(this.size - borderIndex)

    var maxClassCoefficient = Float.NEGATIVE_INFINITY
    var maxClassIndex = 0

    var maxValueCoefficient = Float.NEGATIVE_INFINITY
    var maxValueIndex = 0

    for (i in this.indices) {
        if (i < borderIndex) {
            classesArray[i] = this[i]
            if (this[i] > maxClassCoefficient) {
                maxClassCoefficient = this[i]
                maxClassIndex = i
            }
        } else {
            valuesArray[i - borderIndex] = this[i]
            if (this[i] > maxValueCoefficient) {
                maxValueCoefficient = this[i]
                maxValueIndex = i
            }
        }
    }
    classesArray.softmax()
    valuesArray.softmax()

    return arrayOf(
            Pair(maxClassIndex, classesArray[maxClassIndex].toDouble()),
            Pair(maxValueIndex, valuesArray[maxValueIndex - borderIndex].toDouble()))
}

fun FloatArray.ArgMaxForClasses(numClasses: Int, outputByteArray: ByteArray) {
    var holder = 0f
    var index = 0
    for (i in 0 until this.size step numClasses) {
        for (j in 0 until numClasses) {
            val value = this[i + j]
            if (j == 0 || value > holder) {
                outputByteArray[index] = j.toByte();
                holder = value
            }
        }
        index++;
    }
}
