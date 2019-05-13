package com.mapbox.vision.utils.drawer.detections

import android.graphics.*
import com.mapbox.vision.mobile.core.models.detection.Detection

class DetectionDrawerImpl : DetectionDrawer {

    private val rectPaint = Paint().also {
        it.strokeWidth = 4f
        it.isAntiAlias = true
        it.isDither = true
        it.style = Paint.Style.STROKE
    }

    private val fontMetrics = Paint.FontMetrics()
    private val textPaint = Paint().also {
        it.textSize = 22f
        it.isFakeBoldText = true
        it.style = Paint.Style.FILL
        it.getFontMetrics(fontMetrics)
    }

    private val textMargin = 5
    private val textOffset = 10
    private val textBgColor = Color.argb(127, 0, 0, 0)

    override fun draw(bitmap: Bitmap, detections: Array<Detection>) {
        val bitmapCanvas = Canvas(bitmap)
        val bitmapWidth = bitmap.width
        val bitmapHeight = bitmap.height

        for (detection in detections) {
            val typeModel = TypeModel.values()[detection.detectionClass.ordinal]

            val bbox = detection.boundingBox.absoluteCoordinate(bitmapWidth, bitmapHeight)

            rectPaint.color = typeModel.color
            bitmapCanvas.drawRect(bbox, rectPaint)

            textPaint.color = textBgColor
            bitmapCanvas.drawRect(
                (bbox.left - textMargin), bbox.top - textOffset + fontMetrics.top - textMargin,
                bbox.left + textPaint.measureText(typeModel.typeName) + textMargin, (bbox.top + textMargin),
                textPaint
            )

            textPaint.color = typeModel.color
            bitmapCanvas.drawText(typeModel.typeName, bbox.left, (bbox.top - textOffset), textPaint)

        }
    }

    private fun RectF.absoluteCoordinate(width: Int, height: Int): RectF =
        RectF(
            left * width,
            top * height,
            right * width,
            bottom * height
        )

    private enum class TypeModel(val color: Int, val typeName: String) {
        Car(
            Color.argb(255, 144, 255, 22),
            "CAR"
        ),
        Bicycle(
            Color.argb(255, 144, 128, 22),
            "BICYCLE"
        ),
        Person(
            Color.argb(255, 239, 6, 255),
            "PERSON"
        ),
        Light(
            Color.argb(255, 6, 241, 255),
            "LIGHT"
        ),
        Sign(
            Color.argb(255, 255, 204, 22),
            "SIGN"
        )
    }
}
