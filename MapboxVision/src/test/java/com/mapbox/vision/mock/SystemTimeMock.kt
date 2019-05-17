package com.mapbox.vision.mock

import com.mapbox.vision.mobile.core.utils.extentions.lazyUnsafe
import com.mapbox.vision.utils.system.SystemTime

class SystemTimeMock(var fixedTimestamp: Boolean = true) : SystemTime {

    private var offset = 0L

    private val fixedTimeMills by lazy(LazyThreadSafetyMode.NONE) { System.currentTimeMillis() }

    override fun currentTimeMillis(): Long =
        (if (fixedTimestamp) fixedTimeMills else System.currentTimeMillis()) + offset

    fun setOffsetMills(offset: Long) {
        this.offset = offset
    }
}