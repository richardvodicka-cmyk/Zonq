package com.example.data.remote

import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface RadioBrowserApi {

    @GET("json/stations")
    suspend fun getStations(
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/topvote/{limit}")
    suspend fun getTopStations(
        @Path("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/topclick/{limit}")
    suspend fun getTopClickStations(
        @Path("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/search")
    suspend fun searchStations(
        @Query("name") name: String? = null,
        @Query("tag") tag: String? = null,
        @Query("country") country: String? = null,
        @Query("countrycode") countryCode: String? = null,
        @Query("language") language: String? = null,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/bycountrycodeexact/{countrycode}")
    suspend fun getStationsByCountryCode(
        @Path("countrycode") countryCode: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/bycountry/{country}")
    suspend fun getStationsByCountry(
        @Path("country") country: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/bytag/{tag}")
    suspend fun getStationsByTag(
        @Path("tag") tag: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/stations/bylanguage/{language}")
    suspend fun getStationsByLanguage(
        @Path("language") language: String,
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 60,
        @Query("hidebroken") hideBroken: Boolean = true,
        @Query("order") order: String = "votes",
        @Query("reverse") reverse: Boolean = true
    ): List<RadioBrowserStationDto>

    @GET("json/url/{stationuuid}")
    suspend fun registerClick(
        @Path("stationuuid") stationUuid: String
    ): ResponseBody

    companion object {
        private const val BASE_URL = "https://de1.api.radio-browser.info/"

        fun create(): RadioBrowserApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", "OnlineRadioApp/1.0 (richardvodicka@gmail.com)")
                        .build()
                    chain.proceed(request)
                }
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create())
                .build()

            return retrofit.create(RadioBrowserApi::class.java)
        }
    }
}
