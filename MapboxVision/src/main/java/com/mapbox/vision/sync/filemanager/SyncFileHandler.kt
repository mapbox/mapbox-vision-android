package com.mapbox.vision.sync.filemanager

import com.mapbox.vision.mobile.core.utils.extentions.directorySizeRecursive
import com.mapbox.vision.mobile.core.utils.extentions.sumByLong
import com.mapbox.vision.sync.util.FeatureEnvironment
import com.mapbox.vision.utils.FileUtils
import java.io.File

internal interface SyncFileHandler<T: FeatureEnvironment> {

    fun checkQuota(quotaBytes: Long): List<String>

    fun removePath(path: String)

    class Impl<T: FeatureEnvironment>(private val syncDirectoriesProvider: SyncDirectoriesProvider<T>) : SyncFileHandler<T> {

        override fun checkQuota(quotaBytes: Long): List<String> {
            val files = syncDirectoriesProvider.pathsAllCountries.map { File(it) }.filter { it.exists() }
                .map { it.listFiles().toList() }.flatten().sortedBy { it.name }

            val totalSize = files.sumByLong { it.directorySizeRecursive() }

            val removedPaths = ArrayList<String>()

            if (quotaBytes < totalSize) {
                var bytesToRemove = totalSize - quotaBytes

                files.forEach {
                    removedPaths.add(it.path)

                    bytesToRemove -= it.directorySizeRecursive()
                    it.deleteRecursively()

                    if (bytesToRemove <= 0) {
                        return@forEach
                    }
                }
            }
            return removedPaths
        }

        override fun removePath(path: String) {
            FileUtils.deleteDir(path)
        }
    }
}