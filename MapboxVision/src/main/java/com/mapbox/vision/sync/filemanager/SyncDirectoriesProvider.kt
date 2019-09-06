package com.mapbox.vision.sync.filemanager

import android.app.Application
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.sync.util.FeatureEnvironment
import com.mapbox.vision.utils.FileUtils

internal interface SyncDirectoriesProvider<T: FeatureEnvironment> {

    val pathsAllCountries: Array<String>

    fun getPathForCountry(country: Country): String?

    class Impl<T : FeatureEnvironment>(
        private val application: Application,
        private val featureEnvironment: T
    ) : SyncDirectoriesProvider<T> {

        override val pathsAllCountries: Array<String> by lazy {
            val set = HashSet<String?>()
            enumValues<Country>().forEach {
                set.add(getPathForCountry(it))
            }
            return@lazy set.filterNotNull().toTypedArray()
        }

        override fun getPathForCountry(country: Country): String? {
            val baseDir = featureEnvironment.getCountryDirName(country) ?: return null
            return appRelatedDir("${featureEnvironment.getEnvironmentDirName()}/$baseDir")
        }

        private fun appRelatedDir(dir: String): String =
            FileUtils.getAppRelativeDir(application, dir)
    }
}
