package com.mapbox.vision.examples;

import android.graphics.SurfaceTexture;
import android.hardware.usb.UsbDevice;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.annotation.NonNull;

import com.mapbox.vision.VisionManager;
import com.mapbox.vision.mobile.core.models.frame.ImageFormat;
import com.mapbox.vision.mobile.core.models.frame.ImageSize;
import com.mapbox.vision.performance.ModelPerformance;
import com.mapbox.vision.performance.ModelPerformanceMode;
import com.mapbox.vision.performance.ModelPerformanceRate;
import com.mapbox.vision.video.videosource.VideoSource;
import com.mapbox.vision.video.videosource.VideoSourceListener;
import com.mapbox.vision.view.VisionView;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.UVCCamera;

/**
 * Example shows how Vision SDK can work with external USB camera.
 * [UVCCamera](https://github.com/saki4510t/UVCCamera) library is used to connect to the USB camera itself,
 * frames from camera are then fed to Vision SDK.
 */
public class UsbVideoSourceActivity extends BaseActivity {

    private static final ImageSize CAMERA_FRAME_SIZE = new ImageSize(1280, 720);

    private VisionView visionView;

    private HandlerThread backgroundHandlerThread = new HandlerThread("VideoDecode");
    private Handler backgroundHandler;

    private boolean visionManagerWasInit = false;

    /**
     * Vision SDK will attach listener to get frames and camera parameters from the USB camera.
     */
    private VideoSourceListener usbVideoSourceListener;

    /**
     * VideoSource implementation that connects to USB camera and feeds frames to VisionManager.
     */
    private VideoSource usbVideoSource = new VideoSource() {
        /**
         * VisionManager will attach [videoSourceListener] after [VisionManager.create] is called.
         * Here we open USB camera connection, and proceed connection via [onDeviceConnectListener] callbacks.
         *
         * NOTE : method is called from the same thread, that [VisionManager.create] is called.
         */
        @Override
        public void attach(@NonNull VideoSourceListener videoSourceListener) {
            if (!backgroundHandlerThread.isAlive()) {
                backgroundHandlerThread.start();
            }
            backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
            backgroundHandler.post(() ->
            {
                // Init and register USBMonitor.
                synchronized (UsbVideoSourceActivity.this) {
                    usbVideoSourceListener = videoSourceListener;
                    usbMonitor = new USBMonitor(UsbVideoSourceActivity.this, onDeviceConnectListener);
                    usbMonitor.register();
                }
            });
        }

        /**
         * VisionManager will detach listener after [VisionManager.destroy] is called.
         * Here we close USB camera connection.
         *
         * NOTE : method is called from the same thread, that [VisionManager.destroy] is called.
         */
        @Override
        public void detach() {
            backgroundHandler.post(() -> {
                synchronized (UsbVideoSourceActivity.this) {
                    if (usbMonitor != null) {
                        usbMonitor.unregister();
                        usbMonitor.destroy();
                    }
                    if (uvcCamera != null) {
                        uvcCamera.stopPreview();
                        releaseCamera();
                    }
                    usbVideoSourceListener = null;
                }
            });

            backgroundHandlerThread.quitSafely();
        }
    };

    private USBMonitor usbMonitor;
    private UVCCamera uvcCamera;

    @Override
    protected void initViews() {
        setContentView(R.layout.activity_main);
        visionView = findViewById(R.id.vision_view);
    }

    @Override
    protected void onPermissionsGranted() {
        startVisionManager();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startVisionManager();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopVisionManager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visionView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        visionView.onPause();
    }

    private void startVisionManager() {
        if (allPermissionsGranted() && !visionManagerWasInit) {
            VisionManager.create(usbVideoSource);
            VisionManager.setModelPerformance(
                new ModelPerformance.On(ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH.INSTANCE)
            );
            visionView.setVisionManager(VisionManager.INSTANCE);
            VisionManager.start();

            visionManagerWasInit = true;
        }
    }

    private void stopVisionManager() {
        if (visionManagerWasInit) {
            VisionManager.stop();
            VisionManager.destroy();

            visionManagerWasInit = false;
        }
    }

    private USBMonitor.OnDeviceConnectListener onDeviceConnectListener = new USBMonitor.OnDeviceConnectListener() {
        @Override
        public void onAttach(UsbDevice device) {
            synchronized (UsbVideoSourceActivity.this) {
                usbMonitor.requestPermission(device);
            }
        }

        @Override
        public void onConnect(
                UsbDevice device,
                USBMonitor.UsbControlBlock ctrlBlock,
                boolean createNew
        ) {
            backgroundHandler.post(() -> {
                synchronized (UsbVideoSourceActivity.this) {
                    releaseCamera();
                    initializeCamera(ctrlBlock);
                }
            });
        }

        @Override
        public void onDetach(UsbDevice device) {
        }

        @Override
        public void onCancel(UsbDevice device) {
        }

        @Override
        public void onDisconnect(UsbDevice device, USBMonitor.UsbControlBlock ctrlBlock) {
            backgroundHandler.post(() -> {
                synchronized (UsbVideoSourceActivity.this) {
                    releaseCamera();
                }
            });
        }
    };

    private void releaseCamera() {
        if (uvcCamera != null) {
            uvcCamera.close();
            uvcCamera.destroy();
            uvcCamera = null;
        }
    }

    private void initializeCamera(USBMonitor.UsbControlBlock ctrlBlock) {
        uvcCamera = new UVCCamera();
        uvcCamera.open(ctrlBlock);
        uvcCamera.setPreviewSize(
                CAMERA_FRAME_SIZE.getImageWidth(),
                CAMERA_FRAME_SIZE.getImageHeight(),
                UVCCamera.FRAME_FORMAT_YUYV
        );

        SurfaceTexture surfaceTexture = new SurfaceTexture(createExternalGlTexture());
        surfaceTexture.setDefaultBufferSize(
                CAMERA_FRAME_SIZE.getImageWidth(),
                CAMERA_FRAME_SIZE.getImageHeight()
        );
        // Start preview to external GL texture
        // NOTE : this is necessary for callback passed to [UVCCamera.setFrameCallback]
        // to be triggered afterwards
        uvcCamera.setPreviewTexture(surfaceTexture);
        uvcCamera.startPreview();

        // Set callback that will feed frames from the USB camera to Vision SDK
        uvcCamera.setFrameCallback(
                (frame) -> usbVideoSourceListener.onNewFrame(
                        new VideoSourceListener.FrameHolder.ByteBufferHolder(frame),
                        ImageFormat.RGBA,
                        CAMERA_FRAME_SIZE
                ),
                UVCCamera.PIXEL_FORMAT_RGBX
        );
    }

    /**
     * Create external OpenGL texture for [uvcCamera].
     */
    private int createExternalGlTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texId = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameterf(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR
        );
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
        );
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
        );
        return texId;
    }
}
