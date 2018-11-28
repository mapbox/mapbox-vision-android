package com.mapbox.vision.sensors

import com.mapbox.vision.mobile.models.DeviceMotionData
import com.mapbox.vision.mobile.models.HeadingData

internal interface SensorDataListener {

    fun onDeviceMotionData(deviceMotionData: DeviceMotionData)

    fun onHeadingData(headingData: HeadingData)
}
