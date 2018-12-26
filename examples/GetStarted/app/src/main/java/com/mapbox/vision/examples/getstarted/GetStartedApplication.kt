package com.mapbox.vision.examples.getstarted

import android.support.multidex.MultiDexApplication
import com.mapbox.vision.VisionManager

class GetStartedApplication : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        // Init Vision manager instance with Mapbox token
        VisionManager.init(this, getString(R.string.mapbox_access_token))
    }
}