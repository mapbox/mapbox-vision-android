package com.mapbox.vision.video.videosource.camera

import android.graphics.ImageFormat
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import android.util.Size
import android.view.Surface

internal class YuvAllocation2Rgb(
        renderScript: RenderScript,
        previewSize: Size,
        private val rgbListener: (ByteArray) -> Unit
) : Allocation.OnBufferAvailableListener {

    private val inputAllocation: Allocation
    private val outputAllocation: Allocation
    private val rgbBytes = ByteArray(previewSize.width * previewSize.height * 4)
    private val script = ScriptIntrinsicYuvToRGB.create(renderScript, Element.RGBA_8888(renderScript))

    init {
        val yuvTypeBuilder = Type.Builder(renderScript, Element.YUV(renderScript))
        yuvTypeBuilder.setX(previewSize.width)
        yuvTypeBuilder.setY(previewSize.height)
        yuvTypeBuilder.setYuvFormat(ImageFormat.YUV_420_888)
        inputAllocation = Allocation.createTyped(
                renderScript,
                yuvTypeBuilder.create(),
                Allocation.USAGE_IO_INPUT or Allocation.USAGE_SCRIPT
        )

        val rgbTypeBuilder = Type.Builder(renderScript, Element.RGBA_8888(renderScript))
        rgbTypeBuilder.setX(previewSize.width)
        rgbTypeBuilder.setY(previewSize.height)
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
        outputAllocation.copyTo(rgbBytes)
        rgbListener.invoke(rgbBytes)
    }

    fun release() {
        inputAllocation.surface.release()
        inputAllocation.destroy()
        outputAllocation.destroy()
    }
}
