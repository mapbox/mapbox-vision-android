package com.mapbox.vision.utils.file

import com.mapbox.vision.utils.FileUtils
import java.io.File

internal class RotatedBuffers(
    private val buffersDir: String,
    private val totalBuffersNumber: Int = DEFAULT_BUFFERS_NUMBER
) {
    companion object {
        private const val DEFAULT_BUFFERS_NUMBER = 3
        private const val FIRST_BUFFER_INDEX = 0
    }

    private var bufferIndex = FIRST_BUFFER_INDEX

    fun rotate() {
        bufferIndex++
        if (bufferIndex >= totalBuffersNumber) {
            bufferIndex = FIRST_BUFFER_INDEX
        }

        val bufferFile = File(getBuffer())
        if (bufferFile.exists()) {
            bufferFile.delete()
        }
    }

    fun getBuffer() = FileUtils.getAbsoluteFile(buffersDir, generateBufferName(bufferIndex))

    private fun generateBufferName(index: Int) = "video$index.mp4"
}
