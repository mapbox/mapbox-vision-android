package com.mapbox.vision.examples;

import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.mapbox.vision.VisionManager;
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener;
import com.mapbox.vision.mobile.core.models.AuthorizationStatus;
import com.mapbox.vision.mobile.core.models.Camera;
import com.mapbox.vision.mobile.core.models.Country;
import com.mapbox.vision.mobile.core.models.FrameSegmentation;
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications;
import com.mapbox.vision.mobile.core.models.detection.FrameDetections;
import com.mapbox.vision.mobile.core.models.position.VehicleState;
import com.mapbox.vision.mobile.core.models.road.RoadDescription;
import com.mapbox.vision.mobile.core.models.world.WorldDescription;
import com.mapbox.vision.safety.VisionSafetyManager;
import com.mapbox.vision.safety.core.VisionSafetyListener;
import com.mapbox.vision.safety.core.models.CollisionObject;
import com.mapbox.vision.safety.core.models.RoadRestrictions;
import com.mapbox.vision.view.VisionView;

import org.jetbrains.annotations.NotNull;

/**
 * Example shows how overspeed can be detected using Vision and VisionSafety SDKs combined.
 */
public class SafetyActivity extends BaseActivity {

    private float maxAllowedSpeed = -1f;
    private boolean visionManagerWasInit = false;
    private VisionView visionView;
    private FrameLayout speedAlertView;
    private TextView speedLimitValueView;

    // this listener handles events from Vision SDK
    private VisionEventsListener visionEventsListener = new VisionEventsListener() {

        @Override
        public void onAuthorizationStatusUpdated(@NotNull AuthorizationStatus authorizationStatus) {
        }

        @Override
        public void onFrameSegmentationUpdated(@NotNull FrameSegmentation frameSegmentation) {
        }

        @Override
        public void onFrameDetectionsUpdated(@NotNull FrameDetections frameDetections) {
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
            // current speed of our car
            final float mySpeed = vehicleState.getSpeed();
            // check if we are overspeeding
            if (mySpeed > maxAllowedSpeed && maxAllowedSpeed > 0) {
                // all VisionListener callbacks are executed on a background thread. Need switch to a main thread
                runOnUiThread(() -> {
                    // show current speed limits in view
                    speedAlertView.setVisibility(View.VISIBLE);
                    // notify user by Toast that he's overspeeding
                    Toast.makeText(
                            SafetyActivity.this,
                            "Overspeeding! Current speed : " + mySpeed,
                            Toast.LENGTH_LONG).show();
                });
            } else {
                // hide speeding view
                runOnUiThread(() -> speedAlertView.setVisibility(View.GONE));
            }
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

    private VisionSafetyListener visionSafetyListener = new VisionSafetyListener() {
        @Override
        public void onCollisionsUpdated(@NotNull CollisionObject[] collisions) {
        }

        @Override
        public void onRoadRestrictionsUpdated(@NotNull RoadRestrictions roadRestrictions) {
            maxAllowedSpeed = roadRestrictions.getSpeedLimits().getCar().getMax();
            // set speed value to view to show it if overspeeding occurs
            runOnUiThread(() -> {
                speedLimitValueView.setText(String.valueOf(maxAllowedSpeed));
            });
        }
    };

    @Override
    protected void initViews() {
        setContentView(R.layout.activity_main);
        visionView = findViewById(R.id.vision_view);
        speedAlertView = findViewById(R.id.speed_alert_view);
        speedLimitValueView = findViewById(R.id.speed_value_view);
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
            speedLimitValueView.setVisibility(View.VISIBLE);
            VisionManager.create();
            visionView.setVisionManager(VisionManager.INSTANCE);
            VisionManager.start();
            VisionManager.setVisionEventsListener(visionEventsListener);

            VisionSafetyManager.create(VisionManager.INSTANCE);
            VisionSafetyManager.setVisionSafetyListener(visionSafetyListener);

            visionManagerWasInit = true;
        }
    }

    private void stopVisionManager() {
        if (visionManagerWasInit) {
            VisionSafetyManager.destroy();

            VisionManager.stop();
            VisionManager.destroy();

            visionManagerWasInit = false;
        }
    }
}
