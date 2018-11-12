package com.mapbox.vision.telemetry

/**
 * Counts sent bytes and limits bytes sent per session.
 */
internal interface TotalBytesCounter {

    /**
     * Returns if amount of [bytes] can still be send during current session.
     */
    fun trackSentBytes(bytes: Long): Boolean

    fun millisToNextSession(): Long

    class Impl(
            private val sessionLengthMillis: Long = SESSION_LENGTH_MILLIS,
            private val sessionMaxBytes: Long = SESSION_MAX_BYTES
    ) : TotalBytesCounter {

        companion object {
            private const val SESSION_LENGTH_MILLIS = 60 * 60 * 1000L // one hour
            private const val SESSION_MAX_BYTES = 30 * 1024 * 1024L // 30 MB
        }

        private var sessionStartMillis: Long = 0
        private var bytesSentPerSession: Long = 0

        override fun trackSentBytes(bytes: Long): Boolean {

            val timestamp = System.currentTimeMillis()

            return when {
                sessionStartMillis + sessionLengthMillis < timestamp -> {
                    sessionStartMillis = timestamp
                    bytesSentPerSession = bytes
                    true
                }
                bytesSentPerSession + bytes < sessionMaxBytes -> {
                    bytesSentPerSession += bytes
                    true
                }
                else -> {
                    false
                }
            }
        }

        override fun millisToNextSession() = (sessionStartMillis + sessionLengthMillis - System.currentTimeMillis()).let { sessionLength ->
            when {
                sessionLength < 0 -> 0
                sessionLength > sessionLengthMillis -> sessionLengthMillis
                else -> sessionLength
            }
        }
    }
}
