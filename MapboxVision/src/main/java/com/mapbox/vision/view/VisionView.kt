package com.mapbox.vision.view

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.widget.ImageView
import com.mapbox.vision.R
import com.mapbox.vision.VisionManager
import com.mapbox.vision.utils.drawer.detections.DetectionDrawerImpl
import com.mapbox.vision.visionevents.events.detection.Detection


class VisionView : ImageView, VisualizationUpdateListener {

    var visualizationMode = VisualizationMode.CLEAR

    private val bufferBitmap by lazy {
        val frameSize = VisionManager.getFrameSize()
        Bitmap.createBitmap(frameSize.width, frameSize.height, Bitmap.Config.ARGB_8888)
    }

    private var detections: List<Detection> = emptyList()
    private val detectionDrawer = DetectionDrawerImpl()

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {

        val typedArray = context.theme.obtainStyledAttributes(attrs, R.styleable.VisionView, 0, 0)
        val ordinal = typedArray.getInt(R.styleable.VisionView_visualization_mode, 0)

        if (ordinal in 0 until VisualizationMode.values().size) {
            visualizationMode = VisualizationMode.values()[ordinal]
        }
        typedArray.recycle()

        VisionManager.setVisualizationUpdateListener(this)
    }

    override fun getCurrentMode(): VisualizationMode {
        return visualizationMode
    }

    override fun getBitmapBuffer(): Bitmap = bufferBitmap

    override fun onDetectionsUpdated(detections: List<Detection>) {
        this.detections = detections
    }

    override fun onByteArrayUpdated() {
        when (visualizationMode) {
            VisualizationMode.CLEAR,
            VisualizationMode.SEGMENTATION -> {
                setImageBitmap(bufferBitmap)
            }
            VisualizationMode.DETECTION -> {
                if (detections.isEmpty()) {
                    setImageBitmap(bufferBitmap)
                    return
                }
                val bitmap = bufferBitmap.copy(Bitmap.Config.ARGB_8888, true)
                detectionDrawer.draw(bitmap, detections)
                setImageBitmap(bitmap)
            }
        }
    }
}
