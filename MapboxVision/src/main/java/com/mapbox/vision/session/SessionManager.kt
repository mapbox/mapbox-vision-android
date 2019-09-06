package com.mapbox.vision.session

import android.app.Application
import com.google.gson.Gson
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.telemetry.TelemetryImageSaver
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.mobile.core.utils.extentions.lazyUnsafe
import com.mapbox.vision.models.video.VideoCombined
import com.mapbox.vision.sync.SyncClient
import com.mapbox.vision.sync.filemanager.SyncFileHandler
import com.mapbox.vision.sync.filemanager.SyncDirectoriesProvider
import com.mapbox.vision.sync.telemetry.TelemetryImageSaverImpl
import com.mapbox.vision.sync.telemetry.TelemetryMetaGenerator
import com.mapbox.vision.sync.telemetry.TelemetryQueue
import com.mapbox.vision.sync.telemetry.TelemetrySyncManager
import com.mapbox.vision.sync.util.TelemetryEnvironment
import com.mapbox.vision.sync.util.VideoMetadataJsonMapper
import com.mapbox.vision.sync.util.VisionProEnvironment
import com.mapbox.vision.sync.visionpro.VisionProMetaGenerator
import com.mapbox.vision.sync.visionpro.VisionProQueue
import com.mapbox.vision.sync.visionpro.VisionProSyncManager
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.file.RotatedBuffers
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import okhttp3.OkHttpClient

internal interface SessionManager {

    fun start()

    fun stop()

    fun setCountry(country: Country)

    fun startRecording(path: String)

    fun stopRecording()

    class Impl(
        private val application: Application,
        private val nativeVisionManager: NativeVisionManager,
        private val okHttpClient: OkHttpClient,
        private val videoRecorder: VideoRecorder,
        mapboxTelemetry: MapboxTelemetry,
        private val telemetryImageSaver: TelemetryImageSaver,
        gson: Gson
    ) : SessionManager {

        companion object {
            private const val CACHE_DIR = "Cache"
            private const val VIDEO_BUFFERS_DIR = "Buffers"
        }

        private lateinit var sessionWriter: SessionWriter
        private var currentCountry = Country.Unknown
        private var isRecording = false
        private val videoProcessor: VideoProcessor

        private val syncManagers by lazyUnsafe {
            arrayOf(telemetrySyncManager, visionProSyncManager)
        }

        private val countryChangeListeners = ArrayList<EnvironmentSettings.CountryChange>()

        private val telemetrySyncManager: TelemetrySyncManager
        private val visionProSyncManager: VisionProSyncManager

        private val telemetrySession: EnvironmentSession<EnvironmentSession.Telemetry.EnvironmentData>
        private val visionProSession: EnvironmentSession<EnvironmentSession.VisionPro.EnvironmentData>

        init {
            videoProcessor = VideoProcessor.Impl()

            val videoMetadataJsonMapper = VideoMetadataJsonMapper.Impl(gson)

            val telemetrySyncFileProvider = SyncDirectoriesProvider.Impl(
                application,
                TelemetryEnvironment
            )
            val telemetryQueue =  TelemetryQueue(application, telemetrySyncFileProvider, TelemetryEnvironment)


            val telemetryClient = SyncClient.Telemetry(mapboxTelemetry, TelemetryEnvironment)
            telemetrySyncManager = TelemetrySyncManager(
                syncQueue = telemetryQueue,
                syncFileHandler = SyncFileHandler.Impl(
                    telemetrySyncFileProvider
                ),
                syncClient = telemetryClient
            )

            val visionProSyncFileProvider = SyncDirectoriesProvider.Impl(
                application,
                VisionProEnvironment
            )
            val visionProQueue =  VisionProQueue(application, visionProSyncFileProvider, VisionProEnvironment, videoMetadataJsonMapper)
            val visionProClient = SyncClient.VisionPro(okHttpClient, VisionProEnvironment)
            visionProSyncManager = VisionProSyncManager(
                syncClient = visionProClient,
                syncFileHandler = SyncFileHandler.Impl(visionProSyncFileProvider),
                syncQueue = visionProQueue,
                gson = gson
            )

            telemetrySession = EnvironmentSession.Telemetry(telemetrySyncFileProvider, TelemetryMetaGenerator(), telemetryQueue)
            visionProSession = EnvironmentSession.VisionPro(visionProSyncFileProvider, VisionProMetaGenerator(videoMetadataJsonMapper), visionProQueue)

            countryChangeListeners.addAll(listOf(telemetryQueue, visionProQueue, visionProClient, telemetryClient))
        }

        override fun start() {
            sessionWriter = createRotatedBuffersSessionWriter()
            sessionWriter.start()
            videoProcessor.attach()

            syncManagers.forEach { it.start() }
        }

        override fun stop() {
            syncManagers.forEach { it.stop() }
            videoProcessor.detach()
            sessionWriter.stop()
        }

        override fun setCountry(country: Country) {
            if (currentCountry == country) {
                return
            }

            if (isRecording) {
                currentCountry = country
                return
            }

            when {
                currentCountry == Country.Unknown && country != Country.Unknown -> {
                    currentCountry = country
                }

                currentCountry != Country.Unknown && country != Country.Unknown -> {
                    videoProcessor.detach()
                    sessionWriter.stop()

                    currentCountry = country

                    videoProcessor.attach()
                    sessionWriter.start()
                }

                currentCountry != Country.Unknown && country == Country.Unknown -> {
                    currentCountry = country
                    telemetrySyncManager.stop()
                }
            }
            
            countryChangeListeners.forEach { it.newCountry(country) }
        }

        override fun startRecording(path: String) {
            if (isRecording) {
                VisionLogger.e(TAG_CLASS, "Recording was already started.")
                return
            }
            isRecording = true

            sessionWriter.stop()
            sessionWriter = createRecordingSessionWriter(path)
            sessionWriter.start()

            syncManagers.forEach { it.stop() }
            videoProcessor.detach()
        }

        override fun stopRecording() {
            if (!isRecording) {
                VisionLogger.e(TAG_CLASS, "Recording was not started.")
                return
            }
            isRecording = false

            sessionWriter.stop()
            sessionWriter = createRotatedBuffersSessionWriter()
            sessionWriter.start()

            videoProcessor.attach()
            syncManagers.forEach { it.start() }
        }

        private fun createRotatedBuffersSessionWriter() =
            RotatedBuffersSessionWriter(
                buffers = RotatedBuffers(getVideoBuffersDir()),
                nativeVisionManager = nativeVisionManager,
                rootCacheDir = getCacheDir(),
                videoRecorder = videoRecorder,
                telemetryImageSaverImpl = telemetryImageSaver as TelemetryImageSaverImpl,
                sessionWriterListener = sessionWriterListener
            )

        private fun createRecordingSessionWriter(path: String) =
            RecordingSessionWriter(
                buffers = RotatedBuffers(buffersDir = "$path/", totalBuffersNumber = 1),
                nativeVisionManager = nativeVisionManager,
                sessionDir = "$path/",
                videoRecorder = videoRecorder
            )

        private val sessionWriterListener = object :
            SessionWriterListener {
            override fun onSessionStop(
                clips: VideoCombined,
                videoPath: String,
                cachedTelemetryPath: String,
                coreSessionStartMillis: Long
            ) {
                clips.telemetries?.let {
                    telemetrySession.onSessionStop(
                        EnvironmentSession.Telemetry.EnvironmentData(
                            cachedTelemetryPath,
                            it
                        ),
                        videoProcessor,
                        videoPath,
                        coreSessionStartMillis,
                        currentCountry
                    )
                }
                clips.visionPros?.let {
                    visionProSession.onSessionStop(
                        EnvironmentSession.VisionPro.EnvironmentData(
                            it
                        ),
                        videoProcessor,
                        videoPath,
                        coreSessionStartMillis,
                        currentCountry
                    )
                }
            }
        }

        private fun getVideoBuffersDir() = FileUtils.getAppRelativeDir(
            application,
            VIDEO_BUFFERS_DIR
        )

        private fun getCacheDir() = FileUtils.getAppRelativeDir(
            application,
            CACHE_DIR
        )
    }
}
