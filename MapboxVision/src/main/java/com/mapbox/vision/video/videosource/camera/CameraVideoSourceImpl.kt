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
import android.media.MediaCodec
import android.media.MediaRecorder
import android.os.Handler
import android.os.HandlerThread
import android.renderscript.RenderScript
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.WindowManager
import com.mapbox.vision.models.CameraParamsData
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.io.File
import java.io.IOException
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

internal class CameraVideoSourceImpl(
        val application: Application,
        private val desiredWidth: Int = DEFAULT_FRAME_WIDTH,
        private val desiredHeight: Int = DEFAULT_FRAME_HEIGHT
) : VideoSource {

    private val cameraId: String

    private var videoSourceListener: VideoSourceListener? = null

    private lateinit var yuvAllocation2Rgb: YuvAllocation2Rgb
    private lateinit var previewSize: Size
    private lateinit var cameraParams: CameraParamsData

    private var captureSession: CameraCaptureSession? = null

    // Prevents app from exiting before closing the camera.
    private val cameraOpenCloseLock = Semaphore(1)

    private var cameraDevice: CameraDevice? = null

    private var backgroundHandler: Handler? = null
    private var backgroundThread: HandlerThread? = null

    private val stateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(currentCameraDevice: CameraDevice) {
            cameraDevice = currentCameraDevice
            createCameraPreviewSession()
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

    private var nextVideoFilePath: String? = null

    private var mediaRecorder: MediaRecorder? = null
    private var isRecordingVideo = false

    private val recordingSurface = MediaCodec.createPersistentInputSurface()

    private var currentBufferNum = 0

    private var sensorOrientation = 0

    private val buffersDataDir = FileUtils.getDataDirPath(application)

    init {
        cameraId = setUpCamera()
    }

    override fun getSourceWidth(): Int = previewSize.width

    override fun getSourceHeight(): Int = previewSize.height

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener
        videoSourceListener.onNewCameraParams(cameraParams)
        yuvAllocation2Rgb = YuvAllocation2Rgb(RenderScript.create(application), previewSize) { bytes ->
            this.videoSourceListener?.onNewFrame(bytes)
        }
        mediaRecorder = MediaRecorder()
        startBackgroundThread()
        openCamera()
    }

    override fun detach() {
        closeCamera()
        stopBackgroundThread()
        yuvAllocation2Rgb.release()
        videoSourceListener = null

        recordingSurface.release()
        mediaRecorder?.release()
        mediaRecorder = null
    }

    override fun stopVideoRecording() {
        if (!isRecordingVideo) {
            return
        }
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            mediaRecorder?.reset()
        }
        isRecordingVideo = false

        val filePath = nextVideoFilePath
        if (filePath != null && !filePath.isBlank()) {
            videoSourceListener?.onFileRecorded(filePath)
        }

        nextVideoFilePath = null
    }

    override fun startVideoRecording() {
        updateNextBufferFile()
        mediaRecorder?.setup(nextVideoFilePath!!)
        mediaRecorder?.start()
        isRecordingVideo = true
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

    private fun createCameraPreviewSession() {
        try {
            updateNextBufferFile()
            mediaRecorder?.setup(nextVideoFilePath!!)

            cameraDevice?.let { camera ->

                val previewRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(yuvAllocation2Rgb.getInputSurface())
                    addTarget(recordingSurface)
                }

                camera.createCaptureSession(
                        listOf(
                                yuvAllocation2Rgb.getInputSurface(),
                                recordingSurface
                        ),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                                cameraOpenCloseLock.acquire()
                                if (cameraDevice == null) {
                                    cameraOpenCloseLock.release()
                                    return
                                }

                                captureSession = cameraCaptureSession

                                isRecordingVideo = true
                                mediaRecorder?.start()

                                try {
                                    // Auto focus should be continuous for camera preview.
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
                                // TODO: Proceed edge case
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

                cameraParams = CameraParamsData(
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

    @Throws(IOException::class)
    private fun MediaRecorder.setup(nextVideoFilePath: String) {
        val rotation = (application.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        when (sensorOrientation) {
            SENSOR_ORIENTATION_DEFAULT_DEGREES ->
                setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation))
            SENSOR_ORIENTATION_INVERSE_DEGREES ->
                setOrientationHint(INVERSE_ORIENTATIONS.get(rotation))
        }

        setVideoSource(MediaRecorder.VideoSource.SURFACE)
        setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        setOutputFile(nextVideoFilePath)
        setVideoEncodingBitRate(6000000)
        setVideoFrameRate(30)
        setVideoSize(previewSize.width, previewSize.height)
        setVideoEncoder(MediaRecorder.VideoEncoder.H264)
        setInputSurface(recordingSurface)
        prepare()
    }

    private fun updateNextBufferFile() {
        if (nextVideoFilePath.isNullOrEmpty()) {
            nextVideoFilePath = FileUtils.getVideoFilePath(buffersDataDir, BUFFER_FILE_NAMES[currentBufferNum])
            val bufferFile = File(nextVideoFilePath)
            if (bufferFile.exists()) {
                bufferFile.delete()
            }
            currentBufferNum++
            if (currentBufferNum >= VIDEO_BUFFERS_NUMBER) {
                currentBufferNum = 0
            }
        }
    }

    companion object {
        private const val VIDEO_BUFFERS_NUMBER = 3

        private const val HANDLE_THREAD_NAME = "CameraBackground"

        private const val SENSOR_ORIENTATION_DEFAULT_DEGREES = 90
        private const val SENSOR_ORIENTATION_INVERSE_DEGREES = 270

        private val DEFAULT_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 90)
            append(Surface.ROTATION_90, 0)
            append(Surface.ROTATION_180, 270)
            append(Surface.ROTATION_270, 180)
        }

        private val INVERSE_ORIENTATIONS = SparseIntArray().apply {
            append(Surface.ROTATION_0, 270)
            append(Surface.ROTATION_90, 180)
            append(Surface.ROTATION_180, 90)
            append(Surface.ROTATION_270, 0)
        }

        val BUFFER_FILE_NAMES = listOf(
                "video1.mp4",
                "video2.mp4",
                "video3.mp4"
        )

        // Work resolution
        private const val DEFAULT_FRAME_WIDTH = 1280
        private const val DEFAULT_FRAME_HEIGHT = 720
    }
}
