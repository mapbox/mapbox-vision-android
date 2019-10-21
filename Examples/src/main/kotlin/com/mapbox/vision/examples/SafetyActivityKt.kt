package com.mapbox.vision.examples

import android.widget.Toast
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import com.mapbox.vision.safety.VisionSafetyManager
import com.mapbox.vision.safety.core.VisionSafetyListener
import com.mapbox.vision.safety.core.models.CollisionObject
import com.mapbox.vision.safety.core.models.RoadRestrictions
import kotlinx.android.synthetic.main.activity_main.*

/**
 * Example shows how overspeed can be detected using Vision and VisionSafety SDKs combined.
 */
class SafetyActivityKt : BaseActivity() {

    private var maxAllowedSpeed: Float = -1f
    private var visionManagerWasInit = false

    // this listener handles events from Vision SDK
    private val visionEventsListener = object : VisionEventsListener {

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {}

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            // current speed of our car
            val mySpeed = vehicleState.speed

            // display toast with overspeed warning if our speed is greater than maximum allowed speed
            if (mySpeed > maxAllowedSpeed && maxAllowedSpeed > 0) {
                // all VisionListener callbacks are executed on a background thread. Need switch to a main thread
                runOnUiThread {
                    Toast.makeText(
                        this@SafetyActivityKt,
                        "Overspeeding! Current speed : $mySpeed, allowed speed : $maxAllowedSpeed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        override fun onCameraUpdated(camera: Camera) {}

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}
    }

    // this listener handles events from VisionSafety SDK
    private val visionSafetyListener = object : VisionSafetyListener {
        override fun onCollisionsUpdated(collisions: Array<CollisionObject>) {
        }

        override fun onRoadRestrictionsUpdated(roadRestrictions: RoadRestrictions) {
            maxAllowedSpeed = roadRestrictions.speedLimits.car.max
        }
    }

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
            VisionManager.create()
            vision_view.setVisionManager(VisionManager)
            VisionManager.start()
            VisionManager.visionEventsListener = visionEventsListener

            VisionSafetyManager.create(VisionManager)
            VisionSafetyManager.visionSafetyListener = visionSafetyListener

            visionManagerWasInit = true
        }
    }

    private fun stopVisionManager() {
        if (visionManagerWasInit) {
            VisionSafetyManager.destroy()

            VisionManager.stop()
            VisionManager.destroy()

            visionManagerWasInit = false
        }
    }
}
