package com.mapbox.vision.telemetry

import com.mapbox.vision.utils.system.Time
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockito.BDDMockito.given
import org.mockito.Mockito
import org.spekframework.spek2.Spek

object TotalBytesCounterSpek : Spek({
    group("TotalBytesCounter") {

        var advancedByTime: Long = 0

        val systemTimeMills by memoized { System.currentTimeMillis() }
        val systemTimeMock by memoized {
            Mockito.mock(Time::class.java).also {
                given(it.millis()).willAnswer { systemTimeMills + advancedByTime }
            }
        }
        val totalBytesCounter10Min10kBytes: TotalBytesCounter by memoized {
            TotalBytesCounter.Impl(
                sessionLengthMillis = TimeUnit.MINUTES.toMillis(10),
                sessionMaxBytes = 10_000,
                time = systemTimeMock
            )
        }

        beforeEachTest {
            advancedByTime = 0
        }

        fun advancedByTime(seconds: Long) {
            advancedByTime = seconds
        }

        test("Check millis to next session") {
            assertEquals(totalBytesCounter10Min10kBytes.millisToNextSession(), 0)

            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(4_000))

            assertEquals(totalBytesCounter10Min10kBytes.millisToNextSession(), TimeUnit.MINUTES.toMillis(10))

            advancedByTime(TimeUnit.MINUTES.toMillis(8))
            assertEquals(totalBytesCounter10Min10kBytes.millisToNextSession(), TimeUnit.MINUTES.toMillis(2))

            advancedByTime(TimeUnit.MINUTES.toMillis(9))
            assertEquals(totalBytesCounter10Min10kBytes.millisToNextSession(), TimeUnit.MINUTES.toMillis(1))

            advancedByTime(TimeUnit.MINUTES.toMillis(9) + TimeUnit.SECONDS.toMillis(59))
            assertEquals(totalBytesCounter10Min10kBytes.millisToNextSession(), TimeUnit.SECONDS.toMillis(1))

            advancedByTime(TimeUnit.MINUTES.toMillis(10))
            assertEquals(totalBytesCounter10Min10kBytes.millisToNextSession(), 0)
        }

        test("Check fit in limit") {
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(-1))
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(-10_000))
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(-10_001))
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(-15_000))
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(0))
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(5_000))
            assertTrue(totalBytesCounter10Min10kBytes.fitInLimit(10_000))

            assertFalse(totalBytesCounter10Min10kBytes.fitInLimit(10_001))
            assertFalse(totalBytesCounter10Min10kBytes.fitInLimit(15_000))
            assertFalse(totalBytesCounter10Min10kBytes.fitInLimit(20_000))
            assertFalse(totalBytesCounter10Min10kBytes.fitInLimit(Long.MAX_VALUE))
        }

        test("Check track sent bytes") {
            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(0))
            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(1_000))
            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(4_000))
            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(5_000))
            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(0))

            assertFalse(totalBytesCounter10Min10kBytes.trackSentBytes(1))
            assertFalse(totalBytesCounter10Min10kBytes.trackSentBytes(1_000))

            advancedByTime(TimeUnit.MINUTES.toMillis(10))

            assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(1))
        }
    }
})
