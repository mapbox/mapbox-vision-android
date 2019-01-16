package com.mapbox.vision.utils

import android.content.Context
import android.util.Log
import com.mapbox.vision.BuildConfig
import java.io.File

internal object FileUtils {

    private val TAG = FileUtils::class.java.simpleName

    private const val SOURCE_VIDEO_BUFFERS_DIR_NAME = "Buffers"
    private const val TELEMETRY_DIR_NAME = "Telemetry"

    fun getFilePath(dataDirPath: String, fileName: String): String {
        val baseDir = File(dataDirPath)

        if (!baseDir.exists() && !baseDir.mkdirs()) {
            Log.e(TAG, "Can not create dir in path: ${baseDir.absolutePath}")
            return ""
        }

        return "${baseDir.absolutePath}/$fileName"
    }

    private fun getPath(context: Context, dirName: String): String {
        val dir = if (BuildConfig.DEBUG) {
            File(context.getExternalFilesDir(null), dirName)
        } else {
            File(context.filesDir, dirName)
        }

        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Can not create dir in path: ${dir.absolutePath}")
            return ""
        }

        return dir.absolutePath
    }

    /**
     * Returns publicly accessible dir on [Context.getExternalFilesDir] for debug builds and
     * private [Context.getFilesDir] for release builds.
     */
    fun getTelemetryDirPath(context: Context, telemetryDirName: String = TELEMETRY_DIR_NAME) = getPath(context, telemetryDirName)

    /**
     * Returns publicly accessible dir on [Context.getExternalFilesDir] for debug builds and
     * private [Context.getFilesDir] for release builds.
     */
    fun getDataDirPath(context: Context, buffersDirName: String = SOURCE_VIDEO_BUFFERS_DIR_NAME) = getPath(context, buffersDirName)
}
