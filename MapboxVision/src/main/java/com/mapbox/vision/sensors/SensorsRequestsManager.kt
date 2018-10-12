package com.mapbox.vision.sensors

import android.app.Activity
import android.app.Application
import android.content.Context.WINDOW_SERVICE
import android.hardware.Sensor
import android.hardware.Sensor.*
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
    private val screenOrientation = (application.getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val gameRotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)

    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private val rotations = FloatArray(3)
    private val orientation = FloatArray(3)

    private val userAcceleration = FloatArray(9)

    private val rotationMatrix = FloatArray(9)

    private val listenerUpdateHandler = Handler()

    private var lastTimestamp = 0L

    fun setSensorDataListener(sensorDataListener: SensorDataListener) {
        this.sensorDataListener = sensorDataListener
    }

    fun startDataRequesting() {
        if (accelerometerSensor == null || magneticSensor == null || rotationSensor == null) {
            return
        }
        sensorManager.registerListener(this, accelerometerSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, magneticSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, rotationSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, gameRotationSensor, SENSOR_DELAY_MICROS)
        sensorManager.registerListener(this, gravitySensor, SENSOR_DELAY_MICROS)

        if (sensorDataListener != null) {
            listenerUpdateHandler.postDelayed({ notifyListener() }, LISTENER_UPDATE_DELAY)
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

                    userAcceleration[0] = event.values[0]
                    userAcceleration[1] = event.values[1]
                    userAcceleration[2] = event.values[2]
                }
                TYPE_MAGNETIC_FIELD -> {
                    // mGeomagnetic = event.values;
                    geomagnetic[0] = event.values[0]
                    geomagnetic[1] = event.values[1]
                    geomagnetic[2] = event.values[2]
                }
                TYPE_ROTATION_VECTOR -> {
                    System.arraycopy(event.values, 0, rotations, 0, rotations.size)
                }
                TYPE_GAME_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                    SensorManager.getOrientation(rotationMatrix, orientation)

                    // Hack for current core impl
                    orientation[0] = orientation[0] * -1
                    orientation[2] = orientation[2] * -1

                }
                TYPE_GRAVITY -> {
                    gravity[0] = event.values[0]
                    gravity[1] = event.values[1]
                    gravity[2] = event.values[2]
                }
                else -> {
                    // Do nothing
                }
            }

        }
    }

    private fun notifyListener() {
        val listener = sensorDataListener ?: return

        synchronized(this) {

            var heading = Math.toDegrees(orientation[0].toDouble())
            if (heading < 0) {
                heading += 360
            }

            listener.onDeviceMotionDataReady(DeviceMotionData(rotations, orientation, screenOrientation,
                    gravity, userAcceleration, heading.toFloat()))

            listener.onHeadingDataReady(HeadingData(heading.toFloat(), geomagnetic, lastTimestamp))
        }
        listenerUpdateHandler.postDelayed({ notifyListener() }, LISTENER_UPDATE_DELAY)

    }

    companion object {
        private const val SENSOR_DELAY_MICROS = 20 * 1000 // 16ms
        private const val LISTENER_UPDATE_DELAY = 30L // 30milliseconds
    }

}