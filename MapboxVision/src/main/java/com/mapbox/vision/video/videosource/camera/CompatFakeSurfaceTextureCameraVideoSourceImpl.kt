package com.mapbox.vision.video.videosource.camera

import android.app.Application
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.util.Size
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener

@Suppress("DEPRECATION")
internal class CompatFakeSurfaceTextureCameraVideoSourceImpl(
        private val application: Application,
        val width: Int,
        val height: Int
) : VideoSource {

    private var camera: Camera? = null
    private val surfaceTexture = SurfaceTexture(10)

    private var workingPreviewSize: Size? = null

    private var videoSourceListener: VideoSourceListener? = null

    override fun getSourceWidth(): Int = width

    override fun getSourceHeight(): Int = height

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener
        prepareCamera(width, height)
        camera!!.setPreviewTexture(surfaceTexture)
//        startVideoRecording()
    }

    override fun detach() {
        videoSourceListener = null
//        stopVideoRecording()
    }
//
//    override fun startVideoRecording() {
//        try {
//            camera!!.setPreviewCallback { data, _ ->
//                videoSourceListener?.onNewFrame(convertNv21DataToRgb(data))
////                videoSourceListener?.onNewFrame(data)
//            }
//            camera!!.startPreview()
//        } catch (e: IOException) {
//            println("Exception $e")
//            throw RuntimeException("setPreviewTexture failed", e)
//        } catch (e: Exception) {
//            println("Exception $e")
//        }
//    }
//
//    override fun stopVideoRecording() {
//        camera!!.setPreviewCallback(null)
//        camera!!.stopPreview()
//    }

    private fun convertNv21DataToRgb(data: ByteArray): ByteArray {
        val start = System.currentTimeMillis()

        val rs = RenderScript.create(application)
        val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs))

        val yuvType = Type.Builder(rs, Element.U8(rs)).setX(data.size)
        val input = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)

        val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height)
        val out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)

        input.copyFrom(data)

        yuvToRgbIntrinsic.setInput(input)
        yuvToRgbIntrinsic.forEach(out)

        val output = ByteArray(out.bytesSize)
        out.copyTo(output)

        println("input-output size ${data.size} - ${output.size}")
        println("Total yuv2rgb time ${System.currentTimeMillis() - start}")

        return output
    }

//    override fun release() {
//        if (camera != null) {
//            println("releasing camera")
//            camera!!.stopPreview()
//            camera!!.release()
//            camera = null
//        }
//    }

    private fun prepareCamera(encWidth: Int, encHeight: Int) {
        if (camera != null) {
            throw RuntimeException("camera already initialized")
        }

        val info = Camera.CameraInfo()

        var camera: Camera? = null
        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                camera = Camera.open(i)
                break
            }
        }
        if (camera == null) {
            println("No front-facing camera found; opening default")
            camera = Camera.open()    // opens first back-facing camera
        }
        if (camera == null) {
            throw RuntimeException("Unable to open camera")
        }

        val params = camera.parameters

        workingPreviewSize = chooseOptimalCameraResolution(
                params.supportedVideoSizes.map { Size(it.width, it.height) },
                Size(width, height)
        )
        params.setPreviewSize(workingPreviewSize!!.width, workingPreviewSize!!.height)
        params.previewFormat = ImageFormat.NV21

        // leave the frame rate set to default
        camera.parameters = params

        this.camera = camera
    }
}
