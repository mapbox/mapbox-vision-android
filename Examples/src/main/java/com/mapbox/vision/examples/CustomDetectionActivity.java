package com.mapbox.vision.examples;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.widget.ImageView;
import com.mapbox.vision.VisionManager;
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener;
import com.mapbox.vision.mobile.core.models.AuthorizationStatus;
import com.mapbox.vision.mobile.core.models.Camera;
import com.mapbox.vision.mobile.core.models.Country;
import com.mapbox.vision.mobile.core.models.FrameSegmentation;
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications;
import com.mapbox.vision.mobile.core.models.detection.Detection;
import com.mapbox.vision.mobile.core.models.detection.DetectionClass;
import com.mapbox.vision.mobile.core.models.detection.FrameDetections;
import com.mapbox.vision.mobile.core.models.frame.Image;
import com.mapbox.vision.mobile.core.models.position.VehicleState;
import com.mapbox.vision.mobile.core.models.road.RoadDescription;
import com.mapbox.vision.mobile.core.models.world.WorldDescription;
import com.mapbox.vision.performance.ModelPerformance;
import com.mapbox.vision.performance.ModelPerformanceConfig;
import com.mapbox.vision.performance.ModelPerformanceMode;
import com.mapbox.vision.performance.ModelPerformanceRate;
import java.nio.ByteBuffer;
import org.jetbrains.annotations.NotNull;

public class CustomDetectionActivity extends BaseActivity {

    private Boolean visionManagerWasInit = false;
    private ImageView detectionsView;

    // VisionEventsListener handles events from Vision SDK on background thread.
    private VisionEventsListener visionEventsListener = new VisionEventsListener() {

        @Override
        public void onAuthorizationStatusUpdated(@NotNull AuthorizationStatus authorizationStatus) {

        }

        @Override
        public void onCameraUpdated(@NotNull Camera camera) {

        }

        @Override
        public void onCountryUpdated(@NotNull Country country) {

        }

        @Override
        public void onFrameDetectionsUpdated(@NotNull FrameDetections frameDetections) {
            // prepare camera frame first thing
            final Image originalImage = frameDetections.getFrame().getImage();
            final Bitmap frame = Bitmap.createBitmap(
                    originalImage.getSize().getImageWidth(),
                    originalImage.getSize().getImageHeight(),
                    Bitmap.Config.ARGB_8888
            );
            // prepare direct ByteBuffer that will hold camera frame data
            final ByteBuffer buffer = ByteBuffer.allocateDirect(originalImage.sizeInBytes());
            // we need to lock pixels  explicitly because we will draw in another (main) thread
            originalImage.lockPixels();
            // associate underlying native ByteBuffer with our buffer
            originalImage.copyPixels(buffer);
            buffer.rewind();
            // copy ByteBuffer to bitmap
            frame.copyPixelsFromBuffer(buffer);
            // now we will draw current detections on canvas
            final Canvas canvas = new Canvas(frame);
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(Color.GREEN);
            paint.setStrokeWidth(5f);
            paint.setStyle(Paint.Style.STROKE);
            for (final Detection detection : frameDetections.getDetections()) {
                // we will draw only detected cars
                // and filter detections which we are not confident with
                if (detection.getDetectionClass() == DetectionClass.Car && detection.getConfidence() > 0.6) {
                    // first thing we get coordinates of bounding box
                    final RectF relativeBbox = detection.getBoundingBox();
                    // we need to transform them from relative (range [0, 1]) to absolute in terms of frame size
                    // we do not care about screen resolution at all - we will use cropCenter mode
                    final RectF absoluteBbox = new RectF(
                            relativeBbox.left * frame.getWidth(),
                            relativeBbox.top * frame.getHeight(),
                            relativeBbox.right * frame.getWidth(),
                            relativeBbox.bottom * frame.getHeight()
                    );
                    // we want to draw circle bounds, we need radius and center for that
                    final float radius = (float) Math.sqrt(
                            Math.pow(absoluteBbox.centerX() - absoluteBbox.left, 2) +
                                    Math.pow(absoluteBbox.centerY() - absoluteBbox.top, 2)
                    );
                    canvas.drawCircle(
                            absoluteBbox.centerX(),
                            absoluteBbox.centerY(),
                            radius,
                            paint
                    );
                }
            }
            runOnUiThread(() -> {
                // finally we update our image view on main thread
                detectionsView.setImageBitmap(frame);
                // and we could safely release underlying pixels
                originalImage.unlockPixels();
            });
        }

        @Override
        public void onFrameSegmentationUpdated(@NotNull FrameSegmentation frameSegmentation) {

        }

        @Override
        public void onFrameSignClassificationsUpdated(@NotNull FrameSignClassifications frameSignClassifications) {

        }

        @Override
        public void onRoadDescriptionUpdated(@NotNull RoadDescription roadDescription) {

        }

        @Override
        public void onUpdateCompleted() {

        }

        @Override
        public void onVehicleStateUpdated(@NotNull VehicleState vehicleState) {

        }

        @Override
        public void onWorldDescriptionUpdated(@NotNull WorldDescription worldDescription) {

        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        detectionsView = findViewById(R.id.detections_view);
    }

    @Override
    public void onPermissionsGranted() {
        startVisionManager();
    }

    @Override
    public void initViews() {
        setContentView(R.layout.activity_custom_detection);
    }

    @Override
    public void onStart() {
        super.onStart();
        startVisionManager();
    }

    @Override
    public void onStop() {
        super.onStop();
        stopVisionManager();
    }

    private void startVisionManager() {
        if (allPermissionsGranted() && !visionManagerWasInit) {
            VisionManager.create();
            VisionManager.setModelPerformanceConfig(
                    new ModelPerformanceConfig.Merged(
                            new ModelPerformance.On(
                                    ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH
                            )
                    )
            );
            VisionManager.setVisionEventsListener(visionEventsListener);
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
}
