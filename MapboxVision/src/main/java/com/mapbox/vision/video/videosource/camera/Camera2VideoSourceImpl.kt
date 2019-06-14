package com.mapbox.vision.video.videosource.camera

import android.app.Application
import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.renderscript.RenderScript
import android.util.Size
import com.mapbox.vision.mobile.core.models.CameraParameters
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.utils.threads.WorkThreadHandler
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class Camera2VideoSourceImpl(
    private val application: Application,
    private val desiredWidth: Int = DEFAULT_FRAME_WIDTH,
    private val desiredHeight: Int = DEFAULT_FRAME_HEIGHT
) : VideoSource {

    private val cameraId: String

    private var videoSourceListener: VideoSourceListener? = null

    private lateinit var videoRecorder: SurfaceVideoRecorder
    private lateinit var yuvAllocation2Rgba: YuvAllocation2Rgba
    private lateinit var previewSize: ImageSize
    private lateinit var cameraParameters: CameraParameters

    private var sensorOrientation: Int = 0

    private var captureSession: CameraCaptureSession? = null

    // Prevents app from exiting before closing the camera.
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null

    private var backgroundThreadHandler: WorkThreadHandler = WorkThreadHandler(HANDLE_THREAD_NAME)

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

    init {
        cameraId = setUpCamera()
    }

    internal fun setVideoRecorder(videoRecorder: SurfaceVideoRecorder) {
        this.videoRecorder = videoRecorder
        videoRecorder.init(
            frameWidth = previewSize.imageWidth,
            frameHeight = previewSize.imageHeight,
            sensorOrientation = sensorOrientation
        )
    }

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener
        videoSourceListener.onNewCameraParameters(cameraParameters)
        yuvAllocation2Rgba = YuvAllocation2Rgba(RenderScript.create(application), previewSize) { bytes ->
            this.videoSourceListener?.onNewFrame(bytes, ImageFormat.RGBA, previewSize)
        }
        backgroundThreadHandler.start()
        openCamera()
    }

    override fun detach() {
        closeCamera()
        videoSourceListener = null
        backgroundThreadHandler.stop()
        yuvAllocation2Rgba.release()
    }

    private fun openCamera() {
        val manager = application.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw RuntimeException("Time out waiting to lock camera opening.")
            }
            manager.openCamera(cameraId, stateCallback, backgroundThreadHandler.handler)
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

    private fun createCaptureSession() {
        try {
            cameraDevice?.let { camera ->

                val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                val surfaces = listOf(yuvAllocation2Rgba.getInputSurface(), videoRecorder.surface)
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
                                    backgroundThreadHandler.handler
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

                val focalInPixelsX: Float = focalLength * previewSize.imageWidth / physicalSensorSize.width
                val focalInPixelsY: Float = focalLength * previewSize.imageHeight / physicalSensorSize.height

                cameraParameters = CameraParameters(
                    width = previewSize.imageWidth,
                    height = previewSize.imageHeight,
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

        private fun chooseOptimalCameraResolution(
            supportedSizes: List<Size>,
            desiredSize: Size
        ): ImageSize {
            val minDimension = Math.min(desiredSize.width, desiredSize.height)

            val bigEnough = mutableListOf<Size>()
            for (option in supportedSizes) {
                if (option == desiredSize) {
                    return ImageSize(desiredSize.width, desiredSize.height)
                }

                if (option.height >= minDimension && option.width >= minDimension) {
                    bigEnough.add(option)
                }
            }

            // Pick the smallest of those, assuming we found any
            val size = bigEnough.minBy { it.width.toLong() * it.height } ?: supportedSizes.first()

            return ImageSize(size.width, size.height)
        }
    }
}
