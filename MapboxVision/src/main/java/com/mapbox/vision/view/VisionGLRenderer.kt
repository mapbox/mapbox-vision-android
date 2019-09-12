package com.mapbox.vision.view

import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.mapbox.vision.gl.GLDrawRect
import com.mapbox.vision.gl.GLDrawTextureRGB
import com.mapbox.vision.gl.GLReleasable
import com.mapbox.vision.mobile.core.models.detection.Detection
import com.mapbox.vision.utils.MyGLUtils
import com.mapbox.vision.utils.VisionLogger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.collections.ArrayList

class VisionGLRenderer (
        private var mTextureWidth : Int,
        private var mTextureHeight : Int) : GLSurfaceView.Renderer, GLReleasable {

    private val TAG = "VisionGLRenderer"
    private lateinit var mRGBDrawer : GLDrawTextureRGB
    private lateinit var mRectDrawer : GLDrawRect
    private lateinit var mTextureByteBuffer : ByteBuffer
    private var mTextureId : Int = 0
    private val mBackgroundMatrix = FloatArray(16)
    private val mRectMatrix = FloatArray(16)
    private val mCurrentDetections : ArrayList<Detection> = ArrayList()

    private var mViewportWidth = mTextureWidth
    private var mViewportHeight = mTextureHeight

    private enum class TypeModel(val color: Int, val typeName: String) {
        Car(
                Color.argb(255, 144, 255, 22),
                "CAR"
        ),
        Bicycle(
                Color.argb(255, 144, 128, 22),
                "BICYCLE"
        ),
        Person(
                Color.argb(255, 239, 6, 255),
                "PERSON"
        ),
        Light(
                Color.argb(255, 6, 241, 255),
                "LIGHT"
        ),
        Sign(
                Color.argb(255, 255, 204, 22),
                "SIGN"
        )
    }

    init {
        Matrix.setIdentityM(mBackgroundMatrix, 0)
        Matrix.setIdentityM(mRectMatrix, 0)
    }

    override fun onDrawFrame(p0: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        mRGBDrawer.draw(mTextureId, mBackgroundMatrix)
        for (currentDetection in mCurrentDetections) {
            val typeModel = TypeModel.values()[currentDetection.detectionClass.ordinal]
            val rect = currentDetection.boundingBox
            val floatArray = floatArrayOf(
                    rect.left, rect.bottom,
                    rect.right, rect.bottom,
                    rect.right, rect.top,
                    rect.left, rect.top)
                    .map { (it - 0.5f) * 2f }
                    .toFloatArray()
            mRectDrawer.draw(floatArray, mRectMatrix, typeModel.color)
        }
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    override fun onSurfaceChanged(p0: GL10?, width: Int, height: Int) {
        VisionLogger.d(TAG, "$width $height")
        mViewportWidth = width
        mViewportHeight = height
        val viewportAspect = mViewportWidth.toFloat() / mViewportHeight.toFloat()
        val textureAspect = mTextureWidth.toFloat() / mTextureHeight.toFloat()
        MyGLUtils.calculateMvpMatrix(mBackgroundMatrix, 0, MyGLUtils.Flip.FlipVertical,
                0.5f, 1.0f, textureAspect / viewportAspect)
        MyGLUtils.calculateMvpMatrix(mRectMatrix, 0, MyGLUtils.Flip.FlipVertical,
                0.0f, 1.0f, viewportAspect / textureAspect)
        GLES20.glViewport(0, 0, mViewportWidth, mViewportHeight)
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.5f)

        val renderer = GLES20.glGetString(GLES20.GL_RENDERER).toUpperCase(Locale.ENGLISH)
        val vendor = GLES20.glGetString(GLES20.GL_VENDOR).toUpperCase(Locale.ENGLISH)
        val version = GLES20.glGetString(GLES20.GL_VERSION)

        VisionLogger.i(TAG, " renderer = $renderer")
        VisionLogger.i(TAG, " vendor = $vendor")
        VisionLogger.i(TAG, " version = $version")

        MyGLUtils.setupBlend()
        GLES20.glEnable(GLES20.GL_BLEND)

        mTextureId = MyGLUtils.createTexture(mTextureWidth, mTextureHeight, GLES20.GL_RGBA)
        mTextureByteBuffer = ByteBuffer.allocateDirect(mTextureWidth * mTextureHeight * 4).order(ByteOrder.nativeOrder())

        mRGBDrawer = GLDrawTextureRGB()
        mRectDrawer = GLDrawRect()

    }

    // runs on render thread so no need for explicit synchronization
    fun onNewBackgroundFrame(rgbaByteArray: ByteArray) {
        mCurrentDetections.clear()
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId)
        // this solution may cause drop of couple first frames because of renderer not
        // been yet initialized while frames from main thread are already pushed
        if (::mTextureByteBuffer.isInitialized) {
            mTextureByteBuffer.let {
                it.rewind()
                it.put(rgbaByteArray)
                it.rewind()
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        mTextureWidth, mTextureHeight, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, it)
            }
        }
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    // runs on render thread so no need for explicit synchronization
    fun onNewFrameDetections(detections : Array<Detection>) {
        mCurrentDetections.clear()
        mCurrentDetections.addAll(detections)
    }

    override fun release() {
        mRGBDrawer.release()
        mRectDrawer.release()
    }
}