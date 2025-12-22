package com.androidmanager.data.remote

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Network module for API client configuration
 */
object NetworkModule {

    private const val TAG = "NetworkModule"
    private const val DEFAULT_TIMEOUT = 30L

    private var retrofit: Retrofit? = null
    private var apiService: ApiService? = null
    private var currentBaseUrl: String? = null

    /**
     * Initialize the API client with a base URL
     */
    fun initialize(baseUrl: String) {
        if (baseUrl == currentBaseUrl && retrofit != null) {
            return
        }

        Log.d(TAG, "Initializing API client with URL: $baseUrl")

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit?.create(ApiService::class.java)
        currentBaseUrl = baseUrl
    }

    /**
     * Get the API service instance
     */
    fun getApiService(): ApiService {
        return apiService ?: throw IllegalStateException(
            "NetworkModule not initialized. Call initialize() first."
        )
    }

    /**
     * Check if the network module is initialized
     */
    fun isInitialized(): Boolean = apiService != null

    /**
     * Reset the network module
     */
    fun reset() {
        retrofit = null
        apiService = null
        currentBaseUrl = null
    }
}
