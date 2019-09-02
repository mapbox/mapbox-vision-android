package com.mapbox.vision.session

import android.app.Application
import com.google.gson.Gson
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.mobile.core.telemetry.TelemetryImageSaver
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.mobile.core.utils.extentions.ifNonNull
import com.mapbox.vision.mobile.core.utils.extentions.lazyUnsafe
import com.mapbox.vision.models.videoclip.VideoClipStartStop
import com.mapbox.vision.models.videoclip.VideoClipsCombined
import com.mapbox.vision.models.videoclip.mapToVideoClipStartStop
import com.mapbox.vision.sync.SessionWriterListener
import com.mapbox.vision.sync.telemetry.TelemetryImageSaverImpl
import com.mapbox.vision.sync.telemetry.TelemetryMetaGenerator
import com.mapbox.vision.sync.telemetry.TelemetrySyncManager
import com.mapbox.vision.sync.util.TelemetryEnvironment
import com.mapbox.vision.sync.visionpro.VisionProMetaGenerator
import com.mapbox.vision.sync.visionpro.VisionProSyncManager
import com.mapbox.vision.utils.FileUtils
import com.mapbox.vision.utils.VisionLogger
import com.mapbox.vision.utils.file.RotatedBuffers
import com.mapbox.vision.video.videoprocessor.VideoProcessor
import com.mapbox.vision.video.videoprocessor.VideoProcessorListener
import com.mapbox.vision.video.videosource.camera.VideoRecorder
import java.io.File

internal interface SessionManager {

    fun start()

    fun stop()

    fun setCountry(country: Country)

    fun startRecording(path: String)

    fun stopRecording()

    class Impl(
        private val application: Application,
        private val nativeVisionManager: NativeVisionManager,
        private val videoRecorder: VideoRecorder,
        private val mapboxTelemetry: MapboxTelemetry,
        private val telemetryImageSaver: TelemetryImageSaver,
        private val gson: Gson
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

        private val telemetrySyncManager: TelemetrySyncManager
        private val visionProSyncManager: VisionProSyncManager

        init {
            mapboxTelemetry.updateDebugLoggingEnabled(BuildConfig.DEBUG)

            videoProcessor = VideoProcessor.Impl()

            telemetrySyncManager = TelemetrySyncManager(
                application = application,
                mapboxTelemetry = mapboxTelemetry,
                telemetryMetaGenerator = TelemetryMetaGenerator(),
                telemetryEnvironment = TelemetryEnvironment,
                currentCountry = currentCountry
            )
            visionProSyncManager = VisionProSyncManager(gson,  VisionProMetaGenerator(gson))
        }

        override fun start() {
            sessionWriter = createRotatedBuffersSessionWriter()
            sessionWriter.start()
            videoProcessor.attach(videoProcessorListener)

            telemetrySyncManager.start()
            checkCountryTelemetryDir()
        }

        override fun stop() {
            telemetrySyncManager.stop()
            videoProcessor.detach()
            sessionWriter.stop()
        }

        override fun setCountry(newCountry: Country) {

            if (currentCountry == newCountry) {
                return
            }

            if (isRecording) {
                currentCountry = newCountry
                return
            }

            when {
                currentCountry == Country.Unknown && newCountry != Country.Unknown -> {
                    currentCountry = newCountry
                    configMapboxTelemetry()
                    checkCountryTelemetryDir()
                }

                currentCountry != Country.Unknown && newCountry != Country.Unknown -> {
                    telemetrySyncManager.stop()
                    videoProcessor.detach()
                    sessionWriter.stop()

                    currentCountry = newCountry

                    configMapboxTelemetry()

                    telemetrySyncManager.start()
                    checkCountryTelemetryDir()

                    videoProcessor.attach(videoProcessorListener)
                    sessionWriter.start()
                }

                currentCountry != Country.Unknown && newCountry == Country.Unknown -> {
                    currentCountry = newCountry
                    telemetrySyncManager.stop()
                }
            }
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

            telemetrySyncManager.stop()
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

            videoProcessor.attach(videoProcessorListener)
            telemetrySyncManager.start()
            checkCountryTelemetryDir()
        }

        private fun checkCountryTelemetryDir() {
            val telemetryDir = getCurrentCountryTelemetryPath()

            telemetryDir?.let { dir ->
                File(dir).listFiles()?.forEach {
                    if (it.list().isNullOrEmpty()) {
                        it?.delete()
                    } else {
                        syncSessionDir(it.absolutePath)
                    }
                }
            }
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

        private val sessionWriterListener = object : SessionWriterListener {
            override fun onSessionStop(
                clips: VideoClipsCombined,
                videoPath: String,
                cachedTelemetryPath: String,
                coreSessionStartMillis: Long
            ) {
                if (currentCountry != Country.Unknown) {
                    ifNonNull(clips.telemetryClips, telemetrySyncManager.getCurrentCountryTelemetryPath()){ telemetryClips, currentCountryTelemetryPath ->
                        val newTelemetryPath = telemetrySyncManager.generateSessionPath(
                            cachedTelemetryPath,
                            currentCountryTelemetryPath
                        )
                        videoProcessor.splitVideoClips(
                            clips = telemetryClips.map { it.mapToVideoClipStartStop() }.toTypedArray(),
                            videoPath = videoPath,
                            outputPath = newTelemetryPath,
                            coreSessionStartMillis = coreSessionStartMillis,
                            onVideoClipReady = null,
                            onVideoClipsReady = object : VideoProcessorListener.MultipleClips {
                                override fun onVideoClipsReady(
                                    videoClips: HashMap<String, VideoClipStartStop>,
                                    videoDir: String
                                ) {
                                    telemetrySyncManager.syncSessionDir()
                                }
                            }
                        )
                    }
                    clips.visionProClips?.let {

                    }

                    val currentCountryTelemetryPath = getCurrentCountryTelemetryPath() ?: return
                    val newTelemetryPath = generateSessionPath(
                        cachedTelemetryPath,
                        currentCountryTelemetryPath
                    )
                    FileUtils.moveFiles(cachedTelemetryPath, newTelemetryPath)
                    videoProcessor.splitVideoClips(
                        clips = clips,
                        videoPath = videoPath,
                        outputPath = newTelemetryPath,
                        coreSessionStartMillis = coreSessionStartMillis
                    )
                } else {
                    // delete session because country is unknown
                    FileUtils.deleteDir(cachedTelemetryPath)
                }
            }
        }

        private val videoProcessorListener = object : VideoProcessorListener.MultipleClips {
            override fun onVideoClipsReady(
                videoClips: HashMap<String, VideoClipStartStop>,
                videoDir: String
            ) {
                if (currentCountry != Country.Unknown) {
                     .generateMeta(
                        videoClipMap = videoClips,
                        saveDirPath = videoDir
                    )
                    syncSessionDir(videoDir)

                    // TODO not right logic should be separate
                    visionProMetaGenerator.generateMeta(videoClips, videoDir)
                    syncSessionDir(videoDir)
                }
            }
        }

        private fun syncSessionDir(videoDir: String) {
            mapboxTelemetry.updateDebugLoggingEnabled(BuildConfig.DEBUG)
            telemetrySyncManager.syncSessionDir(videoDir)
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
