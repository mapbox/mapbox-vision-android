package com.mapbox.vision.sync.telemetry

import android.content.SharedPreferences
import com.mapbox.vision.mobile.core.utils.preferences.Preference
import com.mapbox.vision.dsl.TestCase
import com.mapbox.vision.utils.prefs.TotalBytesCounterPrefs
import com.mapbox.vision.utils.system.Time
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestFactory

class TotalBytesCounterTest {

    companion object {
        val TEST_SESSION_MAX_BYTES = 10_000L
        val TEST_SESSION_LENGTH_MS = TimeUnit.MINUTES.toMillis(10)
        val CURRENT_TIME = TimeUnit.HOURS.toMillis(20)
        val DEFAULT_SESSION_SIZE_MB = 30
    }

    @TestFactory
    fun `Check default session size`() = TestCase {
        val mockedTime = mockk<Time>()
        every { mockedTime.millis() }.returns(CURRENT_TIME)

        Given("TotalBytesCounter with default params") {
            val totalBytesCounter = TotalBytesCounter.Impl(
                currentTime = mockedTime,
                totalBytesCounterPrefs = TotalBytesCounterStorageTestImpl()
            )

            When("Get fitInLimit for ${DEFAULT_SESSION_SIZE_MB}MB") {
                val actualValue = totalBytesCounter.fitInLimit(DEFAULT_SESSION_SIZE_MB * 1024 * 1024L)
                Then("It should be <true>") {
                    assertEquals(true, actualValue)
                }
            }

            When("Get fitInLimit for ${DEFAULT_SESSION_SIZE_MB}MB and 1 byte") {
                val actualValue = totalBytesCounter.fitInLimit(DEFAULT_SESSION_SIZE_MB * 1024 * 1024L + 1)
                Then("It should be <false>") {
                    assertEquals(false, actualValue)
                }
            }
        }
    }

    @TestFactory
    fun `Check default session length`() = TestCase {
        val mockedTime = mockk<Time>()
        every { mockedTime.millis() }.returns(CURRENT_TIME)
        Given("TotalBytesCounter with default params") {
            val totalBytesCounter = TotalBytesCounter.Impl(
                currentTime = mockedTime,
                totalBytesCounterPrefs = TotalBytesCounterStorageTestImpl()
            )
            When("Get millisToNextSession") {
                val actualValue = totalBytesCounter.millisToNextSession()
                Then("It should be <1 hour>") {
                    assertEquals(TimeUnit.HOURS.toMillis(1), actualValue)
                }
            }
        }
    }

    @TestFactory
    fun `Check fitInLimit`() = TestCase {
        Given("TotalBytesCounter10Min10kBytes") {
            val mockedTime = mockk<Time>()
            val totalBytesCounter10Min10kBytes = TotalBytesCounter.Impl(
                sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                currentTime = mockedTime,
                totalBytesCounterPrefs = TotalBytesCounterStorageTestImpl()
            )

            mapOf(
                Long.MIN_VALUE to true,
                -1L to true,
                -10_000L to true,
                -10_001L to true,
                -15_000L to true,
                0L to true,
                5_000L to true,
                10_000L to true,

                10_001L to false,
                15_000L to false,
                20_000L to false,
                Long.MAX_VALUE to false
            ).forEach { (bytes, expectedValue) ->
                When("Get FitInLimit for $bytes bytes") {
                    val actualValue = totalBytesCounter10Min10kBytes.fitInLimit(bytes)

                    Then("It should be $expectedValue") {
                        assertEquals(expectedValue, actualValue)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check millisToNextSession`() = TestCase {
        val mockedTime = mockk<Time>()

        val testCases = mapOf(
            TimeUnit.MINUTES.toMillis(0) to TimeUnit.MINUTES.toMillis(10),
            TimeUnit.MILLISECONDS.toMillis(1) to TimeUnit.MINUTES.toMillis(9) +
                    TimeUnit.SECONDS.toMillis(59) +
                    TimeUnit.MILLISECONDS.toMillis(999),
            TimeUnit.MINUTES.toMillis(2) to TimeUnit.MINUTES.toMillis(8),
            TimeUnit.MINUTES.toMillis(5) to TimeUnit.MINUTES.toMillis(5),
            TimeUnit.MINUTES.toMillis(8) to TimeUnit.MINUTES.toMillis(2),
            TimeUnit.MINUTES.toMillis(9) to TimeUnit.MINUTES.toMillis(1),

            TimeUnit.MINUTES.toMillis(9) +
                    TimeUnit.SECONDS.toMillis(59) to TimeUnit.SECONDS.toMillis(1),

            TimeUnit.MINUTES.toMillis(9) +
                    TimeUnit.SECONDS.toMillis(59) +
                    TimeUnit.MILLISECONDS.toMillis(999) to TimeUnit.MILLISECONDS.toMillis(1),

            TimeUnit.MINUTES.toMillis(10) to TimeUnit.MINUTES.toMillis(0),

            TimeUnit.MINUTES.toMillis(10) + TimeUnit.MILLISECONDS.toMillis(1) to TimeUnit.MINUTES.toMillis(0),
            TimeUnit.MINUTES.toMillis(11) to TimeUnit.MINUTES.toMillis(0),
            TimeUnit.MINUTES.toMillis(25) to TimeUnit.MINUTES.toMillis(0)
        )
        testCases.forEach { (requestTime, expectedValue) ->
            Given("TotalBytesCounter with 10 minutes, 10 KBytes and started session") {
                val sessionPrefs = TotalBytesCounterStorageTestImpl()
                val totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                    sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                    sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                    mockedTime = mockedTime,
                    currentTime = CURRENT_TIME,
                    totalBytesCounterPrefs = sessionPrefs
                )

                When("Get millisToNextSession for $requestTime ms") {
                    every { mockedTime.millis() }.returns(CURRENT_TIME + requestTime)
                    val actualValue = totalBytesCounter10Min10kBytes.millisToNextSession()

                    Then("It should be <$expectedValue> ms") {
                        assertEquals(expectedValue, actualValue)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check fitInLimitCurrentSession`() = TestCase {
        val mockedTime = mockk<Time>()

        val testCases = listOf(
            Pair(
                0L, mapOf(
                    0L to true,
                    TEST_SESSION_MAX_BYTES / 2 to true,
                    TEST_SESSION_MAX_BYTES to true,
                    TEST_SESSION_MAX_BYTES + 1 to false,
                    TEST_SESSION_MAX_BYTES + 2 to false
                )
            ),
            Pair(
                1L, mapOf(
                    0L to true,
                    TEST_SESSION_MAX_BYTES / 2 to true,
                    TEST_SESSION_MAX_BYTES to false,
                    TEST_SESSION_MAX_BYTES + 1 to false
                )
            ),
            Pair(
                TEST_SESSION_MAX_BYTES / 2, mapOf(
                    0L to true,
                    TEST_SESSION_MAX_BYTES / 2 to true,
                    (TEST_SESSION_MAX_BYTES / 2 + 1) to false,
                    TEST_SESSION_MAX_BYTES to false
                )
            ),
            Pair(
                TEST_SESSION_MAX_BYTES, mapOf(
                    0L to true,
                    1L to false,
                    TEST_SESSION_MAX_BYTES / 2 to false,
                    TEST_SESSION_MAX_BYTES to false
                )
            )
        )
        testCases.forEach { listOfAlreadySentBytesAndResults ->

            Given("Test TotalBytesCounter with already sent ${listOfAlreadySentBytesAndResults.first} bytes") {
                val totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                    sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                    sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                    mockedTime = mockedTime,
                    currentTime = CURRENT_TIME
                )
                totalBytesCounter10Min10kBytes.trackSentBytes(listOfAlreadySentBytesAndResults.first)

                listOfAlreadySentBytesAndResults.second.forEach { (bytes, expectedValue) ->
                    When("Get fitInLimitCurrentSession for $bytes bytes") {
                        val actualValue = totalBytesCounter10Min10kBytes.fitInLimitCurrentSession(bytes)

                        Then("It should be $expectedValue") {
                            assertEquals(expectedValue, actualValue)
                        }
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check trackSentBytes`() = TestCase {
        val mockedTime = mockk<Time>()

        val testCases = listOf(
            listOf(
                Pair(0L, true),
                Pair(1_000L, true),
                Pair(5_000L, true),
                Pair(4_000L, true),
                Pair(0L, true),
                Pair(1L, false),
                Pair(1_000L, false)
            ),

            listOf(
                Pair(5_000L, true),
                Pair(5_000L, true),
                Pair(1_000L, false)
            ),

            listOf(
                Pair(TEST_SESSION_MAX_BYTES - 1, true),
                Pair(1L, true),
                Pair(1L, false)
            ),

            listOf(
                Pair(TEST_SESSION_MAX_BYTES, true),
                Pair(1L, false)
            ),

            listOf(
                Pair(TEST_SESSION_MAX_BYTES + 1, false)
            ),

            listOf(
                Pair(TEST_SESSION_MAX_BYTES * 2, false)
            )
        )

        testCases.forEach { listOfBytesAndResults ->
            Given("TotalBytesCounter with 10 minutes, 10 KBytes and started session") {
                val totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                    sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                    sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                    mockedTime = mockedTime,
                    currentTime = CURRENT_TIME
                )

                val sequenceOfBytes = listOfBytesAndResults.map { it.first }
                val expectedValue = listOfBytesAndResults.map { it.second }

                When("Send track bytes in sequence of bytes $sequenceOfBytes") {
                    val actualValue = sequenceOfBytes.map { bytes ->
                        totalBytesCounter10Min10kBytes.trackSentBytes(bytes)
                    }

                    Then("It should be $expectedValue") {
                        assertEquals(expectedValue, actualValue)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check trackSentBytes with several sessions`() = TestCase {
        val mockedTime = mockk<Time>()

        lateinit var totalBytesCounter10Min10kBytes: TotalBytesCounter

        Given("TotalBytesCounter with 10 minutes, 10 KBytes and started session") {
            totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                mockedTime = mockedTime,
                currentTime = CURRENT_TIME
            )

            val successfulSequenceOfBytes = listOf(1_000L, 5_000L, 4_000L)
            val unsuccessfulSequenceOfBytes = listOf(1_000L, 1L, 2_000L)

            When("Call methods") {
                Then("Successfully send sequence of bytes $successfulSequenceOfBytes") {
                    successfulSequenceOfBytes.forEach { bytes ->
                        assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(bytes))
                    }
                }

                Then("Unsuccessfully send sequence of bytes $unsuccessfulSequenceOfBytes") {
                    unsuccessfulSequenceOfBytes.forEach { bytes ->
                        assertFalse(totalBytesCounter10Min10kBytes.trackSentBytes(bytes))
                    }
                }

                Then("Move time to the next session") {
                    every { mockedTime.millis() }.returns(CURRENT_TIME + TEST_SESSION_LENGTH_MS)
                }

                Then("Successfully send sequence of bytes $successfulSequenceOfBytes") {
                    successfulSequenceOfBytes.forEach { bytes ->
                        assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(bytes))
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check trackSentBytes persistence`() = TestCase {
        val mockedTime = mockk<Time>()

        val testCases = listOf(
            PersistenceCase(5_000L, TimeUnit.MINUTES.toMillis(5), 5_000L, true),
            PersistenceCase(7_000L, TimeUnit.MINUTES.toMillis(8), 3_000L, true),
            PersistenceCase(2_000L, TimeUnit.MINUTES.toMillis(5), 6_000L, true),
            PersistenceCase(9_000L, TimeUnit.MINUTES.toMillis(15), 6_000L, true),
            PersistenceCase(9_000L, TimeUnit.MINUTES.toMillis(11), 10_000L, true),
            PersistenceCase(10_000L, TimeUnit.MINUTES.toMillis(100), 10_000L, true),
            PersistenceCase(1_000L, TimeUnit.MINUTES.toMillis(1), 9_000L, true),
            PersistenceCase(6_000L, TimeUnit.MINUTES.toMillis(11), 6_000L, true),
            PersistenceCase(5_000L, TimeUnit.MINUTES.toMillis(40), 6_000L, true),

            PersistenceCase(9_000L, TimeUnit.MINUTES.toMillis(2), 2_000L, false),
            PersistenceCase(8_000L, TimeUnit.MINUTES.toMillis(1), 2_500L, false),
            PersistenceCase(5_000L, TimeUnit.MINUTES.toMillis(5), 6_000L, false),
            PersistenceCase(10_000L, TimeUnit.MINUTES.toMillis(5), 1L, false),
            PersistenceCase(10_000L, TimeUnit.MINUTES.toMillis(1), 1_000L, false),
            PersistenceCase(10_000L, TimeUnit.MINUTES.toMillis(9), 10_000L, false)
        )

        testCases.forEach { persistenceCase ->
            val sessionPrefs = TotalBytesCounterStorageTestImpl()

            Given("First TotalBytesCounter and already sent ${persistenceCase.sentBytes} bytes") {
                every { mockedTime.millis() }.returns(CURRENT_TIME)

                val firstTotalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                    sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                    sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                    mockedTime = mockedTime,
                    currentTime = CURRENT_TIME,
                    totalBytesCounterPrefs = sessionPrefs
                )

                assertTrue(firstTotalBytesCounter10Min10kBytes.trackSentBytes(persistenceCase.sentBytes))

                When("Start second TotalBytesCounter after ${persistenceCase.nextSessionStartTime / (1000 * 60)}s and send ${persistenceCase.tryToSentBytes} bytes") {
                    every { mockedTime.millis() }.returns(CURRENT_TIME + persistenceCase.nextSessionStartTime)

                    val secondTotalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                        sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                        sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                        mockedTime = mockedTime,
                        currentTime = CURRENT_TIME + persistenceCase.nextSessionStartTime,
                        totalBytesCounterPrefs = sessionPrefs
                    )

                    val actualValue =
                        secondTotalBytesCounter10Min10kBytes.trackSentBytes(persistenceCase.tryToSentBytes)

                    Then("TrackSentBytes should be ${persistenceCase.expectedValue}") {
                        assertEquals(persistenceCase.expectedValue, actualValue)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check 3 sessions`() = TestCase {
        val mockedTime = mockk<Time>()
        val sessionPrefs = TotalBytesCounterStorageTestImpl()
        Given("Send 5000 bytes") {
            every { mockedTime.millis() }.returns(CURRENT_TIME)

            val firstTotalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                mockedTime = mockedTime,
                currentTime = CURRENT_TIME,
                totalBytesCounterPrefs = sessionPrefs
            )

            assertTrue(firstTotalBytesCounter10Min10kBytes.trackSentBytes(5_000L))
            When("Move to 40 minutes and send 5000 bytes") {

                every { mockedTime.millis() }.returns(CURRENT_TIME + TimeUnit.MINUTES.toMillis(40))

                val secondTotalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(
                    sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                    sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                    mockedTime = mockedTime,
                    currentTime = CURRENT_TIME + TimeUnit.MINUTES.toMillis(40),
                    totalBytesCounterPrefs = sessionPrefs
                )

                assertTrue(secondTotalBytesCounter10Min10kBytes.trackSentBytes(5_000L))

                Then("Move to 45 minutes and try send 6000 bytes") {
                    every { mockedTime.millis() }.returns(CURRENT_TIME + TimeUnit.MINUTES.toMillis(45))

                    val secondTotalBytesCounter10Min10kBytes3 = getTotalBytesCounterWithStartedSession(
                        sessionLengthMillis = TEST_SESSION_LENGTH_MS,
                        sessionMaxBytes = TEST_SESSION_MAX_BYTES,
                        mockedTime = mockedTime,
                        currentTime = CURRENT_TIME + TimeUnit.MINUTES.toMillis(45),
                        totalBytesCounterPrefs = sessionPrefs
                    )

                    assertFalse(secondTotalBytesCounter10Min10kBytes3.trackSentBytes(6_000L))
                }
            }
        }
    }
}

private fun getTotalBytesCounterWithStartedSession(
    sessionLengthMillis: Long,
    sessionMaxBytes: Long,
    mockedTime: Time,
    currentTime: Long,
    totalBytesCounterPrefs: TotalBytesCounterPrefs = TotalBytesCounterStorageTestImpl()
): TotalBytesCounter {
    every { mockedTime.millis() }.returns(currentTime)
    val result = TotalBytesCounter.Impl(
        sessionLengthMillis = sessionLengthMillis,
        sessionMaxBytes = sessionMaxBytes,
        currentTime = mockedTime,
        totalBytesCounterPrefs = totalBytesCounterPrefs
    )

    result.trackSentBytes(0)
    return result
}

private data class PersistenceCase(
    val sentBytes: Long,
    val nextSessionStartTime: Long,
    val tryToSentBytes: Long,
    val expectedValue: Boolean
)

private class TestPreference<T> : Preference<T> {

    private var value: T? = null

    override val preferences: SharedPreferences
        get() = throw Exception("Do nothing")
    override val key: String
        get() = throw Exception("Do nothing")
    override val adapter: Preference.Adapter<T>
        get() = throw Exception("Do nothing")

    override fun get() = value
    override fun set(value: T) {
        this.value = value
    }
}

private class TotalBytesCounterStorageTestImpl : TotalBytesCounterPrefs {
    override val sessionStartMillis = TestPreference<Long>()
    override val bytesSentPerSession = TestPreference<Long>()
}
