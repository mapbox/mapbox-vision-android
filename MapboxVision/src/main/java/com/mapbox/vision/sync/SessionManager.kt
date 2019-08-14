package com.mapbox.vision.sync

import android.app.Application
import com.mapbox.android.telemetry.MapboxTelemetry
import com.mapbox.vision.BuildConfig
import com.mapbox.vision.mobile.core.NativeVisionManager
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.VideoClip
import com.mapbox.vision.mobile.core.telemetry.TelemetryImageSaver
import com.mapbox.vision.mobile.core.utils.extentions.TAG_CLASS
import com.mapbox.vision.sync.metagenerator.TelemetryMetaGenerator
import com.mapbox.vision.sync.session.RecordingSessionWriter
import com.mapbox.vision.sync.session.RotatedBuffersSessionWriter
import com.mapbox.vision.sync.session.SessionWriter
import com.mapbox.vision.sync.syncmanager.TelemetrySyncManager
import com.mapbox.vision.sync.telemetry.TelemetryImageSaverImpl
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
        private val telemetryImageSaver: TelemetryImageSaver
    ) : SessionManager {

        companion object {
            private const val CACHE_DIR = "Cache"
            private const val VIDEO_BUFFERS_DIR = "Buffers"
            private const val DIR_TELEMETRY = "Telemetry"
        }

        private lateinit var sessionWriter: SessionWriter
        private var currentCountry = Country.Unknown

        private val videoProcessor: VideoProcessor

        private val telemetryEnvironment = TelemetryEnvironment
        private val telemetryMetaGenerator = TelemetryMetaGenerator()

        private val telemetrySyncManager: TelemetrySyncManager

        private var isRecording = false

        init {
            configMapboxTelemetry()

            videoProcessor = VideoProcessor.Impl()

            telemetrySyncManager = TelemetrySyncManager(
                mapboxTelemetry = mapboxTelemetry,
                context = application
            )
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

        override fun setCountry(country: Country) {

            if (currentCountry == country) {
                return
            }

            currentCountry = country

            if (isRecording) {
                return
            }

            when {
                currentCountry == Country.Unknown && country != Country.Unknown -> {
                    configMapboxTelemetry()
                }

                currentCountry != Country.Unknown && country != Country.Unknown -> {
                    telemetrySyncManager.stop()
                    videoProcessor.detach()
                    sessionWriter.stop()

                    configMapboxTelemetry(currentCountry)

                    telemetrySyncManager.start()
                    checkCountryTelemetryDir()

                    videoProcessor.attach(videoProcessorListener)
                    sessionWriter.start()
                }

                currentCountry != Country.Unknown && country == Country.Unknown -> {
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
            val telemetryDir = getCountryTelemetryDir()

            telemetryDir?.let { dir ->
                File(dir).listFiles()?.forEach {
                    if (it.list().isNullOrEmpty()) {
                        it?.delete()
                    } else {
                        telemetrySyncManager.syncSessionDir(it.absolutePath)
                    }
                }
            }
        }

        private fun createRotatedBuffersSessionWriter() = RotatedBuffersSessionWriter(
            buffers = RotatedBuffers(getVideoBuffersDir()),
            nativeVisionManager = nativeVisionManager,
            rootCacheDir = getCacheDir(),
            videoRecorder = videoRecorder,
            telemetryImageSaverImpl = telemetryImageSaver as TelemetryImageSaverImpl,
            sessionWriterListener = sessionWriterListener
        )

        private fun createRecordingSessionWriter(path: String) = RecordingSessionWriter(
            buffers = RotatedBuffers(buffersDir = "$path/", totalBuffersNumber = 1),
            nativeVisionManager = nativeVisionManager,
            sessionDir = "$path/",
            videoRecorder = videoRecorder
        )

        private fun configMapboxTelemetry(country: Country? = null) {
            mapboxTelemetry.updateDebugLoggingEnabled(BuildConfig.DEBUG)

            country?.let {
                mapboxTelemetry.setBaseUrl(telemetryEnvironment.getHost(it))
            }
        }

        private val sessionWriterListener = object : SessionWriterListener {
            override fun onSessionStop(
                clips: Array<VideoClip>,
                videoPath: String,
                outputPath: String,
                sessionStartMillis: Long
            ) {
                if (currentCountry != Country.Unknown) {

                    val sessionCountryDir = getCountryTelemetryDir()

                    sessionCountryDir?.let { dir ->
                        val sessionCountryDirWithTimeout = "$dir/$sessionStartMillis/"

                        moveFromCacheDirToCountryDir(outputPath, sessionCountryDirWithTimeout)
                        videoProcessor.splitVideoClips(
                            clips,
                            videoPath,
                            sessionCountryDirWithTimeout,
                            sessionStartMillis
                        )
                    }
                }
            }
        }

        private val videoProcessorListener = object : VideoProcessorListener {
            override fun onVideoClipsReady(
                videoClips: HashMap<String, VideoClip>,
                videoDir: String,
                sessionStartMillis: Long
            ) {
                if (currentCountry != Country.Unknown) {
                    telemetryMetaGenerator.generateMeta(videoClips, videoDir, sessionStartMillis)
                    telemetrySyncManager.syncSessionDir(videoDir)
                }
            }
        }

        private fun moveFromCacheDirToCountryDir(fromDirPath: String, toDirPath: String) {
            FileUtils.moveFilesFromOneDirToAnother(fromDirPath, toDirPath)
        }

        private fun getCountryTelemetryDir(): String? {
            val countryDir = telemetryEnvironment.getBasePath(currentCountry)

            return if (countryDir != null) {
                FileUtils.getAppRelativeDir(application, "$DIR_TELEMETRY/$countryDir/")
            } else {
                null
            }
        }

        private fun getVideoBuffersDir() = FileUtils.getAppRelativeDir(application, VIDEO_BUFFERS_DIR)

        private fun getCacheDir() = FileUtils.getAppRelativeDir(application, CACHE_DIR)
    }
}
