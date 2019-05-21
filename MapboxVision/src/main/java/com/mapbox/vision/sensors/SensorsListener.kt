package com.mapbox.vision.sensors

import com.mapbox.vision.mobile.core.models.DeviceMotionData
import com.mapbox.vision.mobile.core.models.HeadingData

internal interface SensorsListener {

    fun onDeviceMotionData(deviceMotionData: DeviceMotionData)

    fun onHeadingData(headingData: HeadingData)
}
