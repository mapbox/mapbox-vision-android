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

    @TestFactory
    fun `Check get host`() = TestCase {
        Given("Object TelemetryEnvironment") {
            mapOf(
                China to "events.mapbox.cn",
                Other to "events.mapbox.com",
                UK to "events.mapbox.com",
                USA to "events.mapbox.com",
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
                China to "Recordings_China",
                Other to "Recordings_Other",
                UK to "Recordings_Other",
                USA to "Recordings_Other",
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
