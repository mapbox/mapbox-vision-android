package com.mapbox.vision.examples;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.HandlerThread;

import com.mapbox.vision.VisionManager;
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener;
import com.mapbox.vision.mobile.core.models.AuthorizationStatus;
import com.mapbox.vision.mobile.core.models.Camera;
import com.mapbox.vision.mobile.core.models.Country;
import com.mapbox.vision.mobile.core.models.FrameSegmentation;
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications;
import com.mapbox.vision.mobile.core.models.detection.FrameDetections;
import com.mapbox.vision.mobile.core.models.frame.ImageFormat;
import com.mapbox.vision.mobile.core.models.frame.ImageSize;
import com.mapbox.vision.mobile.core.models.position.VehicleState;
import com.mapbox.vision.mobile.core.models.road.RoadDescription;
import com.mapbox.vision.mobile.core.models.world.WorldDescription;
import com.mapbox.vision.video.videosource.VideoSource;
import com.mapbox.vision.video.videosource.VideoSourceListener;
import com.mapbox.vision.view.VisionView;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * Example shows how Vision SDK can work with external video source. This can be some custom camera implementation or any
 * other source of frames - video, set of pictures, etc.
 */
public class ExternalCameraSource extends BaseActivity {

    // Video file that will be processed.
    private static final String PATH_TO_VIDEO_FILE = "path_to_video_file";

    private VideoSourceListener videoSourceListener;
    private VisionView visionView;
    private HandlerThread handlerThread = new HandlerThread("VideoDecode");
    private boolean visionManagerWasInit = false;

    // VideoSource that will play the file.
    private VideoSource customVideoSource = new VideoSource() {
        @Override
        public void attach(@NotNull VideoSourceListener videoSourceListener) {
            // video source is attached, we can start decoding frames from video and feeding them to Vision SDK.
            ExternalCameraSource.this.videoSourceListener = videoSourceListener;
            handlerThread.start();
            new Handler(handlerThread.getLooper()).post(new Runnable() {
                @Override
                public void run() {
                    startFileVideoSource();
                }
            });
        }

        @Override
        public void detach() {
            videoSourceListener = null;
            handlerThread.quitSafely();
        }

        @Override
        public void removeListener(@NotNull VideoSourceListener observer) { }

        @Override
        public void addListener(@NotNull VideoSourceListener observer) { }
    };

    // VisionEventsListener handles events from Vision SDK on background thread.
    private VisionEventsListener visionEventsListener = new VisionEventsListener() {

        @Override
        public void onAuthorizationStatusUpdated(@NotNull AuthorizationStatus authorizationStatus) {
        }

        @Override
        public void onFrameSegmentationUpdated(@NotNull FrameSegmentation frameSegmentation) {
        }

        @Override
        public void onFrameDetectionsUpdated(@NotNull FrameDetections frameDetections) {
            visionView.setDetections(frameDetections);
        }

        @Override
        public void onFrameSignClassificationsUpdated(@NotNull FrameSignClassifications frameSignClassifications) {
        }

        @Override
        public void onRoadDescriptionUpdated(@NotNull RoadDescription roadDescription) {
        }

        @Override
        public void onWorldDescriptionUpdated(@NotNull WorldDescription worldDescription) {
        }

        @Override
        public void onVehicleStateUpdated(@NotNull VehicleState vehicleState) {
        }

        @Override
        public void onCameraUpdated(@NotNull Camera camera) {
        }

        @Override
        public void onCountryUpdated(@NotNull Country country) {
        }

        @Override
        public void onUpdateCompleted() {
        }
    };

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

    private void startVisionManager() {
        if (allPermissionsGranted() && !visionManagerWasInit) {
            VisionManager.create(customVideoSource);
            VisionManager.start(visionEventsListener);

            VisionManager.setVideoSourceListener(visionView);

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

    /**
     * Decodes video source frame by frame and feeds frames to Vision SDK.
     */
    private void startFileVideoSource() {
        // Use MediaMetadataRetriever to decode video.
        // It isn't the fastest approach to decode videos and you probably want some other method.
        // if FPS is important (eg. MediaCodec).
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(PATH_TO_VIDEO_FILE);

        // Get video frame size.
        int frameWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int frameHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
        ImageSize imageSize = new ImageSize(frameWidth, frameHeight);
        // ByteBuffer to hold RGBA bytes.
        ByteBuffer rgbaByteBuffer = ByteBuffer.wrap(new byte[frameWidth * frameHeight * 4]);

        // Get duration.
        long duration = Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

        try {
            // Get frames one by one with 1 second intervals.
            for (int seconds = 0; seconds < duration; seconds++) {
                Bitmap bitmap = retriever
                        .getFrameAtTime(
                                TimeUnit.SECONDS.toMicros(seconds),
                                MediaMetadataRetriever.OPTION_CLOSEST
                        )
                        .copy(Bitmap.Config.ARGB_8888, false);

                bitmap.copyPixelsToBuffer(rgbaByteBuffer);

                videoSourceListener.onNewFrame(
                        rgbaByteBuffer.array(),
                        ImageFormat.RGBA,
                        imageSize
                );
                rgbaByteBuffer.clear();
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        } finally {
            try {
                retriever.release();
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
        }
    }
}
