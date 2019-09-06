package com.mapbox.vision.sync

import androidx.annotation.CallSuper
import com.mapbox.vision.sync.filemanager.SyncFileHandler
import com.mapbox.vision.sync.util.FeatureEnvironment

internal interface SyncManager<T : FeatureEnvironment> : SyncQueue.QueueListener {

    fun start()

    fun stop()

    abstract class SyncManagerBase<T : FeatureEnvironment>(
        syncQueue: SyncQueue.SyncQueueBase<T>,
        private val syncFileHandler: SyncFileHandler<T>,
        private val quotaBytes: Long
    ) : SyncManager<T> {

        init {
            syncQueue.queueListener = this
        }

        @CallSuper
        override fun onNewElement() {
            syncFileHandler.checkQuota(quotaBytes)
        }
    }
}
