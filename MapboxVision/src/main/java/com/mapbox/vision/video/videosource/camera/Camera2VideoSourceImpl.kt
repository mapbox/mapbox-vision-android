package com.mapbox.vision.video.videosource.camera

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.RenderScript
import android.util.Size
import android.view.Surface
import com.mapbox.vision.models.CameraParams
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.lang.IllegalStateException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal class Camera2VideoSourceImpl(
        private val application: Application,
        private val desiredWidth: Int = DEFAULT_FRAME_WIDTH,
        private val desiredHeight: Int = DEFAULT_FRAME_HEIGHT
) : VideoSource {

    private val cameraId: String

    private var videoSourceListener: VideoSourceListener? = null

    private lateinit var recordingSurface: Surface
    private lateinit var yuvAllocation2Rgb: YuvAllocation2Rgb
    private lateinit var previewSize: Size
    private lateinit var cameraParams: CameraParams

    private var captureSession: CameraCaptureSession? = null

    // Prevents app from exiting before closing the camera.
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null

    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            createCaptureSession()
            cameraOpenCloseLock.release()
        }

        override fun onDisconnected(currentCameraDevice: CameraDevice) {
            currentCameraDevice.close()
            cameraDevice = null
            cameraOpenCloseLock.release()
        }

        override fun onError(currentCameraDevice: CameraDevice, error: Int) {
            currentCameraDevice.close()
            cameraDevice = null
            cameraOpenCloseLock.release()
        }
    }

    var sensorOrientation: Int = 0

    init {
        cameraId = setUpCamera()
    }

    fun setRecordingSurface(surface: Surface) {
        recordingSurface = surface
    }

    override fun getSourceWidth(): Int = previewSize.width

    override fun getSourceHeight(): Int = previewSize.height

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener
        videoSourceListener.onNewCameraParams(cameraParams)
        yuvAllocation2Rgb = YuvAllocation2Rgb(RenderScript.create(application), previewSize) { bytes ->
            this.videoSourceListener?.onNewFrame(bytes)
        }
        startBackgroundThread()
        openCamera()
    }

    override fun detach() {
        closeCamera()
        stopBackgroundThread()
        yuvAllocation2Rgb.release()
    }

    private fun openCamera() {
        val manager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera opening.", e)
        }
    }

    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            if (null != captureSession) {
                captureSession!!.close()
                captureSession = null
            }
            if (null != cameraDevice) {
                cameraDevice!!.close()
                cameraDevice = null
            }
        } catch (e: InterruptedException) {
            throw RuntimeException("Interrupted while trying to lock camera closing.", e)
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread(HANDLE_THREAD_NAME)
        backgroundThread!!.start()
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread!!.quitSafely()
        try {
            backgroundThread!!.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    private fun createCaptureSession() {
        try {
            cameraDevice?.let { camera ->

                val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surfaces = listOf(yuvAllocation2Rgb.getInputSurface(), recordingSurface)
                surfaces.forEach(previewRequestBuilder::addTarget)

                camera.createCaptureSession(
                        surfaces,
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                                cameraOpenCloseLock.acquire()
                                if (cameraDevice == null) {
                                    cameraOpenCloseLock.release()
                                    return
                                }

                                captureSession = cameraCaptureSession

                                try {
                                    // TODO test LENS_INFO_HYPERFOCAL_DISTANCE or 0.0f fixed infinity focus
                                    previewRequestBuilder.set(
                                            CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                                    )

                                    cameraCaptureSession.setRepeatingRequest(
                                            previewRequestBuilder.build(),
                                            null,
                                            backgroundHandler
                                    )
                                } catch (e: CameraAccessException) {
                                    e.printStackTrace()
                                } finally {
                                    cameraOpenCloseLock.release()
                                }
                            }

                            override fun onConfigureFailed(cameraCaptureSession: CameraCaptureSession) {
                                throw IllegalStateException("Failed to configure Camera ${cameraCaptureSession.device.id}!")
                            }
                        },
                        null
                )
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun setUpCamera(): String {
        val manager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            for (cameraId in manager.cameraIdList) {
                val characteristics = manager.getCameraCharacteristics(cameraId)

                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue
                }

                val sizes = characteristics
                                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                                    ?.getOutputSizes(SurfaceTexture::class.java)
                                    ?.toList()
                            ?: continue

                previewSize = chooseOptimalCameraResolution(
                        supportedSizes = sizes,
                        desiredSize = Size(desiredWidth, desiredHeight)
                )
                sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION)!!

                val focalLength = characteristics
                        .get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)!!
                        .first()

                val physicalSensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)!!
                val focalInPixelsX: Float = focalLength * previewSize.width / physicalSensorSize.width
                val focalInPixelsY: Float = focalLength * previewSize.height / physicalSensorSize.height

                cameraParams = CameraParams(
                        width = previewSize.width,
                        height = previewSize.height,
                        focalLength = focalLength,
                        focalInPixelsX = focalInPixelsX,
                        focalInPixelsY = focalInPixelsY
                )

                return cameraId
            }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        } catch (e: NullPointerException) {
            // thrown when the Camera2API is not supported on the device.
            // TODO check hardware capabilities
            e.printStackTrace()
        }

        return ""
    }

    companion object {
        private const val HANDLE_THREAD_NAME = "CameraBackground"

        private const val DEFAULT_FRAME_WIDTH = 1280
        private const val DEFAULT_FRAME_HEIGHT = 720
    }
}
