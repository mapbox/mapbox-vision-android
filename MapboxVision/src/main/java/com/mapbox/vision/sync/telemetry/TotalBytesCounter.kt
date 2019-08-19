package com.mapbox.vision.sync.telemetry

import com.mapbox.vision.utils.prefs.SessionPrefs
import com.mapbox.vision.utils.system.Time
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Counts sent bytes and limits bytes sent per session.
 */
internal interface TotalBytesCounter {

    /**
     * Returns if amount of [bytes] can still be send during current session.
     */
    fun trackSentBytes(bytes: Long): Boolean

    fun millisToNextSession(): Long

    fun fitInLimitCurrentSession(bytes: Long): Boolean

    fun fitInLimit(bytes: Long): Boolean

    class Impl(
        private val sessionLengthMillis: Long = SESSION_LENGTH_MILLIS,
        private val sessionMaxBytes: Long = SESSION_MAX_BYTES,
        private val currentTime: Time = Time.SystemImpl,
        private val sessionPrefs: SessionPrefs = SessionPrefs.Impl
    ) : TotalBytesCounter {

        companion object {
            private val SESSION_LENGTH_MILLIS = TimeUnit.HOURS.toMillis(1)
            private const val SESSION_MAX_BYTES = 30 * 1024 * 1024L // 30 MB
        }

        private val sessionStartMillis: Long
            get() = sessionPrefs.sessionStartMillis.get().let {
                it ?: currentTime.millis().also { sessionPrefs.sessionStartMillis.set(it) }
            }
        private val bytesSentPerSession: Long
            get() = sessionPrefs.bytesSentPerSession.get() ?: 0L

        override fun trackSentBytes(bytes: Long): Boolean {
            val timestamp = currentTime.millis()

            if (sessionStartMillis + sessionLengthMillis <= timestamp) {
                sessionPrefs.sessionStartMillis.set(timestamp)
                sessionPrefs.bytesSentPerSession.set(0)
            }

            return when {
                fitInLimitCurrentSession(bytes) -> {
                    sessionPrefs.bytesSentPerSession.set(bytes + bytesSentPerSession)
                    true
                }
                else -> false
            }
        }

        override fun millisToNextSession(): Long {
            return max(0L, sessionStartMillis + sessionLengthMillis - currentTime.millis())
        }

        override fun fitInLimitCurrentSession(bytes: Long): Boolean = bytes + bytesSentPerSession <= sessionMaxBytes

        override fun fitInLimit(bytes: Long): Boolean = bytes <= sessionMaxBytes
    }
}
