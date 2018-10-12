package com.mapbox.vision.utils.file

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal class ZipFileCompressorImpl : FileCompressor {

    override fun compress(files: Array<File>, outFilePath: String) {
        try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outFilePath))).use { output ->
                for (file in files) {
                    if (file.absolutePath == outFilePath) {
                        continue
                    }
                    file.inputStream().buffered(BUFFER_SIZE).use { source ->
                        output.putNextEntry(ZipEntry(file.name))
                        source.copyTo(output, BUFFER_SIZE)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        const val TAG = "ZipFileCompressorImp"

        private const val BUFFER_SIZE = 1024
    }
}
