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
import com.mapbox.vision.performance.ModelPerformanceMode;
import com.mapbox.vision.performance.ModelPerformanceRate;

import org.jetbrains.annotations.NotNull;

import java.nio.ByteBuffer;

public class CustomDetectionActivity extends BaseActivity {

    private Boolean visionManagerWasInit = false;
    private ImageView detectionsView;
    private Paint paint;

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
            final Bitmap frameBitmap = convertImageToBitmap(frameDetections.getFrame().getImage());
            // now we will draw current detections on canvas with frame bitmap
            final Canvas canvas = new Canvas(frameBitmap);
            for (final Detection detection : frameDetections.getDetections()) {
                // we will draw only detected cars
                // and filter detections which we are not confident with
                if (detection.getDetectionClass() == DetectionClass.Car && detection.getConfidence() > 0.6) {
                    drawSingleDetection(canvas, detection);
                }
            }
            runOnUiThread(() -> {
                // finally we update our image view on main thread
                detectionsView.setImageBitmap(frameBitmap);
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
        preparePaint();
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
            VisionManager.setModelPerformance(
                new ModelPerformance.On(
                        ModelPerformanceMode.FIXED, ModelPerformanceRate.HIGH.INSTANCE
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

    private Bitmap convertImageToBitmap(final Image originalImage) {
        final Bitmap bitmap = Bitmap.createBitmap(
                originalImage.getSize().getImageWidth(),
                originalImage.getSize().getImageHeight(),
                Bitmap.Config.ARGB_8888
        );
        // prepare direct ByteBuffer that will hold camera frame data
        ByteBuffer buffer = ByteBuffer.allocateDirect(originalImage.sizeInBytes());
        // associate underlying native ByteBuffer with our buffer
        originalImage.copyPixels(buffer);
        buffer.rewind();
        // copy ByteBuffer to bitmap
        bitmap.copyPixelsFromBuffer(buffer);
        return bitmap;
    }

    private void drawSingleDetection(final Canvas canvas, final Detection detection) {
        // first thing we get coordinates of bounding box
        final RectF relativeBbox = detection.getBoundingBox();
        // we need to transform them from relative (range [0, 1]) to absolute in terms of canvas(frame) size
        // we do not care about screen resolution at all - we will use cropCenter mode
        final RectF absoluteBbox = new RectF(
                relativeBbox.left * canvas.getWidth(),
                relativeBbox.top * canvas.getHeight(),
                relativeBbox.right * canvas.getWidth(),
                relativeBbox.bottom * canvas.getHeight()
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

    private void preparePaint() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.GREEN);
        paint.setStrokeWidth(5f);
        paint.setStyle(Paint.Style.STROKE);
    }
}
