package com.mapbox.vision.examples

import android.view.View
import androidx.core.content.ContextCompat
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

    private sealed class SpeedLimit(val imageResId: Int, val textColorId: Int) {
        class Overspeeding : SpeedLimit(R.drawable.speed_limit_overspeeding, android.R.color.white)
        class NormalSpeed : SpeedLimit(R.drawable.speed_limit_normal, android.R.color.black)
    }

    // this listener handles events from Vision SDK
    private val visionEventsListener = object : VisionEventsListener {

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {}

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {
            // do nothing if we did not find any speed limit signs
            if (maxAllowedSpeed == -1f) return

            // current speed of our car
            val mySpeed = vehicleState.speed
            val currentSpeedState = if (mySpeed > maxAllowedSpeed && maxAllowedSpeed > 0) {
                SpeedLimit.Overspeeding()
            } else {
                SpeedLimit.NormalSpeed()
            }
            // all VisionListener callbacks are executed on a background thread. Need switch to a main thread
            runOnUiThread {
                speed_sign_view.setImageResource(currentSpeedState.imageResId)
                speed_value_view.setTextColor(ContextCompat.getColor(this@SafetyActivityKt, currentSpeedState.textColorId))
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
            if (maxAllowedSpeed != -1f) {
                runOnUiThread {
                    // set speed limit
                    speed_value_view.text = maxAllowedSpeed.toInt().toString()
                    // start showing alert view
                    speed_alert_view.visibility = View.VISIBLE
                }
            }
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
