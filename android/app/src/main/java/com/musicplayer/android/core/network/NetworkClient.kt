package com.musicplayer.android.core.network

import android.content.Context
import android.content.pm.ApplicationInfo
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

class AuthInterceptor : Interceptor {
    @Volatile
    var authToken: String? = null

    var onUnauthorizedListener: (() -> Unit)? = null

    private val isHandling401 = java.util.concurrent.atomic.AtomicBoolean(false)

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        val token = authToken
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())
        if (response.code == 401 && !token.isNullOrBlank()) {
            if (isHandling401.compareAndSet(false, true)) {
                try {
                    onUnauthorizedListener?.invoke()
                } finally {
                    // Reset after 2 seconds to avoid rapid duplicate firings
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        isHandling401.set(false)
                    }, 2000L)
                }
            }
        }
        return response
    }
}

object NetworkClient {
    val DEFAULT_BASE_URL: String
        get() = ServerConfig.DEFAULT_LOCAL_BASE_URL

    val authInterceptor = AuthInterceptor()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Volatile
    private var okHttpClient: OkHttpClient? = null

    fun getOkHttpClient(context: Context? = null): OkHttpClient {
        return okHttpClient ?: synchronized(this) {
            okHttpClient ?: run {
                val isDebug = try {
                    context != null && (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                } catch (e: Exception) {
                    false
                }

                val loggingLevel = if (isDebug) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }

                val builder = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .addInterceptor(HttpLoggingInterceptor().apply {
                        level = loggingLevel
                    })
                    .connectTimeout(15, TimeUnit.SECONDS)
                    .readTimeout(15, TimeUnit.SECONDS)

                if (context != null) {
                    try {
                        val cacheDir = File(context.cacheDir, "http_cache")
                        val cacheSize = 10L * 1024 * 1024 // 10 MB
                        builder.cache(Cache(cacheDir, cacheSize))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                builder.build().also { okHttpClient = it }
            }
        }
    }

    fun createService(baseUrl: String = DEFAULT_BASE_URL, context: Context? = null): MusicApiService {
        val contentType = "application/json".toMediaType()
        val client = getOkHttpClient(context)
        val validBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(validBaseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
            .create(MusicApiService::class.java)
    }
}
