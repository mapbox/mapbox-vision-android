package com.mapbox.vision.sync.telemetry

import com.mapbox.vision.utils.system.Time
import java.util.concurrent.TimeUnit

/**
 * Counts sent bytes and limits bytes sent per session.
 */
internal interface TotalBytesCounter {

    /**
     * Returns if amount of [bytes] can still be send during current session.
     */
    fun trackSentBytes(bytes: Long): Boolean

    fun millisToNextSession(): Long

    fun fitInLimit(bytes: Long): Boolean

    class Impl(
        private val sessionLengthMillis: Long = SESSION_LENGTH_MILLIS,
        private val sessionMaxBytes: Long = SESSION_MAX_BYTES,
        private val time: Time = Time.SystemImpl
    ) : TotalBytesCounter {

        companion object {
            private val SESSION_LENGTH_MILLIS = TimeUnit.HOURS.toMillis(1)
            private const val SESSION_MAX_BYTES = 30 * 1024 * 1024L // 30 MB
        }

        private var sessionStartMillis: Long = 0
        private var bytesSentPerSession: Long = 0

        override fun trackSentBytes(bytes: Long): Boolean {
            val timestamp = time.millis()

            if (sessionStartMillis + sessionLengthMillis <= timestamp) {
                sessionStartMillis = timestamp
                bytesSentPerSession = 0
            }

            return when {
                fitInLimitCurrentSession(bytes) -> {
                    bytesSentPerSession += bytes
                    true
                }
                else -> false
            }
        }

        override fun millisToNextSession(): Long =
            (sessionStartMillis + sessionLengthMillis - time.millis()).let { sessionRest ->
                if (sessionRest < 0) 0 else sessionRest
            }

        private fun fitInLimitCurrentSession(bytes: Long): Boolean = bytes + bytesSentPerSession <= sessionMaxBytes

        override fun fitInLimit(bytes: Long): Boolean = bytes <= sessionMaxBytes
    }
}
