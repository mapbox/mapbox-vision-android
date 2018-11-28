package com.mapbox.vision.video.videosource.camera

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import android.util.Size
import android.view.Surface
import com.mapbox.vision.video.videosource.VideoSource
import com.mapbox.vision.video.videosource.VideoSourceListener
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

@Suppress("DEPRECATION")
internal class CompatOpenglCameraVideoSourceImpl(
        private val application: Application
) : VideoSource {
    companion object {
        private const val WIDTH = 640
        private const val HEIGHT = 360
        private const val BIT_RATE = 6000000      // Mbps

        private const val TAG = "CompatCameraImpl"
        private const val VERBOSE = false               // lots of logging

        // parameters for the encoder
        private const val MIME_TYPE = "video/avc"       // H.264 Advanced Video Coding
        private const val FRAME_RATE = 30               // 30fps
        private const val IFRAME_INTERVAL = 5           // 5 seconds between I-frames
        private const val DURATION_NANOS = 8 * 1000000000L        // 8 seconds of video

        private const val TIMEOUT_USEC = 10000
    }

    private var encoder: MediaCodec? = null
    private var inputSurface: CodecInputSurface? = null
    private var muxer: MediaMuxer? = null
    private var muxerStarted: Boolean = false
    private var trackIndex: Int = 0

    private var camera: Camera? = null
    private var surfaceTextureManager: SurfaceTextureManager? = null

    // allocate one of these up front so we don't need to do it every time
    private var bufferInfo: MediaCodec.BufferInfo? = null

    private var videoSourceListener: VideoSourceListener? = null

    override fun getSourceWidth(): Int = WIDTH

    override fun getSourceHeight(): Int = HEIGHT

    override fun attach(videoSourceListener: VideoSourceListener) {
        this.videoSourceListener = videoSourceListener
    }

    override fun detach() {
        videoSourceListener = null
    }

    /**
     * Tests encoding of AVC video from Camera input.  The output is saved as an MP4 file.
     */
    private fun encodeCameraToMpeg() {
        try {
            prepareCamera(WIDTH, HEIGHT)
            prepareEncoder(WIDTH, HEIGHT, BIT_RATE)
            inputSurface!!.makeCurrent()
            prepareSurfaceTexture()

            camera!!.startPreview()

            val startWhen = System.nanoTime()
            val desiredEnd = startWhen + DURATION_NANOS
            val surfaceTexture = surfaceTextureManager!!.surfaceTexture
            var frameCount = 0

            while (System.nanoTime() < desiredEnd) {
                // Feed any pending encoder output into the muxer.
                drainEncoder(false)

                frameCount++

                // Acquire a new frame of input, and render it to the Surface.  If we had a
                // GLSurfaceView we could switch EGL contexts and call drawImage() a second
                // time to render it on screen.  The texture can be shared between contexts by
                // passing the GLSurfaceView's EGLContext as eglCreateContext()'s share_context
                // argument.
                surfaceTextureManager!!.awaitNewImage()
                surfaceTextureManager!!.drawImage()

                // Set the presentation time stamp from the SurfaceTexture's time stamp.  This
                // will be used by MediaMuxer to set the PTS in the video.
                if (VERBOSE) {
                    Log.d(TAG, "present: ${(surfaceTexture!!.timestamp - startWhen) / 1000000.0}ms")
                }
                inputSurface!!.setPresentationTime(surfaceTexture!!.timestamp)

                // Submit it to the encoder.  The eglSwapBuffers call will block if the input
                // is full, which would be bad if it stayed full until we dequeued an output
                // buffer (which we can't do, since we're stuck here).  So long as we fully drain
                // the encoder before supplying additional input, the system guarantees that we
                // can supply another frame without blocking.
                if (VERBOSE) Log.d(TAG, "sending frame to encoder")
                inputSurface!!.swapBuffers()
            }

            // send end-of-stream to encoder, and drain remaining output
            drainEncoder(true)
        } finally {
            // release everything we grabbed
            releaseAll()
        }
    }

    private fun releaseAll() {
        releaseCamera()
        releaseEncoder()
        releaseSurfaceTexture()
    }

    /**
     * Configures Camera for video capture.  Sets camera.
     *
     * Opens a Camera and sets parameters.  Does not start preview.
     */
    private fun prepareCamera(encWidth: Int, encHeight: Int) {
        if (camera != null) {
            throw RuntimeException("camera already initialized")
        }

        val info = Camera.CameraInfo()

        for (i in 0 until Camera.getNumberOfCameras()) {
            Camera.getCameraInfo(i, info)
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                camera = Camera.open(i)
                break
            }
        }
        if (camera == null) {
            Log.d(TAG, "No front-facing camera found; opening default")
            camera = Camera.open()    // opens first back-facing camera
        }
        if (camera == null) {
            throw RuntimeException("Unable to open camera")
        }

        val params = camera!!.parameters

        val size = chooseOptimalCameraResolution(
                params.supportedVideoSizes.map { Size(it.width, it.height) },
                Size(WIDTH, HEIGHT)
        )
        params.setPreviewSize(size.width, size.height)

        // leave the frame rate set to default
        camera!!.parameters = params

        Log.d(TAG, "Camera preview size is " + size.width + "x" + size.height)
    }

    /**
     * Stops camera preview, and releases the camera to the system.
     */
    private fun releaseCamera() {
        if (camera != null) {
            if (VERBOSE) Log.d(TAG, "releasing camera")
            camera!!.stopPreview()
            camera!!.release()
            camera = null
        }
    }

    /**
     * Configures SurfaceTexture for camera preview.  Initializes surfaceTextureManager, and sets the
     * associated SurfaceTexture as the Camera's "preview texture".
     *
     * Configure the EGL surface that will be used for output before calling here.
     */
    private fun prepareSurfaceTexture() {
        surfaceTextureManager = SurfaceTextureManager()
        val surfaceTexture = surfaceTextureManager!!.surfaceTexture
        try {
            camera!!.setPreviewTexture(surfaceTexture)
        } catch (ioe: IOException) {
            throw RuntimeException("setPreviewTexture failed", ioe)
        }

    }

    /**
     * Releases the SurfaceTexture.
     */
    private fun releaseSurfaceTexture() {
        if (surfaceTextureManager != null) {
            surfaceTextureManager!!.release()
            surfaceTextureManager = null
        }
    }

    /**
     * Configures encoder and muxer state, and prepares the input Surface. Initializes
     * encoder, muxer, inputSurface, bufferInfo, trackIndex, and muxerStarted.
     */
    private fun prepareEncoder(width: Int, height: Int, bitRate: Int) {
        bufferInfo = MediaCodec.BufferInfo()

        val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)

        // Set some properties.  Failing to specify some of these can cause the MediaCodec
        // configure() call to throw an unhelpful exception.
        format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        )
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL)
        if (VERBOSE) Log.d(TAG, "format: $format")

        // Create a MediaCodec encoder, and configure it with our format.  Get a Surface
        // we can use for input and wrap it with a class that handles the EGL work.
        //
        // If you want to have two EGL contexts -- one for display, one for recording --
        // you will likely want to defer instantiation of CodecInputSurface until after the
        // "display" EGL context is created, then modify the eglCreateContext call to
        // take eglGetCurrentContext() as the share_context argument.
        encoder = MediaCodec.createEncoderByType(MIME_TYPE)
        encoder!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = CodecInputSurface(encoder!!.createInputSurface())
        encoder!!.start()

        // Output filename.  Ideally this would use Context.getFilesDir() rather than a
        // hard-coded output directory.
        val outputPath = File(application.filesDir, "test." + width + "x" + height + ".mp4").toString()
        Log.i(TAG, "Output file is $outputPath")


        // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
        // because our MediaFormat doesn't have the Magic Goodies.  These can only be
        // obtained from the encoder after it has started processing data.
        //
        // We're not actually interested in multiplexing audio.  We just want to convert
        // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
        try {
            muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (ioe: IOException) {
            throw RuntimeException("MediaMuxer creation failed", ioe)
        }

        trackIndex = -1
        muxerStarted = false
    }

    /**
     * Releases encoder resources.
     */
    private fun releaseEncoder() {
        if (VERBOSE) Log.d(TAG, "releasing encoder objects")
        if (encoder != null) {
            encoder!!.stop()
            encoder!!.release()
            encoder = null
        }
        if (inputSurface != null) {
            inputSurface!!.release()
            inputSurface = null
        }
        if (muxer != null) {
            muxer!!.stop()
            muxer!!.release()
            muxer = null
        }
    }

    /**
     * Extracts all pending data from the encoder and forwards it to the muxer.
     *
     * If endOfStream is not set, this returns when there is no more data to drain.  If it
     * is set, we send EOS to the encoder, and then iterate until we see EOS on the output.
     * Calling this with endOfStream set should be done once, right before stopping the muxer.
     *
     * We're just using the muxer to get a .mp4 file (instead of a raw H.264 stream).  We're
     * not recording audio.
     */
    private fun drainEncoder(endOfStream: Boolean) {
        if (VERBOSE) Log.d(TAG, "drainEncoder($endOfStream)")

        if (endOfStream) {
            if (VERBOSE) Log.d(TAG, "sending EOS to encoder")
            encoder!!.signalEndOfInputStream()
        }

        var encoderOutputBuffers = encoder!!.outputBuffers
        while (true) {
            val encoderStatus = encoder!!.dequeueOutputBuffer(bufferInfo!!, TIMEOUT_USEC.toLong())
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // no output available yet
                if (!endOfStream) {
                    break
                } else {
                    if (VERBOSE) Log.d(TAG, "no output available, spinning to await EOS")
                }
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                // not expected for an encoder
                encoderOutputBuffers = encoder!!.outputBuffers
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // should happen before receiving buffers, and should only happen once
                if (muxerStarted) {
                    throw RuntimeException("format changed twice")
                }
                val newFormat = encoder!!.outputFormat
                Log.d(TAG, "encoder output format changed: $newFormat")

                // now that we have the Magic Goodies, start the muxer
                trackIndex = muxer!!.addTrack(newFormat)
                muxer!!.start()
                muxerStarted = true
            } else if (encoderStatus < 0) {
                Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: $encoderStatus")
                // let's ignore it
            } else {
                val encodedData = encoderOutputBuffers[encoderStatus]
                                  ?: throw RuntimeException(("encoderOutputBuffer " + encoderStatus +
                                                             " was null"))

                if (bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    // The codec config data was pulled out and fed to the muxer when we got
                    // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                    if (VERBOSE) Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG")
                    bufferInfo!!.size = 0
                }

                if (bufferInfo!!.size != 0) {
                    if (!muxerStarted) {
                        throw RuntimeException("muxer hasn't started")
                    }

                    // adjust the ByteBuffer values to match BufferInfo (not needed?)
                    encodedData.position(bufferInfo!!.offset)
                    encodedData.limit(bufferInfo!!.offset + bufferInfo!!.size)

                    muxer!!.writeSampleData(trackIndex, encodedData, bufferInfo!!)
                    if (VERBOSE) Log.d(TAG, "sent " + bufferInfo!!.size + " bytes to muxer")
                }

                encoder!!.releaseOutputBuffer(encoderStatus, false)

                if (bufferInfo!!.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    if (!endOfStream) {
                        Log.w(TAG, "reached end of stream unexpectedly")
                    } else {
                        if (VERBOSE) Log.d(TAG, "end of stream reached")
                    }
                    break      // out of while
                }
            }
        }
    }

    /**
     * Holds state associated with a Surface used for MediaCodec encoder input.
     *
     * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses
     * that to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to
     * be sent to the video encoder.
     *
     * This object owns the Surface -- releasing this will release the Surface too.
     */
    private class CodecInputSurface(private var surface: Surface?) {

        private var eGLDisplay = EGL14.EGL_NO_DISPLAY
        private var eglContext = EGL14.EGL_NO_CONTEXT
        private var eGLSurface = EGL14.EGL_NO_SURFACE

        init {
            eGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eGLDisplay === EGL14.EGL_NO_DISPLAY) {
                throw RuntimeException("unable to get EGL14 display")
            }
            val version = IntArray(2)
            if (!EGL14.eglInitialize(eGLDisplay, version, 0, version, 1)) {
                throw RuntimeException("unable to initialize EGL14")
            }

            // Configure EGL for recording and OpenGL ES 2.0.
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(
                    eGLDisplay,
                    intArrayOf(EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT, EGL_RECORDABLE_ANDROID, 1, EGL14.EGL_NONE),
                    0,
                    configs,
                    0,
                    configs.size,
                    numConfigs,
                    0
            )
            checkEglError("eglCreateContext RGB888+recordable ES2")

            eglContext = EGL14.eglCreateContext(
                    eGLDisplay,
                    configs[0],
                    EGL14.EGL_NO_CONTEXT,
                    intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE),
                    0
            )
            checkEglError("eglCreateContext")

            // Create a window surface, and attach it to the Surface we received.
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            eGLSurface = EGL14.eglCreateWindowSurface(eGLDisplay, configs[0], surface,
                    surfaceAttribs, 0)
            checkEglError("eglCreateWindowSurface")
        }

        /**
         * Discards all resources held by this class, notably the EGL context.  Also releases the
         * Surface that was passed to our constructor.
         */
        fun release() {
            if (eGLDisplay !== EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                        EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(eGLDisplay, eGLSurface)
                EGL14.eglDestroyContext(eGLDisplay, eglContext)
                EGL14.eglReleaseThread()
                EGL14.eglTerminate(eGLDisplay)
            }
            surface!!.release()

            eGLDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eGLSurface = EGL14.EGL_NO_SURFACE

            surface = null
        }

        /**
         * Makes our EGL context and surface current.
         */
        fun makeCurrent() {
            EGL14.eglMakeCurrent(eGLDisplay, eGLSurface, eGLSurface, eglContext)
            checkEglError("eglMakeCurrent")
        }

        /**
         * Calls eglSwapBuffers.  Use this to "publish" the current frame.
         */
        fun swapBuffers(): Boolean {
            val result = EGL14.eglSwapBuffers(eGLDisplay, eGLSurface)
            checkEglError("eglSwapBuffers")
            return result
        }

        /**
         * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
         */
        fun setPresentationTime(nsecs: Long) {
            EGLExt.eglPresentationTimeANDROID(eGLDisplay, eGLSurface, nsecs)
            checkEglError("eglPresentationTimeANDROID")
        }

        /**
         * Checks for EGL errors.  Throws an exception if one is found.
         */
        private fun checkEglError(msg: String) {
            val error = EGL14.eglGetError()
            if (error != EGL14.EGL_SUCCESS) {
                throw RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error))
            }
        }

        companion object {
            private val EGL_RECORDABLE_ANDROID = 0x3142
        }
    }

    /**
     * Manages a SurfaceTexture.  Creates SurfaceTexture and TextureRender objects, and provides
     * functions that wait for frames and render them to the current EGL surface.
     *
     *
     * The SurfaceTexture can be passed to Camera.setPreviewTexture() to receive camera output.
     */
    private class SurfaceTextureManager : SurfaceTexture.OnFrameAvailableListener {

        companion object {
            private const val TIMEOUT_MS = 2500
        }

        var surfaceTexture: SurfaceTexture? = null
        private var textureRender: SurfaceTextureRender? = null

        private val frameSyncObject = Object()     // guards frameAvailable
        private var frameAvailable: Boolean = false

        /**
         * Creates instances of TextureRender and SurfaceTexture.
         */
        init {
            textureRender = SurfaceTextureRender()
            textureRender!!.surfaceCreated()

            if (VERBOSE) Log.d(TAG, "textureID=" + textureRender!!.textureId)
            @SuppressLint("Recycle")
            surfaceTexture = SurfaceTexture(textureRender!!.textureId)

            // This doesn't work if this object is created on the thread that CTS started for
            // these test cases.
            //
            // The CTS-created thread has a Looper, and the SurfaceTexture constructor will
            // create a Handler that uses it.  The "frame available" message is delivered
            // there, but since we're not a Looper-based thread we'll never see it.  For
            // this to do anything useful, OutputSurface must be created on a thread without
            // a Looper, so that SurfaceTexture uses the main application Looper instead.
            //
            // Java language note: passing "this" out of a constructor is generally unwise,
            // but we should be able to get away with it here.
            // FIXME
            surfaceTexture!!.setOnFrameAvailableListener(this)
        }

        fun release() {
            // this causes a bunch of warnings that appear harmless but might confuse someone:
            //  W BufferQueue: [unnamed-3997-2] cancelBuffer: BufferQueue has been abandoned!

            surfaceTexture!!.release();
            textureRender = null
            surfaceTexture = null
        }

        /**
         * Replaces the fragment shader.
         */
        // TODO
        fun changeFragmentShader(fragmentShader: String?) {
            textureRender!!.changeFragmentShader(fragmentShader)
        }

        /**
         * Latches the next buffer into the texture.  Must be called from the thread that created
         * the OutputSurface object.
         */
        fun awaitNewImage() {
            synchronized(frameSyncObject)
            {
                while (!frameAvailable) {
                    try {
                        // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                        // stalling the test if it doesn't arrive.
                        frameSyncObject.wait(TIMEOUT_MS.toLong())
                        if (!frameAvailable) {
                            // TODO: if "spurious wakeup", continue while loop
                            throw RuntimeException("Camera frame wait timed out")
                        }
                    } catch (ie: InterruptedException) {
                        // shouldn't happen
                        throw RuntimeException(ie)
                    }

                }
                frameAvailable = false
            }

            // Latch the data.
            textureRender!!.checkGlError("before updateTexImage")
            surfaceTexture!!.updateTexImage()
        }

        /**
         * Draws the data from SurfaceTexture onto the current EGL surface.
         */
        fun drawImage() {
            textureRender!!.drawFrame(surfaceTexture!!)
        }

        override fun onFrameAvailable(st: SurfaceTexture) {
            if (VERBOSE) Log.d(TAG, "new frame available")
            synchronized(frameSyncObject) {
                if (frameAvailable) {
                    throw RuntimeException("frameAvailable already set, frame could be dropped")
                }
                frameAvailable = true
                frameSyncObject.notifyAll()
            }
        }
    }

    /**
     * Code for rendering a texture onto a surface using OpenGL ES 2.0.
     */
    private class SurfaceTextureRender {
        companion object {
            private val FLOAT_SIZE_BYTES = 4
            private val TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES
            private val TRIANGLE_VERTICES_DATA_POS_OFFSET = 0
            private val TRIANGLE_VERTICES_DATA_UV_OFFSET = 3

            private val VERTEX_SHADER = "uniform mat4 uMVPMatrix;\n" +
                                        "uniform mat4 uSTMatrix;\n" +
                                        "attribute vec4 aPosition;\n" +
                                        "attribute vec4 aTextureCoord;\n" +
                                        "varying vec2 vTextureCoord;\n" +
                                        "void main() {\n" +
                                        "    gl_Position = uMVPMatrix * aPosition;\n" +
                                        "    vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                                        "}\n"

            private val FRAGMENT_SHADER = "#extension GL_OES_EGL_image_external : require\n" +
                                          "precision mediump float;\n" +      // highp here doesn't seem to matter

                                          "varying vec2 vTextureCoord;\n" +
                                          "uniform samplerExternalOES sTexture;\n" +
                                          "void main() {\n" +
                                          "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                                          "}\n"

            fun checkLocation(location: Int, label: String) {
                if (location < 0) {
                    throw RuntimeException("Unable to locate '$label' in program")
                }
            }
        }

        private val triangleVerticesData = floatArrayOf(
                // X, Y, Z, U, V
                -1.0f, -1.0f, 0f, 0f, 0f, 1.0f, -1.0f, 0f, 1f, 0f, -1.0f, 1.0f, 0f, 0f, 1f, 1.0f, 1.0f, 0f, 1f, 1f
        )

        private val triangleVertices: FloatBuffer

        private val mvpMatrix = FloatArray(16)
        private val stMatrix = FloatArray(16)

        private var program: Int = 0
        var textureId = -12345
            private set
        private var uMVPMatrixHandle: Int = 0
        private var uSTMatrixHandle: Int = 0
        private var aPositionHandle: Int = 0
        private var aTextureHandle: Int = 0

        init {
            triangleVertices = ByteBuffer.allocateDirect(
                    triangleVerticesData.size * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
            triangleVertices.put(triangleVerticesData).position(0)

            Matrix.setIdentityM(stMatrix, 0)
        }

        fun drawFrame(texture: SurfaceTexture) {
            checkGlError("onDrawFrame start")
            texture.getTransformMatrix(stMatrix)

            // (optional) clear to green so we can see if we're failing to set pixels
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT or GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glUseProgram(program)
            checkGlError("glUseProgram")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

            triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET)
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices)
            checkGlError("glVertexAttribPointer maPosition")
            GLES20.glEnableVertexAttribArray(aPositionHandle)
            checkGlError("glEnableVertexAttribArray aPositionHandle")

            triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET)
            GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices)
            checkGlError("glVertexAttribPointer aTextureHandle")
            GLES20.glEnableVertexAttribArray(aTextureHandle)
            checkGlError("glEnableVertexAttribArray aTextureHandle")

            Matrix.setIdentityM(mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mvpMatrix, 0)
            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            checkGlError("glDrawArrays")

            // IMPORTANT: on some devices, if you are sharing the external texture between two
            // contexts, one context may not see updates to the texture unless you un-bind and
            // re-bind it.  If you're not using shared EGL contexts, you don't need to bind
            // texture 0 here.
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        }

        /**
         * Initializes GL state.  Call this after the EGL surface has been created and made current.
         */
        fun surfaceCreated() {
            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            if (program == 0) {
                throw RuntimeException("failed creating program")
            }
            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            checkLocation(aPositionHandle, "aPosition")
            aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            checkLocation(aTextureHandle, "aTextureCoord")

            uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
            checkLocation(uMVPMatrixHandle, "uMVPMatrix")
            uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
            checkLocation(uSTMatrixHandle, "uSTMatrix")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)

            textureId = textures[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            checkGlError("glBindTexture mTextureID")

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_NEAREST.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE)
            checkGlError("glTexParameter")
        }

        /**
         * Replaces the fragment shader.  Pass in null to reset to default.
         */
        fun changeFragmentShader(fragmentShader: String?) {
            GLES20.glDeleteProgram(program)
            program = createProgram(VERTEX_SHADER, fragmentShader ?: FRAGMENT_SHADER)
            if (program == 0) {
                throw RuntimeException("failed creating program")
            }
        }

        private fun loadShader(shaderType: Int, source: String): Int {
            var shader = GLES20.glCreateShader(shaderType)
            checkGlError("glCreateShader type=$shaderType")
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $shaderType:")
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
            return shader
        }

        private fun createProgram(vertexSource: String, fragmentSource: String): Int {
            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
            if (vertexShader == 0) {
                return 0
            }
            val pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
            if (pixelShader == 0) {
                return 0
            }

            var program = GLES20.glCreateProgram()
            if (program == 0) {
                Log.e(TAG, "Could not create program")
            }
            GLES20.glAttachShader(program, vertexShader)
            checkGlError("glAttachShader")
            GLES20.glAttachShader(program, pixelShader)
            checkGlError("glAttachShader")
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ")
                Log.e(TAG, GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                program = 0
            }
            return program
        }

        fun checkGlError(op: String) {
            val error = GLES20.glGetError()
            while (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "$op: glError $error")
                throw RuntimeException("$op: glError $error")
            }
        }
    }
}
