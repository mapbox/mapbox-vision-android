package com.mapbox.vision.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mapbox.vision.utils.extensions.fillRGB
import java.io.IOException
import java.io.InputStream

internal object TestUtils {

    fun getBitmapFromAsset(context: Context, filePath: String): Bitmap? {
        val assetManager = context.assets

        val istr: InputStream
        var bitmap: Bitmap? = null
        try {
            istr = assetManager.open(filePath)
            bitmap = BitmapFactory.decodeStream(istr)
        } catch (e: IOException) {
            // handle exception
        }

        return bitmap
    }

    fun getTestRGBData(context: Context, filePath: String, width:Int, heght:Int): ByteArray {

        val bitmap = getBitmapFromAsset(context, filePath) ?: return ByteArray(0)
        val intValues = IntArray(width * heght)

        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val byteData = ByteArray(width * heght * 3)
        byteData.fillRGB(intValues)

        return byteData

    }
}
