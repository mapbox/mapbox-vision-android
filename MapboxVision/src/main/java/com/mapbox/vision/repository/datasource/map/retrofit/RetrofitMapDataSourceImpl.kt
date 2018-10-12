package com.mapbox.vision.repository.datasource.map.retrofit

import com.mapbox.vision.core.map.MapDataSource
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

internal class RetrofitMapDataSourceImpl : MapDataSource {

    override lateinit var onSuccessCallback: (response: String, url: String) -> Unit
    override lateinit var onFailCallback: (error: String) -> Unit
    private val callsMap = HashMap<Call<String>, String>()

    private val retrofitService = Retrofit.Builder()
            .baseUrl("http://mapbox.com")
            .addConverterFactory(ScalarsConverterFactory.create())
            .build().create(MapDataSourceService::class.java)


    override fun matchGPSData(url: String) {
        val call = retrofitService.getMatchedGPSData(url)
        callsMap[call] = url
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
                val mappedUrl = callsMap[call] ?: ""
                onSuccessCallback.invoke(response.body() ?: "", mappedUrl)
            }

        })
    }
}
