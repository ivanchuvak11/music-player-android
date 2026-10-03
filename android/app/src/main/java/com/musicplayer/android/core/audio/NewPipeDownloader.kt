package com.musicplayer.android.core.audio

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Custom OkHttp-based Downloader implementation for NewPipeExtractor.
 * Executes requests directly on the Android device's network (Wi-Fi or cellular),
 * bypassing server-side IP datacenter bans.
 */
class NewPipeDownloader(
    private val client: OkHttpClient = sharedClient
) : Downloader() {

    companion object {
        /**
         * Shared OkHttpClient with connection pooling (up to 8 idle connections kept alive for 5 minutes).
         * Eliminates repetitive TLS handshakes and drastically speeds up search and stream extraction.
         */
        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .followRedirects(true)
                .retryOnConnectionFailure(true)
                .build()
        }
    }

    @Throws(IOException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val reqBuilder = okhttp3.Request.Builder().url(url)
        headers?.forEach { (name, values) ->
            values.forEach { value ->
                reqBuilder.addHeader(name, value)
            }
        }

        // Ensure modern browser user-agent if missing
        if (headers?.keys?.none { it.equals("User-Agent", ignoreCase = true) } == true) {
            reqBuilder.header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
            )
        }

        val body = if (dataToSend != null) {
            val contentType = headers?.get("Content-Type")?.firstOrNull()?.toMediaTypeOrNull()
            dataToSend.toRequestBody(contentType)
        } else if (httpMethod.equals("POST", ignoreCase = true) || httpMethod.equals("PUT", ignoreCase = true)) {
            ByteArray(0).toRequestBody(null)
        } else {
            null
        }

        reqBuilder.method(httpMethod, body)

        val okCall = client.newCall(reqBuilder.build())
        val okResponse = okCall.execute()
        val responseBodyString = okResponse.body?.string().orEmpty()
        val responseHeaders = okResponse.headers.toMultimap()

        return Response(
            okResponse.code,
            okResponse.message,
            responseHeaders,
            responseBodyString,
            okResponse.request.url.toString()
        )
    }
}

