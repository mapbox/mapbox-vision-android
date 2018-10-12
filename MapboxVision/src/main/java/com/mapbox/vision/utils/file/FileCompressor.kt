package com.mapbox.vision.utils.file

import java.io.File

internal interface FileCompressor {

    fun compress(files: Array<File>, outFilePath: String)
}
