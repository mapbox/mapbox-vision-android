package com.mapbox.vision.sync

import com.mapbox.vision.dsl.TestCase
import com.mapbox.vision.mobile.core.models.Country.China
import com.mapbox.vision.mobile.core.models.Country.Other
import com.mapbox.vision.mobile.core.models.Country.UK
import com.mapbox.vision.mobile.core.models.Country.USA
import com.mapbox.vision.mobile.core.models.Country.Unknown
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.TestFactory

class TelemetryEnvironmentTest {

    companion object {
        private const val HOST_NAME_CHINA = "events.mapbox.cn"
        private const val HOST_NAME_OTHER = "events.mapbox.com"

        private const val BASE_PATH_CHINA = "Recordings_China"
        private const val BASE_PATH_OTHER = "Recordings_Other"
    }

    @TestFactory
    fun `Check get host`() = TestCase {
        Given("Object TelemetryEnvironment") {
            mapOf(
                China to HOST_NAME_CHINA,
                Other to HOST_NAME_OTHER,
                UK to HOST_NAME_OTHER,
                USA to HOST_NAME_OTHER,
                Unknown to null
            ).forEach { (country, expectedHost) ->
                When("Get host name for country $country") {
                    val actualHost = TelemetryEnvironment.getHost(country)
                    Then("Host name should be $expectedHost") {
                        assertEquals(expectedHost, actualHost)
                    }
                }
            }
        }
    }

    @TestFactory
    fun `Check base path`() = TestCase {
        Given("Object TelemetryEnvironment") {
            mapOf(
                China to BASE_PATH_CHINA,
                Other to BASE_PATH_OTHER,
                UK to BASE_PATH_OTHER,
                USA to BASE_PATH_OTHER,
                Unknown to null
            ).forEach { (country, expectedBasePath) ->
                When("Get base path for country $country") {
                    val actualBasePath = TelemetryEnvironment.getBasePath(country)
                    Then("Base path should be $expectedBasePath") {
                        assertEquals(expectedBasePath, actualBasePath)
                    }
                }
            }
        }
    }
}
