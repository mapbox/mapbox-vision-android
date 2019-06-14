package com.mapbox.vision.examples.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.mapbox.vision.ar.R
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.frame.PixelCoordinate

class LanesView
@JvmOverloads
constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var scaleXFactor = 1f
    private var scaleYFactor = 1f

    private val distancePath = Path()
    private val distancePaint = Paint().apply {
        color = resources.getColor(R.color.colorAccent, null)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private var leftLaneStart: PixelCoordinate? = null
    private var leftLaneEnd: PixelCoordinate? = null
    private var rightLaneStart: PixelCoordinate? = null
    private var rightLaneEnd: PixelCoordinate? = null

    private val transparent = resources.getColor(android.R.color.transparent, null)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val frameSize = ImageSize(1280, 720)
        scaleYFactor = height.toFloat() / frameSize.imageHeight
        scaleXFactor = width.toFloat() / frameSize.imageWidth
    }

    fun setPoints(
        leftLaneStart: PixelCoordinate?,
        leftLaneEnd: PixelCoordinate?,
        rightLaneStart: PixelCoordinate?,
        rightLaneEnd: PixelCoordinate?
    ) {
        this.leftLaneStart = leftLaneStart
        this.leftLaneEnd = leftLaneEnd
        this.rightLaneStart = rightLaneStart
        this.rightLaneEnd = rightLaneEnd

        invalidate()
    }

    private fun Float.scaleX(): Float = this * scaleXFactor
    private fun Float.scaleY(): Float = this * scaleYFactor

    private fun Path.to(action: Path.(Float, Float) -> Unit, pixelCoordinate: PixelCoordinate?) {
        if (pixelCoordinate != null) {
            action(pixelCoordinate.x.toFloat().scaleX(), pixelCoordinate.y.toFloat().scaleY())
        }
    }

    private fun Path.moveTo(pixelCoordinate: PixelCoordinate?) {
        to(Path::moveTo, pixelCoordinate)
    }

    private fun Path.lineTo(pixelCoordinate: PixelCoordinate?) {
        to(Path::lineTo, pixelCoordinate)
    }

    override fun onDraw(canvas: Canvas) {
        distancePath.reset()
        setBackgroundColor(transparent)

        distancePath.moveTo(leftLaneStart)
        distancePath.lineTo(leftLaneEnd)
        distancePath.moveTo(rightLaneStart)
        distancePath.lineTo(rightLaneEnd)

        canvas.drawPath(distancePath, distancePaint)

        super.onDraw(canvas)
    }
}
