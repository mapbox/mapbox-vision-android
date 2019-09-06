package com.mapbox.vision.sync.visionpro

import android.app.Application
import com.mapbox.vision.models.video.VideoMetadata
import com.mapbox.vision.sync.SyncQueue
import com.mapbox.vision.sync.filemanager.SyncDirectoriesProvider
import com.mapbox.vision.sync.util.VideoMetadataJsonMapper
import com.mapbox.vision.sync.util.VisionProEnvironment
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

internal class VisionProQueue(
    application: Application,
    syncDirectoriesProvider: SyncDirectoriesProvider<VisionProEnvironment>,
    featureEnvironment: VisionProEnvironment,
    private val videoMetadataJsonMapper: VideoMetadataJsonMapper
) : SyncQueue.SyncQueueBase<VisionProEnvironment>(
    application,
    syncDirectoriesProvider,
    featureEnvironment
) {

    private val queue = ConcurrentLinkedQueue<Pair<String, VideoMetadata>>()

    override fun syncSessionDir(path: String) {
        val filesList = File(path).listFiles()
        val videoFilesList = filesList.filter { it.name.endsWith(".mp4") }.filter { it.exists() }

        val localList = ArrayList<Pair<String, VideoMetadata>>()

        videoFilesList.forEach { videoFile ->
            val jsonName = VisionProUtil.provideJsonNameByVideoName(videoFile.name)
            val jsonFile = filesList.find { it.name == jsonName }
            jsonFile?.let { jsonFileName ->
                videoMetadataJsonMapper.deserialize(jsonFileName.absolutePath)
                    ?.let { videoMetadata ->
                        localList.add(videoFile.absolutePath to videoMetadata)
                    }
            }
        }

        queue.addAll(localList)
    }

    fun nextForSync(): Pair<String, VideoMetadata>? = queue.peek()

    fun removeFromQueue(element: Pair<String, VideoMetadata>) {
        queue.remove(element)

        val videoFile = File(element.first)
        val jsonFile = videoFile.parentFile.listFiles().find { it.name == VisionProUtil.provideJsonNameByVideoName(videoFile.name) }

        videoFile.delete()
        jsonFile?.delete()
    }

    override fun resetQueue() {
        queue.clear()
    }
}