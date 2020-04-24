package com.mapbox.vision.examples

import android.graphics.SurfaceTexture
import android.hardware.usb.UsbDevice
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.performance.ModelPerformance
import com.mapbox.vision.performance.ModelPerformanceMode
import com.mapbox.vision.performance.ModelPerformanceRate
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.android.synthetic.main.activity_main.*

/**
 * Example shows how Vision SDK can work with external USB camera.
 * [UVCCamera](https://github.com/saki4510t/UVCCamera) library is used to connect to the USB camera itself,
 * frames from camera are then fed to Vision SDK.
 */
class UsbVideoSourceActivityKt : BaseActivity() {

    companion object {
        private val CAMERA_FRAME_SIZE = ImageSize(
            imageWidth = 1280,
            imageHeight = 720
        )
    }

    private val backgroundHandlerThread = HandlerThread("VideoDecode").apply { start() }
    private var backgroundHandler = Handler(backgroundHandlerThread.looper)

    private var visionManagerWasInit = false

    /**
     * Vision SDK will attach listener to get frames and camera parameters from the USB camera.
     */
    private var usbVideoSourceListener: VideoSourceListener? = null

    /**
     * VideoSource implementation that connects to USB camera and feeds frames to VisionManager.
     */
    private val usbVideoSource = object : VideoSource {

        /**
         * VisionManager will attach [videoSourceListener] after [VisionManager.create] is called.
         * Here we open USB camera connection, and proceed connection via [onDeviceConnectListener] callbacks.
         *
         * NOTE : method is called from the same thread, that [VisionManager.create] is called.
         */
        override fun attach(videoSourceListener: VideoSourceListener) {
            if (!backgroundHandlerThread.isAlive) {
                backgroundHandlerThread.start()
            }
            backgroundHandler.post {
                // Init and register USBMonitor.
                synchronized(this@UsbVideoSourceActivityKt) {
                    this@UsbVideoSourceActivityKt.usbVideoSourceListener = videoSourceListener

                    usbMonitor = USBMonitor(this@UsbVideoSourceActivityKt, onDeviceConnectListener)
                    usbMonitor?.register()
                }
            }
        }

        /**
         * VisionManager will detach listener after [VisionManager.destroy] is called.
         * Here we close USB camera connection.
         *
         * NOTE : method is called from the same thread, that [VisionManager.destroy] is called.
         */
        override fun detach() {
            backgroundHandler.post {
                synchronized(this@UsbVideoSourceActivityKt) {
                    usbMonitor?.unregister()
                    uvcCamera?.stopPreview()
                    usbMonitor?.destroy()
                    releaseCamera()
                    usbVideoSourceListener = null
                }
            }

            backgroundHandlerThread.quitSafely()
        }
    }

    private var usbMonitor: USBMonitor? = null
    private var uvcCamera: UVCCamera? = null

    override fun onPermissionsGranted() {
        startVisionManager()
    }

    override fun initViews() {
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()
        startVisionManager()
    }

    override fun onStop() {
        super.onStop()
        stopVisionManager()
    }

    override fun onResume() {
        super.onResume()
        vision_view.onResume()
    }

    override fun onPause() {
        super.onPause()
        vision_view.onPause()
    }

    private fun startVisionManager() {
        if (allPermissionsGranted() && !visionManagerWasInit) {
            VisionManager.create(usbVideoSource)
            VisionManager.setModelPerformance(
                ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH)
            )
            vision_view.setVisionManager(VisionManager)
            VisionManager.start()

            visionManagerWasInit = true
        }
    }

    private fun stopVisionManager() {
        if (visionManagerWasInit) {
            VisionManager.stop()
            VisionManager.destroy()

            visionManagerWasInit = false
        }
    }

    private val onDeviceConnectListener: USBMonitor.OnDeviceConnectListener =
        object : USBMonitor.OnDeviceConnectListener {
            override fun onAttach(device: UsbDevice?) {
                synchronized(this@UsbVideoSourceActivityKt) {
                    usbMonitor?.requestPermission(device!!)
                }
            }

            override fun onConnect(
                device: UsbDevice?,
                ctrlBlock: USBMonitor.UsbControlBlock?,
                createNew: Boolean
            ) {
                backgroundHandler.post {
                    synchronized(this@UsbVideoSourceActivityKt) {
                        releaseCamera()
                        initializeCamera(ctrlBlock!!)
                    }
                }
            }

            override fun onDetach(device: UsbDevice?) {}

            override fun onCancel(device: UsbDevice?) {}

            override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
                backgroundHandler.post {
                    synchronized(this@UsbVideoSourceActivityKt) {
                        releaseCamera()
                    }
                }
            }
        }

    private fun releaseCamera() {
        uvcCamera?.close()
        uvcCamera?.destroy()
        uvcCamera = null
    }

    private fun initializeCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        uvcCamera = UVCCamera().also { camera ->
            camera.open(ctrlBlock)
            camera.setPreviewSize(
                CAMERA_FRAME_SIZE.imageWidth,
                CAMERA_FRAME_SIZE.imageHeight,
                UVCCamera.FRAME_FORMAT_YUYV
            )

            val surfaceTexture = SurfaceTexture(createExternalGlTexture())
            surfaceTexture.setDefaultBufferSize(
                CAMERA_FRAME_SIZE.imageWidth,
                CAMERA_FRAME_SIZE.imageHeight
            )
            // Start preview to external GL texture
            // NOTE : this is necessary for callback passed to [UVCCamera.setFrameCallback]
            // to be triggered afterwards
            camera.setPreviewTexture(surfaceTexture)
            camera.startPreview()

            // Set callback that will feed frames from the USB camera to Vision SDK
            camera.setFrameCallback(
                { frame ->
                    usbVideoSourceListener?.onNewFrame(
                        VideoSourceListener.FrameHolder.ByteBufferHolder(frame),
                        ImageFormat.RGBA,
                        CAMERA_FRAME_SIZE
                    )
                },
                UVCCamera.PIXEL_FORMAT_RGBX
            )
        }
    }

    /**
     * Create external OpenGL texture for [uvcCamera].
     */
    private fun createExternalGlTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val texId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId)
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameterf(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR.toFloat()
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
            GLES20.GL_CLAMP_TO_EDGE
        )
        GLES20.glTexParameteri(
            GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
            GLES20.GL_CLAMP_TO_EDGE
        )
        return texId
    }
}
