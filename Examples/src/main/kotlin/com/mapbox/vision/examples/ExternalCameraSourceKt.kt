package com.mapbox.vision.examples

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.vision.VisionManager
import com.mapbox.vision.mobile.core.interfaces.VisionEventsListener
import com.mapbox.vision.mobile.core.models.AuthorizationStatus
import com.mapbox.vision.mobile.core.models.Camera
import com.mapbox.vision.mobile.core.models.Country
import com.mapbox.vision.mobile.core.models.FrameSegmentation
import com.mapbox.vision.mobile.core.models.classification.FrameSignClassifications
import com.mapbox.vision.mobile.core.models.detection.FrameDetections
import com.mapbox.vision.mobile.core.models.frame.ImageFormat
import com.mapbox.vision.mobile.core.models.frame.ImageSize
import com.mapbox.vision.mobile.core.models.position.VehicleState
import com.mapbox.vision.mobile.core.models.road.RoadDescription
import com.mapbox.vision.mobile.core.models.world.WorldDescription
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import kotlinx.android.synthetic.main.activity_main.*

/**
 * Example shows how Vision SDK can work with external video source. This can be some custom camera implementation or any
 * other source of frames - video, set of pictures, etc.
 */
class ExternalCameraSourceKt : AppCompatActivity() {

    companion object {
        // Video file that will be processed.
        private const val VIDEO_FILE = "video.mp4"
    }

    private var videoSourceListener: VideoSourceListener? = null

    private val handlerThread = HandlerThread("VideoDecode")

    // VideoSource that will play the file.
    private val customVideoSource = object : VideoSource {
        override fun attach(videoSourceListener: VideoSourceListener) {
            this@ExternalCameraSourceKt.videoSourceListener = videoSourceListener
            handlerThread.start()
            Handler(handlerThread.looper).post { startFileVideoSource() }
        }

        override fun detach() {
            videoSourceListener = null
            handlerThread.quitSafely()
        }
    }

    // VideoSourceListener handles events from Vision SDK.
    private val visionEventsListener = object : VisionEventsListener {

        override fun onAuthorizationStatusUpdated(authorizationStatus: AuthorizationStatus) {}

        override fun onFrameSegmentationUpdated(frameSegmentation: FrameSegmentation) {}

        override fun onFrameDetectionsUpdated(frameDetections: FrameDetections) {}

        override fun onFrameSignClassificationsUpdated(frameSignClassifications: FrameSignClassifications) {}

        override fun onRoadDescriptionUpdated(roadDescription: RoadDescription) {}

        override fun onWorldDescriptionUpdated(worldDescription: WorldDescription) {}

        override fun onVehicleStateUpdated(vehicleState: VehicleState) {}

        override fun onCameraUpdated(camera: Camera) {}

        override fun onCountryUpdated(country: Country) {}

        override fun onUpdateCompleted() {}
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onStart() {
        super.onStart()

        VisionManager.create(customVideoSource)
        VisionManager.start(visionEventsListener)
        VisionManager.setVideoSourceListener(vision_view)
    }

    override fun onStop() {
        super.onStop()

        VisionManager.stop()
        VisionManager.destroy()
    }

    /**
     * Decodes video source frame by frame and feeds frames to Vision SDK.
     */
    private fun startFileVideoSource() {
        // Use MediaMetadataRetriever to decode video.
        // It isn't the fastest approach to decode videos and you probably want some other method
        // if FPS is important (eg. MediaCodec).
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(File(getExternalFilesDir(""), VIDEO_FILE).absolutePath)

        // Get video frame size.
        val frameWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
        val frameHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        val imageSize = ImageSize(frameWidth, frameHeight)
        // ByteBuffer to hold RGBA bytes.
        val rgbaByteBuffer = ByteBuffer.wrap(ByteArray(frameWidth * frameHeight * 4))

        // Get duration.
        val duration = java.lang.Long.parseLong(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))

        try {
            // Get frames one by one with 1 second intervals.
            for (seconds in 0 until duration) {
                val bitmap = retriever
                    .getFrameAtTime(
                        TimeUnit.SECONDS.toMicros(seconds),
                        MediaMetadataRetriever.OPTION_CLOSEST
                    )
                    .copy(Bitmap.Config.ARGB_8888, false)

                bitmap.copyPixelsToBuffer(rgbaByteBuffer)

                videoSourceListener!!.onNewFrame(
                    rgbaByteBuffer.array(),
                    ImageFormat.RGBA,
                    imageSize
                )
                rgbaByteBuffer.clear()
            }
        } catch (e: RuntimeException) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: RuntimeException) {
                e.printStackTrace()
            }
        }
    }
}
