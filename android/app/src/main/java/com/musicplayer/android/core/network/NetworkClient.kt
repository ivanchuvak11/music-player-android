package com.musicplayer.android.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

class AuthInterceptor : Interceptor {
    @Volatile
    var authToken: String? = null

    var onUnauthorizedListener: (() -> Unit)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val token = authToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())
        if (response.code == 401 && !token.isNullOrBlank()) {
            onUnauthorizedListener?.invoke()
        }
        return response
    }
}

object NetworkClient {
    // 10.0.2.2 points to localhost of host machine in Android Emulator (port 5116)
    const val DEFAULT_BASE_URL = "http://10.0.2.2:5116/"

    val authInterceptor = AuthInterceptor()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun createService(baseUrl: String = DEFAULT_BASE_URL): MusicApiService {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MusicApiService::class.java)
    }
}
