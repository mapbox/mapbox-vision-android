package com.mapbox.vision.repository.datasource.map.retrofit

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Url

internal interface RetrofitHttpClient {
    @GET
    fun httpGet(@Url url: String): Call<String>

    @POST
    fun httpPost(@Url url: String, @Body data: String): Call<String>
}
