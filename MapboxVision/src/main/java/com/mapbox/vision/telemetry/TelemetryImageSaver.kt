package com.mapbox.vision.telemetry

import android.graphics.Bitmap
import com.mapbox.vision.core.events.ImageSaver
import com.mapbox.vision.utils.threads.WorkThreadHandler
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class TelemetryImageSaver : ImageSaver {

    private val threadHandler = WorkThreadHandler().apply { start() }

    private val sessionDir: AtomicReference<String> = AtomicReference()

    fun setSessionDir(dir: String) {
        sessionDir.set(dir)
    }

    override fun saveImage(rgbaBytes: ByteArray, width: Int, height: Int, fileName: String) {
        threadHandler.post {
            File(sessionDir.get(), "$fileName.png").also { it.createNewFile() }
                    .outputStream()
                    .use { outputStream ->
                        Bitmap
                                .createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                .apply {
                                    copyPixelsFromBuffer(ByteBuffer.wrap(rgbaBytes))
                                }
                                .compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    }
        }
    }
}
