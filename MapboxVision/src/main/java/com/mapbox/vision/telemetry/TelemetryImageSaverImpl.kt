package com.mapbox.vision.telemetry

import android.graphics.Bitmap
import com.mapbox.vision.mobile.core.telemetry.TelemetryImageSaver
import com.mapbox.vision.utils.threads.WorkThreadHandler
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicReference

internal class TelemetryImageSaverImpl : TelemetryImageSaver {

    private val threadHandler = WorkThreadHandler()

    private val sessionDir: AtomicReference<String> = AtomicReference()

    fun start(dir: String) {
        sessionDir.set(dir)
        threadHandler.start()
    }

    fun stop() {
        threadHandler.stop()
        sessionDir.set("")
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
