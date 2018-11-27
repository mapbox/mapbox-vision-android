package com.mapbox.vision.visionevents.events

import android.graphics.Bitmap

/**
 * Represents image with [format] and size [width]-[height].
 * Allows to retrieve bytes of image or bitmap.
 *
 * IMPORTANT : do not cache or store images retrieved from SDK. Data that it holds can be invalidated.
 * Instead, if you need the data, call [getImageBitmap] or [getImageBytes] and use it.
 */
data class Image(
        val format: Format,
        val width: Int,
        val height: Int,
        val identifier: Long
) {

    internal var imageSource: ImageSource? = null

    /**
     * Get bytes of image.
     *
     * @return null if image was invalidated
     */
    fun getImageBytes(): ByteArray? = imageSource?.getImageBytes(this)

    /**
     * Get bitmap of image.
     *
     * @return null if image was invalidated or Bitmap can not be created from byte array (eg., segmentationMaskImage)
     */
    fun getImageBitmap(): Bitmap? = imageSource?.getImageBitmap(this)

    internal interface ImageSource {

        fun getImageBytes(sourceImage: Image): ByteArray?

        fun getImageBitmap(sourceImage: Image): Bitmap?
    }

    enum class Format(val channelsNum: Int) {
        Unknown(0),
        RGBA(4),
        BGRA(4),
        RGB(3),
        BGR(3),
        GRAYSCALE8(1)
    }

}
