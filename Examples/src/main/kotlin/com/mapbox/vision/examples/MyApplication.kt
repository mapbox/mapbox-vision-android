package com.mapbox.vision.examples

import android.app.Application
import com.mapbox.vision.VisionManager

class MyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        VisionManager.init(this, getString(R.string.mapbox_access_token))
    }
}
