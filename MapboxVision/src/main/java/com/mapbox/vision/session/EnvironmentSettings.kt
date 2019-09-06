package com.mapbox.vision.session

import com.mapbox.vision.mobile.core.models.Country

internal interface EnvironmentSettings {
    interface CountryChange {
        fun newCountry(country: Country)
    }
}
