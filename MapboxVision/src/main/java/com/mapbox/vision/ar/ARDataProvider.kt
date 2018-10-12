package com.mapbox.vision.ar

/**
 * Interface for AR relative data provider.
 * Used for communication between VisionSDK and VisionAR frameworks.
 */
interface ARDataProvider {

    /**
     * Get camera params for AR.
     *
     * @return six camera parameters [
     *      m_verticalFOV, - vertical field of view, in radians
     *      m_aspectRatio, - aspect ratio
     *      m_roll,        - roll, in radians
     *      m_pitch,       - pitch, in radians
     *      m_yaw,         - yaw, in radians
     *      m_height       - camera height, in meters
     *      ]
     */
    fun getCameraParams(): FloatArray?

    /**
     * Get AR cubic spline of route.
     *
     * @return AR cubic spline of route
     */
    fun getARRouteData(): DoubleArray?
}
