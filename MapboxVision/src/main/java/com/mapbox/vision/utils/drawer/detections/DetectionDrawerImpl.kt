package com.mapbox.vision.utils.drawer.detections

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.mapbox.vision.visionevents.events.detection.Detection

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


    override fun draw(bitmap: Bitmap, detections: List<Detection>) {
        val bitmapCanvas = Canvas(bitmap)

        for (detection in detections) {
            val typeModel = TypeModel.values()[detection.objectType.ordinal]

            val bbox = detection.boundingBox

            rectPaint.color = typeModel.color
            bitmapCanvas.drawRect(bbox, rectPaint)

            textPaint.color = textBgColor
            bitmapCanvas.drawRect((bbox.left  - textMargin).toFloat(), bbox.top - textOffset + fontMetrics.top - textMargin,
                    bbox.left + textPaint.measureText(typeModel.typeName) + textMargin, (bbox.top +textMargin).toFloat(),
                    textPaint)

            textPaint.color = typeModel.color
            bitmapCanvas.drawText(typeModel.typeName, bbox.left.toFloat(), (bbox.top - textOffset).toFloat(), textPaint)

        }
    }



    private enum class TypeModel(val color: Int, val typeName: String) {
        LIGHT(Color.argb(255, 6, 241, 255), "LIGHT"),
        SIGN(Color.argb(255, 255, 204, 22), "SIGN"),
        CAR(Color.argb(255, 144, 255, 22), "CAR"),
        PERSON(Color.argb(255, 239, 6, 255), "PERSON"),

    }

}