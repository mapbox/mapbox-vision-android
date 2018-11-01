package com.mapbox.vision.corewrapper

import com.mapbox.vision.VideoStreamListener
import com.mapbox.vision.corewrapper.update.VisionEventsListener
import com.mapbox.vision.models.CameraParamsData
import com.mapbox.vision.models.DeviceMotionData
import com.mapbox.vision.models.GPSData
import com.mapbox.vision.models.HeadingData
import com.mapbox.vision.models.LaneDepartureState
import com.mapbox.vision.models.route.NavigationRoute
import com.mapbox.vision.performance.ModelPerformanceConfig
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.view.VisualizationUpdateListener
import com.mapbox.vision.visionevents.CalibrationProgress
import com.mapbox.vision.visionevents.ScreenCoordinate
import com.mapbox.vision.visionevents.WorldCoordinate
import com.mapbox.vision.visionevents.events.Image
import com.mapbox.vision.visionevents.events.position.Position
import com.mapbox.vision.visionevents.events.roaddescription.RoadDescription
import com.mapbox.vision.visionevents.events.worlddescription.WorldDescription
import java.lang.ref.WeakReference

/**
 * Main interface for ML Core
 */
internal interface VisionCore {

    val imageWidth: Int
    val imageHeight: Int

    val imageFormat: Image.Format

    fun setVisionEventListener(visionEventsListener: VisionEventsListener?)

    fun setVisualizationUpdateListener(visualizationUpdateListener: WeakReference<VisualizationUpdateListener>?)

    fun setVideoStreamListener(videoStreamListener: WeakReference<VideoStreamListener>?)

    fun setModelPerformanceConfig(modelPerformanceConfig: ModelPerformanceConfig)

    fun setRGBABytes(rgbaByteArray: ByteArray, width: Int, height: Int)

    fun requestUpdate()

    fun setGPSData(gpsData: GPSData)

    fun setDeviceMotionData(deviceMotionData: DeviceMotionData)

    fun setHeadingData(headingData: HeadingData)

    fun setCameraParamsData(cameraParamsData: CameraParamsData)

    fun setRouteData(navigationRoute: NavigationRoute?)

    fun startDataSavingSession(dataDirPath: String)

    fun stopDataSavingSession()

    // Get core data methods
    fun getWorldDescription(): WorldDescription

    fun getRoadDescription(): RoadDescription?

    fun getPosition(): Position

    fun getCalibrationProgress(): CalibrationProgress
    // end get core data methods

    fun getAndResetClipsTimeList(): List<VideoProcessor.VideoPart>

    fun getCoreMilliseconds(): Long

    // AR
    fun getARCameraParams(): FloatArray?

    fun getARRouteData(): DoubleArray?

    fun getLaneDepartureState() : LaneDepartureState

    // Test methods
    fun getFrameStatistics(): FloatArray
    // end

    // Convert points methods
    fun worldToPixel(worldCoordinate: WorldCoordinate): ScreenCoordinate

    fun pixelToWorld(screenCoordinate: ScreenCoordinate): WorldCoordinate
    // End Convert points methods

    // Lifecycle
    fun onPause()

    fun onResume()
    // end lifecycle

    fun release()


}
