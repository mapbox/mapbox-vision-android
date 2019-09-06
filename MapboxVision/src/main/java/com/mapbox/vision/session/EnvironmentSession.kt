package com.mapbox.vision.session

import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.models.video.VideoStartStop
import com.mapbox.vision.models.video.VideoTelemetry
import com.mapbox.vision.models.video.VideoVisionPro
import com.mapbox.vision.models.video.mapToVideoClipStartStop
import com.mapbox.vision.sync.MetaGenerator
import com.mapbox.vision.sync.SyncQueue
import com.mapbox.vision.sync.filemanager.SyncDirectoriesProvider
import com.mapbox.vision.sync.util.TelemetryEnvironment
import com.mapbox.vision.sync.util.VisionProEnvironment
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.system.Time
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videoprocessor.VideoProcessorListener
import java.io.File

internal interface EnvironmentSession<T : EnvironmentSession.Environment> {

    fun onSessionStop(
        environmentData: T,
        videoProcessor: VideoProcessor,
        videoPath: String,
        coreSessionStartMillis: Long,
        country: Country
    )

    class Telemetry(
        private val syncDirectoriesProvider: SyncDirectoriesProvider<TelemetryEnvironment>,
        private val metaGenerator: MetaGenerator,
        private val syncQueue: SyncQueue<TelemetryEnvironment>
    ) : EnvironmentSession<Telemetry.EnvironmentData> {
        override fun onSessionStop(
            environmentData: EnvironmentData,
            videoProcessor: VideoProcessor,
            videoPath: String,
            coreSessionStartMillis: Long,
            country: Country
        ) {
            if (country == Country.Unknown) {
                FileUtils.deleteDir(environmentData.cachedTelemetryPath)
                return
            }

            val fileManager = syncDirectoriesProvider
            val outputDir = "${(fileManager.getPathForCountry(country) ?: return)}/${File(
                environmentData.cachedTelemetryPath
            ).name ?: return}"

            FileUtils.moveFiles(environmentData.cachedTelemetryPath, outputDir)

            videoProcessor.splitVideoClips(
                clips = environmentData.video.map { it.mapToVideoClipStartStop() }.toTypedArray(),
                videoPath = videoPath,
                outputPath = outputDir,
                coreSessionStartMillis = coreSessionStartMillis,
                onVideoClipReady = null,
                onVideoClipsReady = object : VideoProcessorListener.MultipleClips {
                    override fun onVideoClipsReady(
                        videoClips: HashMap<String, VideoStartStop>,
                        videoDir: String
                    ) {
                        metaGenerator.generateMeta(
                            videoClips,
                            videoDir
                        )
                        syncQueue.syncSessionDir(videoDir)
                    }
                }
            )
        }

        internal data class EnvironmentData(
            val cachedTelemetryPath: String,
            val video: Array<VideoTelemetry>
        ) : Environment
    }

    class VisionPro(
        private val syncDirectoriesProvider: SyncDirectoriesProvider<VisionProEnvironment>,
        private val metaGenerator: MetaGenerator,
        private val syncQueue: SyncQueue<VisionProEnvironment>,
        private val time: Time = Time.SystemImpl
    ) : EnvironmentSession<VisionPro.EnvironmentData> {
        override fun onSessionStop(
            environmentData: EnvironmentData,
            videoProcessor: VideoProcessor,
            videoPath: String,
            coreSessionStartMillis: Long,
            country: Country
        ) {
            if (country == Country.Unknown) {
                return
            }

            val fileManager = syncDirectoriesProvider
            val outputDir = "${fileManager.getPathForCountry(country) ?: return}/${time.millis()}"

            val crossData = environmentData.video.map { it to it.mapToVideoClipStartStop() }

            videoProcessor.splitVideoClips(
                clips = crossData.map { it.second }.toTypedArray(),
                videoPath = videoPath,
                outputPath = outputDir,
                coreSessionStartMillis = coreSessionStartMillis,
                onVideoClipReady = object : VideoProcessorListener.SingleClip {
                    override fun onVideoClipReady(clipPath: String, clip: VideoStartStop) {

                        val metadata =
                            crossData.find { it.second == clip }
                                ?.first
                                ?.metadata
                                ?: return

                        metaGenerator.generateMeta(
                            clipPath, clip, metadata
                        )
                    }
                },
                onVideoClipsReady = object : VideoProcessorListener.MultipleClips {
                    override fun onVideoClipsReady(
                        videoClips: HashMap<String, VideoStartStop>,
                        videoDir: String
                    ) {
                        syncQueue.syncSessionDir(videoDir)
                    }
                }
            )
        }

        data class EnvironmentData(val video: Array<VideoVisionPro>) : Environment
    }

    interface Environment
}
