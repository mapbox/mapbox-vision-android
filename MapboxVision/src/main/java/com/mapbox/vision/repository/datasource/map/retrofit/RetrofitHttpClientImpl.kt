package com.mapbox.vision.repository.datasource.map.retrofit

import com.mapbox.vision.core.map.HttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

internal class RetrofitHttpClientImpl : HttpClient {

    override lateinit var onSuccessCallback: (response: String, url: String) -> Unit
    override lateinit var onFailCallback: (error: String) -> Unit

    private val retrofitService = Retrofit.Builder()
            .baseUrl("http://mapbox.com")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
            .create(RetrofitHttpClient::class.java)

    override fun httpGet(url: String) {
        val call = retrofitService.httpGet(url)
        call.enqueue(object : Callback<String> {
            override fun onFailure(call: Call<String>, t: Throwable) {
                if (!::onFailCallback.isInitialized) {
                    return
                }
                onFailCallback.invoke(t.localizedMessage)
            }

            override fun onResponse(call: Call<String>, response: Response<String>) {
                if (!::onSuccessCallback.isInitialized) {
                    return
                }
                onSuccessCallback.invoke(
                        response.body() ?: "", response.raw().request().url().toString()
                )
            }
        })
    }

    override fun httpPost(url: String, data: String) {
        val call = retrofitService.httpPost(url, data)
        call.enqueue(object : Callback<String> {
            override fun onFailure(call: Call<String>, t: Throwable) {}

            override fun onResponse(call: Call<String>, response: Response<String>) {}
        })
    }
}
