package com.mapbox.vision.sensors

import android.app.Activity
import android.app.Application
import android.content.Context.WINDOW_SERVICE
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.view.WindowManager
import com.mapbox.vision.mobile.core.models.DeviceMotionData
import com.mapbox.vision.mobile.core.models.HeadingData
import com.mapbox.vision.mobile.core.utils.extentions.copyFrom
import java.util.concurrent.TimeUnit

internal class SensorsManager(application: Application) : SensorEventListener {

    private var sensorDataListener: SensorDataListener? = null
    private var lastAccuracy = 0

    private val sensorManager: SensorManager = application.getSystemService(Activity.SENSOR_SERVICE) as SensorManager
    private val screenOrientation =
        (application.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val gravity = FloatArray(3)
    private val geomagneticXyz = FloatArray(3)
    private val geomagneticOrientation = FloatArray(3)

    private val rotations = FloatArray(3)
    private val orientations = FloatArray(3)

    private val userAccelerationWithGravity = FloatArray(3)

    private val rotationMatrix = FloatArray(9)

    private val listenerUpdateHandler = Handler()

    private var lastTimestamp = 0L

    fun setSensorDataListener(sensorDataListener: SensorDataListener) {
        this.sensorDataListener = sensorDataListener
    }

    fun start() {
        if (accelerometerSensor == null || magneticSensor == null) {
            return
        }
        sensorManager.registerListener(this, accelerometerSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, magneticSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, gameRotationSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, gravitySensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, gyroscopeSensor, SENSOR_DELAY_MICROS)

        if (sensorDataListener != null) {
            listenerUpdateHandler.postDelayed({ notifyListener() }, LISTENER_UPDATE_DELAY_MILLIS)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        listenerUpdateHandler.removeCallbacksAndMessages(null)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (lastAccuracy != accuracy) {
            lastAccuracy = accuracy
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (lastAccuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            return
        }
        synchronized(this) {
            lastTimestamp = TimeUnit.NANOSECONDS.toMillis(event.timestamp)
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    userAccelerationWithGravity.copyFrom(event.values)
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    geomagneticXyz.copyFrom(event.values)

                    val rotationMatrix = FloatArray(9)
                    if (SensorManager.getRotationMatrix(
                            rotationMatrix,
                            null,
                            userAccelerationWithGravity,
                            geomagneticXyz
                        )
                    ) {
                        SensorManager.getOrientation(rotationMatrix, geomagneticOrientation)
                    } else Unit
                }
                Sensor.TYPE_GYROSCOPE -> {
                    rotations.copyFrom(event.values)
                }
                Sensor.TYPE_GAME_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientations)
                }
                Sensor.TYPE_GRAVITY -> {
                    gravity.copyFrom(event.values)
                }
                else -> Unit
            }

        }
    }

    private fun notifyListener() {
        val listener = sensorDataListener ?: return

        synchronized(this) {

            // TODO is it common in HeadingData and DeviceMotionData?
            val heading = Math.toDegrees(orientations[0].toDouble()).let { if (it < 0) it + 360 else it }

            val headingGeomagnetic =
                Math.toDegrees(geomagneticOrientation[0].toDouble()).let { if (it < 0) it + 360 else it }

            listener.onDeviceMotionData(
                DeviceMotionData(
                    rotations = rotations,
                    orientations = orientations,
                    screenOrientation = screenOrientation,
                    gravity = gravity,
                    userAccelerationRelativeToGravity = userAccelerationWithGravity
                        .mapIndexed { index, value ->
                            (value - gravity[index]) / SensorManager.GRAVITY_EARTH
                        }
                        .toFloatArray(),
                    heading = heading.toFloat()
                )
            )

            listener.onHeadingData(
                HeadingData(
                    heading.toFloat(),
                    headingGeomagnetic.toFloat(),
                    geomagneticXyz,
                    lastTimestamp
                )
            )
        }
        listenerUpdateHandler.postDelayed({ notifyListener() }, LISTENER_UPDATE_DELAY_MILLIS)

    }

    companion object {
        private const val SENSOR_DELAY_MICROS = 20 * 1000
        private const val LISTENER_UPDATE_DELAY_MILLIS = 33L
    }

}
