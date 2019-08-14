package com.mapbox.vision.utils

import android.content.Context
import com.mapbox.vision.BuildConfig
import java.io.File

internal object FileUtils {

    fun getAbsoluteFile(absoluteDir: String, relativeFile: String): String {
        val baseDir = File(absoluteDir)

        if (!baseDir.exists() && !baseDir.mkdirs()) {
            throw IllegalStateException("Can not create dir in path: ${baseDir.absolutePath}")
        }

        return "${baseDir.absolutePath}/$relativeFile"
    }

    fun getAbsoluteDir(absoluteDir: String): String {
        val dir = File(absoluteDir)
        if (!dir.exists() && !dir.mkdirs()) {
            throw IllegalStateException("Can not create dir at ${dir.path}")
        }

        return dir.absolutePath
    }

    fun getAppRelativeDir(context: Context, relativeDir: String): String {
        val dir = if (BuildConfig.DEBUG) {
            File(context.getExternalFilesDir(null), relativeDir)
        } else {
            File(context.filesDir, relativeDir)
        }

        return "${getAbsoluteDir(dir.absolutePath)}/"
    }

    fun moveFiles(fromDirPath: String, toDirPath: String) {
        val fileFrom = File(fromDirPath)
        val fileTo = File(toDirPath)
        fileFrom.renameTo(fileTo)
    }

    fun deleteDir(path: String) {
        File(path).deleteRecursively()
    }
}
