package com.mapbox.vision.telemetry

import com.mapbox.vision.utils.system.Time
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import java.util.concurrent.TimeUnit

const val DEFAULT_SESSION_MAX_BYTES = 10_000L
val DEFAULT_SESSION_LENGTH_MS = TimeUnit.MINUTES.toMillis(10)
val CURRENT_TIME = TimeUnit.HOURS.toMillis(20)


object TotalBytesCounterTest : Spek({
    Feature("TotalBytesCounter") {

        Scenario("Check default session size") {
            lateinit var totalBytesCounter: TotalBytesCounter
            val mockedTime = mockk<Time>()

            Given("TotalBytesCounter with default params") {
                totalBytesCounter = TotalBytesCounter.Impl(time = mockedTime)
            }

            var actualValue = false

            When("Get fitInLimit for 30MB") {
                actualValue = totalBytesCounter.fitInLimit(30 * 1024 * 1024L)
            }

            Then("It should be <true>") {
                assertEquals(true, actualValue)
            }

            When("Get fitInLimit for 30MB and 1 byte") {
                actualValue = totalBytesCounter.fitInLimit(30 * 1024 * 1024L + 1)
            }

            Then("It should be <false>") {
                assertEquals(false, actualValue)
            }
        }


        Scenario("Check default session length") {
            lateinit var totalBytesCounter: TotalBytesCounter
            val mockedTime = mockk<Time>()
            every { mockedTime.millis() }.returns(0)

            Given("TotalBytesCounter with default params") {
                totalBytesCounter = TotalBytesCounter.Impl(time = mockedTime)
            }

            var actualValue = 0L

            When("Get millisToNextSession") {
                actualValue = totalBytesCounter.millisToNextSession()
            }

            Then("It should be <1 hour>") {
                assertEquals(TimeUnit.HOURS.toMillis(1), actualValue)
            }
        }


        Scenario("Check fitInLimit") {

            lateinit var totalBytesCounter10Min10kBytes: TotalBytesCounter
            val mockedTime = mockk<Time>()

            val testCases = mapOf(
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
            )

            Given("TotalBytesCounter with 10 minutes, 10 KBytes params") {
                totalBytesCounter10Min10kBytes = TotalBytesCounter.Impl(
                    sessionLengthMillis = DEFAULT_SESSION_LENGTH_MS,
                    sessionMaxBytes = DEFAULT_SESSION_MAX_BYTES,
                    time = mockedTime
                )
            }

            testCases.forEach { (bytes, expectedValue) ->
                var actualValue = false

                When("Get FitInLimit for $bytes bytes") {
                    actualValue = totalBytesCounter10Min10kBytes.fitInLimit(bytes)
                }


                Then("It should be <$expectedValue>") {
                    assertEquals(expectedValue, actualValue)
                }
            }
        }


        Scenario("Check millisToNextSession") {

            lateinit var totalBytesCounter10Min10kBytes: TotalBytesCounter
            val mockedTime = mockk<Time>()

            val testCases = mapOf(
                TimeUnit.MINUTES.toMillis(0) to TimeUnit.MINUTES.toMillis(10),
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
                TimeUnit.MINUTES.toMillis(11) to TimeUnit.MINUTES.toMillis(0),
                TimeUnit.MINUTES.toMillis(25) to TimeUnit.MINUTES.toMillis(0)
            )

            Given("TotalBytesCounter with 10 minutes, 10 KBytes and started session") {
                totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(mockedTime)
            }

            testCases.forEach { (requestTime, expectedValue) ->
                var actualValue = 0L

                When("Get millisToNextSession for $requestTime ms") {

                    every { mockedTime.millis() }.returns(CURRENT_TIME + requestTime)

                    actualValue = totalBytesCounter10Min10kBytes.millisToNextSession()
                }

                Then("It should be <$expectedValue> ms") {
                    assertEquals(expectedValue, actualValue)
                }
            }
        }


        Scenario("Check trackSentBytes") {

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
                    Pair(DEFAULT_SESSION_MAX_BYTES - 1, true),
                    Pair(1L, true),
                    Pair(1L, false)
                ),

                listOf(
                    Pair(DEFAULT_SESSION_MAX_BYTES, true),
                    Pair(1L, false)
                ),

                listOf(
                    Pair(DEFAULT_SESSION_MAX_BYTES + 1, false)
                ),

                listOf(
                    Pair(DEFAULT_SESSION_MAX_BYTES * 2, false)
                )
            )

            testCases.forEach { listOfBytesAndResults ->

                lateinit var totalBytesCounter10Min10kBytes: TotalBytesCounter

                Given("TotalBytesCounter with 10 minutes, 10 KBytes and started session") {
                    totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(mockedTime)
                }

                val sequenceOfBytes = listOfBytesAndResults.map { it.first }
                val expectedValue = listOfBytesAndResults.map { it.second }
                lateinit var actualValue: List<Boolean>

                When("Send track bytes in sequence of bytes $sequenceOfBytes") {

                    actualValue = sequenceOfBytes.map { bytes ->
                        totalBytesCounter10Min10kBytes.trackSentBytes(bytes)
                    }
                }

                Then("It should be $expectedValue") {
                    assertEquals(expectedValue, actualValue)
                }
            }
        }

        Scenario("Check trackSentBytes with several sessions") {

            val mockedTime = mockk<Time>()

            lateinit var totalBytesCounter10Min10kBytes: TotalBytesCounter

            Given("TotalBytesCounter with 10 minutes, 10 KBytes and started session") {
                totalBytesCounter10Min10kBytes = getTotalBytesCounterWithStartedSession(mockedTime)
            }

            val successfulSequenceOfBytes = listOf(1_000L, 5_000L, 4_000L)
            val unsuccessfulSequenceOfBytes = listOf(1_000L, 1L, 2_000L)

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
                every { mockedTime.millis() }.returns(CURRENT_TIME + DEFAULT_SESSION_LENGTH_MS)
            }

            Then("Successfully send sequence of bytes $successfulSequenceOfBytes") {
                successfulSequenceOfBytes.forEach { bytes ->
                    assertTrue(totalBytesCounter10Min10kBytes.trackSentBytes(bytes))
                }
            }
        }
    }
})

private fun getTotalBytesCounterWithStartedSession(mockedTime: Time): TotalBytesCounter {

    val result = TotalBytesCounter.Impl(
        sessionLengthMillis = DEFAULT_SESSION_LENGTH_MS,
        sessionMaxBytes = DEFAULT_SESSION_MAX_BYTES,
        time = mockedTime
    )
    every { mockedTime.millis() }.returns(CURRENT_TIME)
    assertEquals(result.millisToNextSession(), 0)

    result.trackSentBytes(0)
    return result
}