package com.mapbox.vision.corewrapper.update.jni

import android.app.Application
import android.graphics.Bitmap
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.Type
import android.support.annotation.WorkerThread
import com.mapbox.vision.VideoStreamListener
import com.mapbox.vision.core.CoreWrapper
import com.mapbox.vision.core.buffers.*
import com.mapbox.vision.corewrapper.update.RoadRestrictionsListener
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.utils.threads.MainThreadHandler
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.view.VisualizationMode
import com.mapbox.vision.view.VisualizationUpdateListener
import com.mapbox.vision.visionevents.CalibrationProgress
import com.mapbox.vision.visionevents.LaneDepartureState
import com.mapbox.vision.visionevents.events.Image
import com.mapbox.vision.visionevents.events.classification.SignClassification
import com.mapbox.vision.visionevents.events.detection.Detections
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.roadrestrictions.SpeedLimit
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

    private var detectionDataBuffer: DetectionDataBuffer? = null
    private var lastKnownDetectionId = 0L

    private var segmentationDataBuffer: SegmentationDataBuffer? = null
    private var lastKnownSegmentationMaskId = 0L

    private var signClassificationDataBuffer: SignClassificationDataBuffer? = null
    private var lastKnownClassificationId = 0L

    private var roadDescriptionDataBuffer: RoadDescriptionDataBuffer? = null
    private var lastKnownRoadDescriptionId = 0L

    private var worldDescriptionDataBuffer: WorldDescriptionDataBuffer? = null
    private var lastKnownWorldDescriptionId = 0L

    private var positionDataBuffer: PositionDataBuffer? = null
    private var lastKnownPositionId = 0L

    private var calibrationDataBuffer: CalibrationDataBuffer? = null
    private var lastKnownCalibrationId = 0L

    private var roadRestrictionsDataBuffer: RoadRestrictionsDataBuffer? = null
    private var lastKnownRoadRestrictionsId = 0L

    private var lastKnownLaneDepartureState = LaneDepartureState.Normal

    private var visionEventsListener: WeakReference<VisionEventsListener>? = null
    private var roadRestrictionsListener: WeakReference<RoadRestrictionsListener>? = null
    private var visualizationUpdateListener: WeakReference<VisualizationUpdateListener>? = null
    private var videoStreamListener: WeakReference<VideoStreamListener>? = null

    private val sourceImageOutputAllocation by lazy {
        val renderScript = RenderScript.create(application)
        val rgbTypeBuilder = Type.Builder(renderScript, Element.RGBA_8888(renderScript))
        rgbTypeBuilder.setX(imageWidth)
        rgbTypeBuilder.setY(imageHeight)
        Allocation.createTyped(renderScript, rgbTypeBuilder.create(), Allocation.USAGE_SHARED)
    }

    private fun Allocation.visualize(from: ByteArray, listener: VisualizationUpdateListener) {
        copyFrom(from)
        copyTo(listener.getBitmapBuffer())
        mainThreadHandler.post { listener.onByteArrayUpdated() }
    }

    fun setRGBABytes(rgbaByteArray: ByteArray, width: Int, height: Int) {
        coreWrapper.setImageByteData(rgbaByteArray, width, height)

        val visualizationListener = visualizationUpdateListener?.get()
        if (visualizationListener?.getCurrentMode() == VisualizationMode.CLEAR) {
            visualizationUpdateThreadHandler.post {
                sourceImageOutputAllocation.visualize(rgbaByteArray, visualizationListener)
            }
        }

        val localArVideoSourceListener = videoStreamListener?.get() ?: return
        mainThreadHandler.post {
            localArVideoSourceListener.onNewFrame(rgbaByteArray)
        }
    }

    fun setVisionEventListener(visionEventsListener: WeakReference<VisionEventsListener>?) {
        this.visionEventsListener = visionEventsListener
    }

    fun setVisualizationUpdateListener(visualizationUpdateListener: WeakReference<VisualizationUpdateListener>?) {
        this.visualizationUpdateListener = visualizationUpdateListener
    }

    fun setVideoSourceListener(videoStreamListener: WeakReference<VideoStreamListener>?) {
        this.videoStreamListener = videoStreamListener
    }

    fun setRoadRestrictionsListener(roadRestrictionsListener: WeakReference<RoadRestrictionsListener>?) {
        this.roadRestrictionsListener = roadRestrictionsListener
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
        updateCalibrationProgress()
        updateLaneDepartureState()
        updateRoadRestrictions()
    }

    fun getCurrentRoadDescription(): RoadDescription? {
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
        return Position(positionDataBuffer!!)
    }

    fun getCalibrationProgress(): CalibrationProgress {
        if (calibrationDataBuffer == null) {
            initCalibrationBuffer()
        }
        coreWrapper.requestCalibration()
        return CalibrationProgress(calibrationDataBuffer!!)
    }

    fun getLaneDepartureState(): LaneDepartureState {
        return LaneDepartureState.values().getOrElse(coreWrapper.getLaneDepartureState()) {
            LaneDepartureState.Normal
        }
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

    private fun initDetectionBuffer() {
        detectionDataBuffer = DetectionDataBuffer()
        coreWrapper.setDetectionDataBuffer(detectionDataBuffer!!)
    }

    private val detectionsSource = object : Image.ImageSource {

        override fun getImageBytes(sourceImage: Image) = coreWrapper
                .getDetectionsSourceImageDataArray(sourceImage.identifier)
                .nullIfEmpty()

        override fun getImageBitmap(sourceImage: Image) = coreWrapper
                .getDetectionsSourceImageDataArray(sourceImage.identifier)
                .toBitmap(sourceImage)
    }

    private val signClassificationSource = object : Image.ImageSource {

        override fun getImageBytes(sourceImage: Image) = coreWrapper
                .getSignClassificationSourceImageDataArray(sourceImage.identifier)
                .nullIfEmpty()

        override fun getImageBitmap(sourceImage: Image) = coreWrapper
                .getSignClassificationSourceImageDataArray(sourceImage.identifier)
                .toBitmap(sourceImage)
    }

    private val segmentationSource = object : Image.ImageSource {

        override fun getImageBytes(sourceImage: Image) = coreWrapper
                .getSegmentationSourceImageDataArray(sourceImage.identifier)
                .nullIfEmpty()

        override fun getImageBitmap(sourceImage: Image) = coreWrapper
                .getSegmentationSourceImageDataArray(sourceImage.identifier)
                .toBitmap(sourceImage)
    }

    private val segmentationMaskSource = object : Image.ImageSource {

        override fun getImageBytes(sourceImage: Image) = coreWrapper
                .getSegmentationMaskImageDataArray(sourceImage.identifier)
                .nullIfEmpty()

        override fun getImageBitmap(sourceImage: Image) = null
    }

    private fun updateDetections() {
        if (detectionDataBuffer == null) {
            initDetectionBuffer()
        }
        val localDetectionDataBuffer = detectionDataBuffer ?: return
        if (lastKnownDetectionId == localDetectionDataBuffer.detectionsIdentifier) {
            return
        }

        val detections = Detections(localDetectionDataBuffer)

        detections.sourceImage.imageSource = detectionsSource

        val visionEventsListenerRef = visionEventsListener?.get()
        if (visionEventsListenerRef != null) {
            mainThreadHandler.post {
                visionEventsListenerRef.detectionsUpdated(detections)
            }
        }

        val visualizationListener = visualizationUpdateListener?.get()

        if (visualizationListener?.getCurrentMode() == VisualizationMode.DETECTION) {
            visualizationUpdateThreadHandler.post {
                visualizationListener.onDetectionsUpdated(detections.detections)
                val array = coreWrapper.getDetectionsSourceImageDataArray(detections.sourceImage.identifier)
                if (!array.isEmpty()) {
                    sourceImageOutputAllocation.visualize(array, visualizationListener)
                }
            }
        }

        lastKnownDetectionId = localDetectionDataBuffer.detectionsIdentifier
    }

    private fun initSegmentationBuffer() {
        segmentationDataBuffer = SegmentationDataBuffer()
        coreWrapper.setSegmentationDataBuffer(segmentationDataBuffer!!)
    }

    private fun updateSegmentation() {
        if (segmentationDataBuffer == null) {
            initSegmentationBuffer()
        }
        val localSegmentationBuffer = segmentationDataBuffer ?: return
        if (lastKnownSegmentationMaskId == localSegmentationBuffer.maskImageIdentifier) {
            return
        }

        val segmentationMask = SegmentationMask(localSegmentationBuffer)
        segmentationMask.sourceImage.imageSource = segmentationSource
        segmentationMask.segmentationMaskImage.imageSource = segmentationMaskSource

        val visionEventsListenerRef = visionEventsListener?.get()
        if (visionEventsListenerRef != null) {
            mainThreadHandler.post {
                visionEventsListenerRef.segmentationUpdated(segmentationMask)
            }
        }

        val visualizationListener = visualizationUpdateListener?.get()

        if (visualizationListener?.getCurrentMode() == VisualizationMode.SEGMENTATION) {
            visualizationUpdateThreadHandler.post {
                sourceImageOutputAllocation.visualize(
                        from = coreWrapper.getBlendedSegmentationMaskImageDataArray(segmentationMask.segmentationMaskImage.identifier),
                        listener = visualizationListener
                )
            }
        }

        lastKnownSegmentationMaskId = localSegmentationBuffer.maskImageIdentifier
    }

    private fun initSignClassificationBuffer() {
        signClassificationDataBuffer = SignClassificationDataBuffer()
        coreWrapper.setSignClassificationDataBuffer(signClassificationDataBuffer!!)
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

        val signClassificationEvent = SignClassification(localSignClassificationDataBuffer)

        val visionEventsListenerRef = visionEventsListener?.get() ?: return
        signClassificationEvent.sourceImage.imageSource = signClassificationSource

        mainThreadHandler.post {
            visionEventsListenerRef.signClassificationUpdated(signClassificationEvent)
        }

        lastKnownClassificationId = localSignClassificationDataBuffer.signClassificationIdentifier
    }

    private fun initRoadDescriptionBuffer() {
        roadDescriptionDataBuffer = RoadDescriptionDataBuffer()
        coreWrapper.setRoadDescriptionDataBuffer(roadDescriptionDataBuffer!!)
    }

    private fun updateRoadDescription() {
        if (roadDescriptionDataBuffer == null) {
            initRoadDescriptionBuffer()
        }
        val localRoadDescriptionBuffer = roadDescriptionDataBuffer ?: return
        if (lastKnownRoadDescriptionId == localRoadDescriptionBuffer.roadDescriptionIdentifier) {
            return
        }

        val visionEventsListenerRef = visionEventsListener?.get() ?: return
        val roadDescriptionEvent = RoadDescription.fromRoadDescriptionBuffer(localRoadDescriptionBuffer) ?: return
        mainThreadHandler.post {
            visionEventsListenerRef.roadDescriptionUpdated(roadDescriptionEvent)
        }

        lastKnownRoadDescriptionId = localRoadDescriptionBuffer.roadDescriptionIdentifier
    }

    private fun initWorldDescriptionBuffer() {
        worldDescriptionDataBuffer = WorldDescriptionDataBuffer()
        coreWrapper.setWorldDescriptionDataBuffer(worldDescriptionDataBuffer!!)
    }

    private fun updateWorldDescription() {
        if (worldDescriptionDataBuffer == null) {
            initWorldDescriptionBuffer()
        }
        val localWorldDescriptionBuffer = worldDescriptionDataBuffer ?: return
        if (lastKnownWorldDescriptionId == localWorldDescriptionBuffer.worldDescriptionIdentifier) {
            return
        }

        val visionEventsListenerRef = visionEventsListener?.get() ?: return
        val worldDescription = WorldDescription.fromWorldDescriptionDataBuffer(localWorldDescriptionBuffer)
        mainThreadHandler.post {
            visionEventsListenerRef.worldDescriptionUpdated(worldDescription)
        }

        lastKnownWorldDescriptionId = localWorldDescriptionBuffer.worldDescriptionIdentifier
    }

    private fun initPositionBuffer() {
        positionDataBuffer = PositionDataBuffer()
        coreWrapper.setPositionDataBuffer(positionDataBuffer!!)
    }

    private fun updatePosition() {
        if (positionDataBuffer == null) {
            initPositionBuffer()
        }
        val localPositionBuffer = positionDataBuffer ?: return
        if (lastKnownPositionId == localPositionBuffer.positionIdentifier) {
            return
        }

        val visionEventsListenerRef = visionEventsListener?.get() ?: return
        mainThreadHandler.post {
            visionEventsListenerRef.estimatedPositionUpdated(Position(localPositionBuffer))
        }

        lastKnownPositionId = localPositionBuffer.positionIdentifier
    }

    private fun initCalibrationBuffer() {
        calibrationDataBuffer = CalibrationDataBuffer()
        coreWrapper.setCalibrationDataBuffer(calibrationDataBuffer!!)
    }

    private fun updateCalibrationProgress() {
        if (calibrationDataBuffer == null) {
            initCalibrationBuffer()
        }
        val localCalibrationBuffer = calibrationDataBuffer ?: return
        if (lastKnownCalibrationId == localCalibrationBuffer.identifier) {
            return
        }

        val visionEventsListenerRef = visionEventsListener?.get() ?: return
        val calibrationProgress = CalibrationProgress(localCalibrationBuffer)
        mainThreadHandler.post {
            visionEventsListenerRef.calibrationProgressUpdated(calibrationProgress)
        }

        lastKnownCalibrationId = localCalibrationBuffer.identifier
    }

    private fun updateLaneDepartureState() {
        val laneDepartureState = getLaneDepartureState()
        if (lastKnownLaneDepartureState == laneDepartureState) {
            return
        }

        val visionEventsListenerRef = visionEventsListener?.get() ?: return
        mainThreadHandler.post {
            visionEventsListenerRef.laneDepartureStateUpdated(laneDepartureState)
        }
    }

    private fun initRoadRestrictionsDataBuffer() {
        roadRestrictionsDataBuffer = RoadRestrictionsDataBuffer()
        coreWrapper.setRoadRestrictionsDataBuffer(roadRestrictionsDataBuffer!!)
    }

    private fun getSpeedLimit(): SpeedLimit {
        if (roadRestrictionsDataBuffer == null) {
            initRoadRestrictionsDataBuffer()
        }
        coreWrapper.requestCalibration()
        return SpeedLimit(roadRestrictionsDataBuffer!!)
    }

    private fun updateRoadRestrictions() {
        if (calibrationDataBuffer == null) {
            initCalibrationBuffer()
        }

        val roadRestrictionsListenerRef = roadRestrictionsListener?.get() ?: return

        val speedLimit = getSpeedLimit()
        if (lastKnownRoadRestrictionsId == speedLimit.identifier) {
            return
        }

        mainThreadHandler.post {
            roadRestrictionsListenerRef.speedLimitUpdated(speedLimit)
        }

        lastKnownRoadRestrictionsId = speedLimit.identifier
    }

    private fun ByteArray.nullIfEmpty() = if (isEmpty()) null else this

    private fun ByteArray.toBitmap(sourceImage: Image): Bitmap? = if (isEmpty()) {
        null
    } else {
        val bitmap = Bitmap.createBitmap(sourceImage.width, sourceImage.height, Bitmap.Config.ARGB_8888)

        sourceImageOutputAllocation.copyFrom(this)
        sourceImageOutputAllocation.copyTo(bitmap)
        bitmap
    }

    private fun releaseAllBuffers() {
        if (detectionDataBuffer != null) {
            coreWrapper.removeDetectionDataBuffer()
            detectionDataBuffer = null
        }
        if (segmentationDataBuffer != null) {
            coreWrapper.removeSegmentationDataBuffer()
            segmentationDataBuffer = null
        }
        if (signClassificationDataBuffer != null) {
            coreWrapper.removeSignClassificationDataBuffer()
            signClassificationDataBuffer = null
        }
        if (roadDescriptionDataBuffer != null) {
            coreWrapper.removeRoadDescriptionDataBuffer()
            roadDescriptionDataBuffer = null
        }
        if (worldDescriptionDataBuffer != null) {
            coreWrapper.removeWorldDescriptionDataBuffer()
            worldDescriptionDataBuffer = null
        }
        if (positionDataBuffer != null) {
            coreWrapper.removePositionDataBuffer()
            positionDataBuffer = null
        }
        if (calibrationDataBuffer != null) {
            coreWrapper.removeCalibrationDataBuffer()
            calibrationDataBuffer = null
        }
        if (roadRestrictionsDataBuffer != null) {
            coreWrapper.removeRoadRestrictionsDataBuffer()
            roadRestrictionsDataBuffer = null
        }
    }
}
