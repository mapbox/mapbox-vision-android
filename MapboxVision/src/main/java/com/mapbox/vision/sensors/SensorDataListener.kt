package com.mapbox.vision.sensors

import com.mapbox.vision.models.DeviceMotionData
import com.mapbox.vision.models.HeadingData

internal interface SensorDataListener {

    fun onDeviceMotionDataReady(deviceMotionData: DeviceMotionData)

    fun onHeadingDataReady(headingData: HeadingData)
}