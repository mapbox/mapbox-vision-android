package com.mapbox.vision.corewrapper.update.jni

import android.app.Application
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Type
import android.support.annotation.WorkerThread
import android.util.Log
import com.mapbox.vision.VideoStreamListener
import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.buffers.DetectionDataBuffer
import com.mapbox.vision.core.buffers.PositionDataBuffer
import com.mapbox.vision.core.buffers.RoadDescriptionDataBuffer
import com.mapbox.vision.core.buffers.SegmentationDataBuffer
import com.mapbox.vision.core.buffers.SignClassificationDataBuffer
import com.mapbox.vision.core.buffers.WorldDescriptionDataBuffer
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.utils.threads.MainThreadHandler
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.view.VisualizationMode
import com.mapbox.vision.view.VisualizationUpdateListener
import com.mapbox.vision.visionevents.events.Image
import com.mapbox.vision.visionevents.events.classification.SignClassification
import com.mapbox.vision.visionevents.events.detection.Detections
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.segmentation.SegmentationMask
import com.mapbox.vision.visionevents.events.worlddescription.WorldDescription
import java.lang.ref.WeakReference

internal class JNICoreUpdateManager(
        private val coreWrapper: CoreWrapper,
        private val application: Application,
        private val imageWidth: Int,
        private val imageHeight: Int
) {

    private val mainThreadHandler = MainThreadHandler().also { it.start() }
    private val visualizationUpdateThreadHandler = WorkThreadHandler("VisualizationUpdateThread").also { it.start() }

    // Detection Event
    private var detectionDataBuffer: DetectionDataBuffer? = null
    private var lastKnownDetectionId = 0L

    // Segmentation Event
    private var segmentationDataBuffer: SegmentationDataBuffer? = null
    private var lastKnownSegmentationMaskId = 0L

    // Sign Classification Event
    private var signClassificationDataBuffer: SignClassificationDataBuffer? = null
    private var lastKnownClassificationId = 0L

    // Road Description Event
    private var roadDescriptionDataBuffer: RoadDescriptionDataBuffer? = null
    private var lastKnownRoadDescriptionId = 0L

    // World Description Event
    private var worldDescriptionDataBuffer: WorldDescriptionDataBuffer? = null
    private var lastKnownWorldDescriptionId = 0L

    // Position Event
    private var positionDataBuffer: PositionDataBuffer? = null
    private var lastKnownPositionId = 0L

    private var visionEventsListener: VisionEventsListener? = null
    private var visualizationUpdateListener: WeakReference<VisualizationUpdateListener>? = null
    private var videoStreamListener: WeakReference<VideoStreamListener>? = null

    private val sourceImageOutputAllocation by lazy {
        val renderScript = RenderScript.create(application)
        val rgbTypeBuilder = Type.Builder(renderScript, Element.RGBA_8888(renderScript))
        rgbTypeBuilder.setX(imageWidth)
        rgbTypeBuilder.setY(imageHeight)
        Allocation.createTyped(renderScript, rgbTypeBuilder.create(),
                Allocation.USAGE_SHARED)
    }

    fun setRGBABytes(rgbaByteArray: ByteArray, width: Int, height: Int) {
        coreWrapper.setImageByteData(rgbaByteArray, width, height)

        val visualizationListener = visualizationUpdateListener?.get()

        if (visualizationListener?.getCurrentMode() == VisualizationMode.CLEAR) {
            visualizationUpdateThreadHandler.post {
                sourceImageOutputAllocation.copyFrom(rgbaByteArray)
                sourceImageOutputAllocation.copyTo(visualizationListener.getBitmapBuffer())
                mainThreadHandler.post { visualizationListener.onByteArrayUpdated() }
            }
        }

        val localArVideoSourceListener = videoStreamListener?.get() ?: return
        mainThreadHandler.post {
            localArVideoSourceListener.onNewFrame(rgbaByteArray)
        }
    }

    fun setVisionEventListener(visionEventsListener: VisionEventsListener?) {
        this.visionEventsListener = visionEventsListener
    }

    fun setVisualizationUpdateListener(visualizationUpdateListener: WeakReference<VisualizationUpdateListener>?) {
        this.visualizationUpdateListener = visualizationUpdateListener
    }

    fun setVideoSourceListener(videoStreamListener: WeakReference<VideoStreamListener>?) {
        this.videoStreamListener = videoStreamListener
    }

    @WorkerThread
    fun requestUpdate() {
        coreWrapper.requestUpdate()

        if (visualizationUpdateListener?.get() == null) {
            return
        }

        updateDetections()
        updateSegmentation()
        updateSignClassification()
        updateRoadDescription()
        updateWorldDescription()
        updatePosition()
    }

    fun getCurrentRoadDescription(): RoadDescription {
        if (roadDescriptionDataBuffer == null) {
            initRoadDescriptionBuffer()
        }
        coreWrapper.requestRoadDescription()
        return RoadDescription.fromRoadDescriptionBuffer(roadDescriptionDataBuffer!!)
    }

    fun getCurrentWorldDescription(): WorldDescription {
        if (worldDescriptionDataBuffer == null) {
            initWorldDescriptionBuffer()
        }
        coreWrapper.requestWorldDescription()
        return WorldDescription.fromWorldDescriptionDataBuffer(worldDescriptionDataBuffer!!)
    }

    fun getCurrentPosition(): Position {
        if (positionDataBuffer == null) {
            initPositionBuffer()
        }
        coreWrapper.requestPosition()
        return Position.fromPositionBuffer(positionDataBuffer!!)
    }

    fun onPause() {
        visualizationUpdateThreadHandler.stop()
        mainThreadHandler.stop()
    }

    fun onResume() {
        mainThreadHandler.start()
        visualizationUpdateThreadHandler.start()
    }

    fun release() {
        releaseAllBuffers()
        visualizationUpdateThreadHandler.stop()
        mainThreadHandler.stop()
    }

    // Private methods

    // Detections
    private fun initDetectionBuffer() {
        detectionDataBuffer = DetectionDataBuffer()
        coreWrapper.setDetectionDataBuffer(detectionDataBuffer!!)
    }

    private fun removeDetectionBuffer() {
        coreWrapper.removeDetectionDataBuffer()
        detectionDataBuffer = null
    }

    private fun updateDetections() {
        if (detectionDataBuffer == null) {
            initDetectionBuffer()
        }
        val localDetectionDataBuffer = detectionDataBuffer ?: return
        if (lastKnownDetectionId == localDetectionDataBuffer.detectionsIdentifier) {
            return
        }

        val detections = Detections.fromDetectionDataBuffer(localDetectionDataBuffer)

        if (visionEventsListener != null) {
            val imageSource = object : Image.ImageSource {

                override fun getImageBytes(): ByteArray? {
                    val byteArray = coreWrapper.getDetectionsSourceImageDataArray(detections.sourceImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        return byteArray
                    }
                }

                override fun getImageBitmap(): Bitmap? {

                    val byteArray = coreWrapper.getDetectionsSourceImageDataArray(detections.sourceImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        val bitmap = Bitmap.createBitmap(detections.sourceImage.width,
                                detections.sourceImage.height, Bitmap.Config.ARGB_8888)

                        sourceImageOutputAllocation.copyFrom(byteArray)
                        sourceImageOutputAllocation.copyTo(bitmap)
                        return bitmap
                    }
                }
            }

            detections.sourceImage.setImageSource(imageSource)

            mainThreadHandler.post {
                visionEventsListener?.detectionsUpdated(detections)
            }
        }

        val visualizationListener = visualizationUpdateListener?.get()

        if (visualizationListener?.getCurrentMode() == VisualizationMode.DETECTION) {
            visualizationUpdateThreadHandler.post {
                visualizationListener.onDetectionsUpdated(detections.detections)
                sourceImageOutputAllocation.copyFrom(coreWrapper.getDetectionsSourceImageDataArray(detections.sourceImage.identifier))
                sourceImageOutputAllocation.copyTo(visualizationListener.getBitmapBuffer())
                mainThreadHandler.post {
                    visualizationListener.onByteArrayUpdated()
                }
            }
        }

        lastKnownDetectionId = localDetectionDataBuffer.detectionsIdentifier
    }
    // End detections

    // Segmentation
    private fun initSegmentationBuffer() {
        segmentationDataBuffer = SegmentationDataBuffer()
        coreWrapper.setSegmentationDataBuffer(segmentationDataBuffer!!)
    }

    private fun removeSegmentationBuffer() {
        coreWrapper.removeSegmentationDataBuffer()
        segmentationDataBuffer = null
    }

    private fun updateSegmentation() {
        if (segmentationDataBuffer == null) {
            initSegmentationBuffer()
        }
        val localSegmentationBuffer = segmentationDataBuffer ?: return
        if (lastKnownSegmentationMaskId == localSegmentationBuffer.maskImageIdentifier) {
            return
        }

        val segmentationMask = SegmentationMask.fromSegmentationDataBuffer(localSegmentationBuffer)

        if (visionEventsListener != null) {
            val sourceImageSource = object : Image.ImageSource {
                override fun getImageBytes(): ByteArray? {
                    val byteArray = coreWrapper.getSegmentationSourceImageDataArray(segmentationMask.sourceImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        return byteArray
                    }
                }

                override fun getImageBitmap(): Bitmap? {

                    val byteArray = coreWrapper.getSegmentationSourceImageDataArray(segmentationMask.sourceImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        val bitmap = Bitmap.createBitmap(segmentationMask.sourceImage.width,
                                segmentationMask.sourceImage.height, Bitmap.Config.ARGB_8888)

                        sourceImageOutputAllocation?.copyFrom(byteArray)
                        sourceImageOutputAllocation?.copyTo(bitmap)
                        return bitmap
                    }
                }
            }
            segmentationMask.sourceImage.setImageSource(sourceImageSource)


            val maskSource = object : Image.ImageSource {
                override fun getImageBytes(): ByteArray? {
                    val byteArray = coreWrapper.getSegmentationMaskImageDataArray(segmentationMask.segmentationMaskImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        return byteArray
                    }
                }

                override fun getImageBitmap(): Bitmap? {

                    Log.e(TAG, "getImageBitmap method is not supported for Mask Image")

                    return Bitmap.createBitmap(segmentationMask.segmentationMaskImage.width,
                            segmentationMask.segmentationMaskImage.height, Bitmap.Config.ARGB_8888)
                }
            }
            segmentationMask.segmentationMaskImage.setImageSource(maskSource)

            mainThreadHandler.post {
                visionEventsListener?.segmentationUpdated(segmentationMask)
            }
        }

        val visualizationListener = visualizationUpdateListener?.get()

        if (visualizationListener?.getCurrentMode() == VisualizationMode.SEGMENTATION) {
            visualizationUpdateThreadHandler.post {
                sourceImageOutputAllocation?.copyFrom(coreWrapper.getBlendedSegmentationMaskImageDataArray(segmentationMask.segmentationMaskImage.identifier))
                sourceImageOutputAllocation?.copyTo(visualizationListener.getBitmapBuffer())
                mainThreadHandler.post {
                    visualizationListener.onByteArrayUpdated()
                }
            }
        }

        lastKnownSegmentationMaskId = localSegmentationBuffer.maskImageIdentifier
    }
    // End segmentation

    // Sign Classification
    private fun initSignClassificationBuffer() {
        signClassificationDataBuffer = SignClassificationDataBuffer()
        coreWrapper.setSignClassificationDataBuffer(signClassificationDataBuffer!!)
    }

    private fun removeSignClassificationBuffer() {
        coreWrapper.removeSignClassificationDataBuffer()
        signClassificationDataBuffer = null
    }

    private fun updateSignClassification() {
        if (signClassificationDataBuffer == null) {
            initSignClassificationBuffer()
        }
        val localSignClassificationDataBuffer = signClassificationDataBuffer ?: return
        if (lastKnownClassificationId == localSignClassificationDataBuffer.signClassificationIdentifier) {
            // Data is up to date
            return
        }

        val signClassificationEvent = SignClassification.fromSignClassificationDataBuffer(localSignClassificationDataBuffer)

        if (visionEventsListener != null) {
            val sourceImage = object : Image.ImageSource {
                override fun getImageBytes(): ByteArray? {
                    val byteArray = coreWrapper.getSignClassificationSourceImageDataArray(signClassificationEvent.sourceImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        return byteArray
                    }
                }

                override fun getImageBitmap(): Bitmap? {

                    val byteArray = coreWrapper.getSignClassificationSourceImageDataArray(signClassificationEvent.sourceImage.identifier)
                    if (byteArray.isEmpty()) {
                        return null
                    } else {
                        val bitmap = Bitmap.createBitmap(signClassificationEvent.sourceImage.width,
                                signClassificationEvent.sourceImage.height, Bitmap.Config.ARGB_8888)

                        sourceImageOutputAllocation?.copyFrom(byteArray)
                        sourceImageOutputAllocation?.copyTo(bitmap)
                        return bitmap
                    }

                }
            }

            signClassificationEvent.sourceImage.setImageSource(sourceImage)


            mainThreadHandler.post { visionEventsListener?.signClassificationUpdated(signClassificationEvent) }
        }

        lastKnownClassificationId = localSignClassificationDataBuffer.signClassificationIdentifier
    }

    // End sign classification

    // Road description
    private fun initRoadDescriptionBuffer() {
        roadDescriptionDataBuffer = RoadDescriptionDataBuffer()
        coreWrapper.setRoadDescriptionDataBuffer(roadDescriptionDataBuffer!!)
    }

    private fun removeRoadDescriptionBuffer() {
        coreWrapper.removeRoadDescriptionDataBuffer()
        roadDescriptionDataBuffer = null
    }

    private fun updateRoadDescription() {
        if (roadDescriptionDataBuffer == null) {
            initRoadDescriptionBuffer()
        }
        val localRoadDescriptionBuffer = roadDescriptionDataBuffer ?: return
        if (lastKnownRoadDescriptionId == localRoadDescriptionBuffer.roadDescriptionIdentifier) {
            return
        }

        if (visionEventsListener != null) {
            val roadDescriptionEvent = RoadDescription.fromRoadDescriptionBuffer(localRoadDescriptionBuffer)
            mainThreadHandler.post { visionEventsListener?.roadDescriptionUpdated(roadDescriptionEvent) }
        }

        lastKnownRoadDescriptionId = localRoadDescriptionBuffer.roadDescriptionIdentifier
    }

    // Road description

    // World description
    private fun initWorldDescriptionBuffer() {
        worldDescriptionDataBuffer = WorldDescriptionDataBuffer()
        coreWrapper.setWorldDescriptionDataBuffer(worldDescriptionDataBuffer!!)
    }

    private fun removeWorldDescriptionBuffer() {
        coreWrapper.removeWorldDescriptionDataBuffer()
        worldDescriptionDataBuffer = null
    }

    private fun updateWorldDescription() {
        if (worldDescriptionDataBuffer == null) {
            initWorldDescriptionBuffer()
        }
        val localWorldDescriptionBuffer = worldDescriptionDataBuffer ?: return
        if (lastKnownWorldDescriptionId == localWorldDescriptionBuffer.worldDescriptionIdentifier) {
            return
        }

        if (visionEventsListener != null) {
            val worldDescription = WorldDescription.fromWorldDescriptionDataBuffer(localWorldDescriptionBuffer)
            mainThreadHandler.post { visionEventsListener?.worldDescriptionUpdated(worldDescription) }
        }

        lastKnownWorldDescriptionId = localWorldDescriptionBuffer.worldDescriptionIdentifier
    }
    // End world description

    // Position
    private fun initPositionBuffer() {
        positionDataBuffer = PositionDataBuffer()
        coreWrapper.setPositionDataBuffer(positionDataBuffer!!)
    }

    private fun removePositionBuffer() {
        coreWrapper.removePositionDataBuffer()
        positionDataBuffer = null
    }

    private fun updatePosition() {
        if (positionDataBuffer == null) {
            initPositionBuffer()
        }
        val localPositionBuffer = positionDataBuffer ?: return
        if (lastKnownPositionId == localPositionBuffer.positionIdentifier) {
            return
        }

        if (visionEventsListener != null) {
            val position = Position.fromPositionBuffer(localPositionBuffer)
            mainThreadHandler.post { visionEventsListener?.estimatedPositionUpdated(position) }
        }

        lastKnownPositionId = localPositionBuffer.positionIdentifier
    }
    // End position

    private fun releaseAllBuffers() {
        if (detectionDataBuffer != null) {
            removeDetectionBuffer()
        }
        if (segmentationDataBuffer != null) {
            removeSegmentationBuffer()
        }
        if (signClassificationDataBuffer != null) {
            removeSignClassificationBuffer()
        }
        if (roadDescriptionDataBuffer != null) {
            removeRoadDescriptionBuffer()
        }
        if (worldDescriptionDataBuffer != null) {
            removeWorldDescriptionBuffer()
        }
        if (positionDataBuffer != null) {
            removePositionBuffer()
        }
    }

    companion object {
        private const val TAG = "EventsListenerManager"
    }

}
