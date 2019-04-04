package com.mapbox.vision.examples;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
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
import org.jetbrains.annotations.NotNull;

/**
 * Example shows how overspeed can be detected using Vision and VisionSafety SDKs combined.
 */
public class SafetyActivity extends AppCompatActivity {

    private Float maxAllowedSpeed = -1f;

    // this listener handles events from Vision SDK
    private VisionEventsListener visionEventsListener = new VisionEventsListener() {

        @Override
        public void onAuthorizationStatusUpdated(@NotNull AuthorizationStatus authorizationStatus) {}

        @Override
        public void onFrameSegmentationUpdated(@NotNull FrameSegmentation frameSegmentation) {}

        @Override
        public void onFrameDetectionsUpdated(@NotNull FrameDetections frameDetections) {}

        @Override
        public void onFrameSignClassificationsUpdated(@NotNull FrameSignClassifications frameSignClassifications) {}

        @Override
        public void onRoadDescriptionUpdated(@NotNull RoadDescription roadDescription) {}

        @Override
        public void onWorldDescriptionUpdated(@NotNull WorldDescription worldDescription) {}

        @Override
        public void onVehicleStateUpdated(@NotNull VehicleState vehicleState) {
            // current speed of our car
            Float mySpeed = vehicleState.getSpeed();

            // display toast with overspeed warning if our speed is greater than maximum allowed speed
            if (mySpeed > maxAllowedSpeed && maxAllowedSpeed > 0) {
                Toast.makeText(
                        SafetyActivity.this,
                        "Overspeeding! Current speed : " + mySpeed +
                                ", allowed speed : " + maxAllowedSpeed,
                        Toast.LENGTH_LONG
                ).show();
            }
        }

        @Override
        public void onCameraUpdated(@NotNull Camera camera) {}

        @Override
        public void onCountryUpdated(@NotNull Country country) {}

        @Override
        public void onUpdateCompleted() {}
    };

    private VisionSafetyListener visionSafetyListener = new VisionSafetyListener() {
        @Override
        public void onCollisionsUpdated(@NotNull CollisionObject[] collisions) {}

        @Override
        public void onRoadRestrictionsUpdated(@NotNull RoadRestrictions roadRestrictions) {
            maxAllowedSpeed = roadRestrictions.getSpeedLimits().getCar().getMax();
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        super.onStart();

        VisionManager.create();
        VisionManager.start(visionEventsListener);

        VisionSafetyManager.create(VisionManager.INSTANCE, visionSafetyListener);
    }

    @Override
    protected void onStop() {
        super.onStop();

        VisionSafetyManager.destroy();

        VisionManager.stop();
        VisionManager.destroy();
    }
}
