package com.mapbox.vision.sensors

import android.app.Activity
import android.app.Application
import android.content.Context.WINDOW_SERVICE
import android.hardware.Sensor
import android.hardware.Sensor.TYPE_ACCELEROMETER
import android.hardware.Sensor.TYPE_GAME_ROTATION_VECTOR
import android.hardware.Sensor.TYPE_GRAVITY
import android.hardware.Sensor.TYPE_GYROSCOPE
import android.hardware.Sensor.TYPE_MAGNETIC_FIELD
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.view.WindowManager
import com.mapbox.vision.models.DeviceMotionData
import com.mapbox.vision.models.HeadingData

internal class SensorsRequestsManager(application: Application) : SensorEventListener {

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
    private val geomagnetic = FloatArray(3)

    private val rotations = FloatArray(3)
    private val orientations = FloatArray(3)

    private val userAccelerationWithGravity = FloatArray(3)

    private val rotationMatrix = FloatArray(9)

    private val listenerUpdateHandler = Handler()

    private var lastTimestamp = 0L

    fun setSensorDataListener(sensorDataListener: SensorDataListener) {
        this.sensorDataListener = sensorDataListener
    }

    fun startDataRequesting() {
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

    fun stopDataRequesting() {
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
            lastTimestamp = event.timestamp / 1000000
            when (event.sensor.type) {
                TYPE_ACCELEROMETER -> {
                    userAccelerationWithGravity[0] = event.values[0]
                    userAccelerationWithGravity[1] = event.values[1]
                    userAccelerationWithGravity[2] = event.values[2]
                }
                TYPE_MAGNETIC_FIELD -> {
                    geomagnetic[0] = event.values[0]
                    geomagnetic[1] = event.values[1]
                    geomagnetic[2] = event.values[2]
                }
                TYPE_GYROSCOPE -> {
                    System.arraycopy(event.values, 0, rotations, 0, rotations.size)
                }
                TYPE_GAME_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientations)
                }
                TYPE_GRAVITY -> {
                    gravity[0] = event.values[0]
                    gravity[1] = event.values[1]
                    gravity[2] = event.values[2]
                }
                else -> {
                }
            }

        }
    }

    private fun notifyListener() {
        val listener = sensorDataListener ?: return

        synchronized(this) {

            var heading = Math.toDegrees(orientations[0].toDouble())
            if (heading < 0) {
                heading += 360
            }

            listener.onDeviceMotionDataReady(
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

            listener.onHeadingDataReady(HeadingData(heading.toFloat(), geomagnetic, lastTimestamp))
        }
        listenerUpdateHandler.postDelayed({ notifyListener() }, LISTENER_UPDATE_DELAY_MILLIS)

    }

    companion object {
        private const val SENSOR_DELAY_MICROS = 20 * 1000
        private const val LISTENER_UPDATE_DELAY_MILLIS = 33L
    }

}
