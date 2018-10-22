package com.mapbox.vision.utils.drawer.distance

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import com.mapbox.vision.models.DistanceToCar
import kotlin.math.roundToInt

class DistanceDrawerImpl : DistanceDrawer {

    private val textBgColor = Color.argb(127, 0, 0, 0)
    private val mainColor = Color.argb(255, 144, 255, 22)

    private val textOffset = 30
    private val textBoxMarginY = 60

    private val paint = Paint().also {
        it.isAntiAlias = true
        it.isDither = true
        it.style = Paint.Style.FILL
    }

    private val fontMetrics = Paint.FontMetrics()
    private val textPaint = Paint().also {
        it.textSize = 40f
        it.isFakeBoldText = true
        it.style = Paint.Style.FILL
        it.getFontMetrics(fontMetrics)
        it.color = mainColor
    }

    override fun draw(bitmap: Bitmap, distanceToCar: DistanceToCar) {
        val bitmapCanvas = Canvas(bitmap)

        val startX = (distanceToCar.leftRelPosition[0] * bitmap.width).toFloat()
        val startY = (distanceToCar.leftRelPosition[1] * bitmap.height).toFloat() + 50f

        val stopX = (distanceToCar.rightRelPosition[0] * bitmap.width).toFloat()
        val stopY = (distanceToCar.rightRelPosition[1] * bitmap.height).toFloat() + 50f

        paint.shader = LinearGradient(startX, startY - 50f, startX, stopY, mainColor,
                Color.TRANSPARENT, Shader.TileMode.CLAMP)

        val path = Path()
        path.moveTo(startX, startY)
        path.lineTo(stopX, stopY)
        path.lineTo(stopX - 30f, stopY - 50f)
        path.lineTo(startX + 30f, stopY - 50f)
        path.lineTo(startX, startY)

        bitmapCanvas.drawPath(path, paint)


        val distance = distanceToCar.worldPosition[1].roundToInt().toString() + " M "
        val textSize = textPaint.measureText(distance)
        textPaint.color = textBgColor

        val textBoxStartX = ((bitmap.width * 0.5) - (textSize / 2) - textOffset).toFloat()
        val textBoxStartY = (bitmap.height - textBoxMarginY - (textOffset * 2) - 32f).toFloat()

        val textBoxEndX = textBoxStartX + textSize + textOffset * 2
        val textBoxEndY = (bitmap.height - textBoxMarginY).toFloat()

        bitmapCanvas.drawRect(textBoxStartX, textBoxStartY, textBoxEndX, textBoxEndY, textPaint);


        textPaint.color = mainColor

        val textStartX = ((bitmap.width * 0.5) - (textSize / 2)).toFloat()
        val textStartY = (bitmap.height - textBoxMarginY - textOffset).toFloat()

        bitmapCanvas.drawText(distance, textStartX, textStartY, textPaint);
    }
}
