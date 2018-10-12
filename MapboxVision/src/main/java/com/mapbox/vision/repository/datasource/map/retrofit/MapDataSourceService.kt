package com.mapbox.vision.repository.datasource.map.retrofit

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

internal interface MapDataSourceService {
    @GET
    fun getMatchedGPSData(@Url url: String): Call<String>
}
