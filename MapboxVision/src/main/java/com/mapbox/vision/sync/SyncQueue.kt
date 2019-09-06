package com.mapbox.vision.sync

import android.app.Application
import androidx.annotation.CallSuper
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.session.EnvironmentSettings
import com.mapbox.vision.sync.filemanager.SyncDirectoriesProvider
import com.mapbox.vision.sync.util.FeatureEnvironment
import java.io.File

internal interface SyncQueue<T : FeatureEnvironment> : EnvironmentSettings.CountryChange {

    val syncDirectoriesProvider: SyncDirectoriesProvider<T>

    var queueListener: QueueListener?

    fun syncSessionDir(path: String)

    abstract class SyncQueueBase<T : FeatureEnvironment>(
        protected val application: Application,
        override val syncDirectoriesProvider: SyncDirectoriesProvider<T>,
        private val featureEnvironment: FeatureEnvironment
    ) : SyncQueue<T> {
        override var queueListener: QueueListener? = null

        protected var country: Country = Country.Unknown

        init {
            checkCountryTelemetryDir()
        }

        @CallSuper
        override fun newCountry(country: Country) {
            this.country = country
            resetQueue()
            checkCountryTelemetryDir()
        }

        protected abstract fun resetQueue()

        private fun checkCountryTelemetryDir() {
            val syncDir = featureEnvironment.getCountryDirName(country) ?: return

            File(syncDir).listFiles()?.forEach {
                if (it.list().isNullOrEmpty()) {
                    it?.delete()
                } else {
                    syncSessionDir(it.absolutePath)
                }
            }
        }
    }

    interface QueueListener {
        fun onNewElement()
    }
}