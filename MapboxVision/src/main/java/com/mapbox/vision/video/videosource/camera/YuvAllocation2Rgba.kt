package com.mapbox.vision.video.videosource.camera

import android.graphics.ImageFormat
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.view.Surface
import com.mapbox.vision.mobile.core.models.frame.ImageSize

internal class YuvAllocation2Rgba(
    renderScript: RenderScript,
    imageSize: ImageSize,
    private val rgbaListener: (ByteArray) -> Unit
) : Allocation.OnBufferAvailableListener {

    private val inputAllocation: Allocation
    private val outputAllocation: Allocation
    private val rgbaBytes = ByteArray(imageSize.imageWidth * imageSize.imageHeight * 4)
    private val script = ScriptIntrinsicYuvToRGB.create(renderScript, Element.RGBA_8888(renderScript))

    init {
        val yuvTypeBuilder = Type.Builder(renderScript, Element.YUV(renderScript))
        yuvTypeBuilder.setX(imageSize.imageWidth)
        yuvTypeBuilder.setY(imageSize.imageHeight)
        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888)
        inputAllocation = Allocation.createTyped(
            renderScript,
            yuvTypeBuilder.create(),
            Allocation.USAGE_IO_INPUT or Allocation.USAGE_SCRIPT
        )

        val rgbTypeBuilder = Type.Builder(renderScript, Element.RGBA_8888(renderScript))
        rgbTypeBuilder.setX(imageSize.imageWidth)
        rgbTypeBuilder.setY(imageSize.imageHeight)
        outputAllocation = Allocation.createTyped(
            renderScript,
            rgbTypeBuilder.create(),
            Allocation.USAGE_SCRIPT
        )

        script.setInput(inputAllocation)
        inputAllocation.setOnBufferAvailableListener(this)
    }

    fun getInputSurface(): Surface {
        return inputAllocation.surface
    }

    override fun onBufferAvailable(a: Allocation) {
        inputAllocation.ioReceive()
        script.forEach(outputAllocation)
        outputAllocation.copyTo(rgbaBytes)
        rgbaListener.invoke(rgbaBytes)
    }

    fun release() {
        inputAllocation.surface.release()
        inputAllocation.destroy()
        outputAllocation.destroy()
    }
}
