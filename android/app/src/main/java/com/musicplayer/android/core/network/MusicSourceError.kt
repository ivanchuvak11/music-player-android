package com.musicplayer.android.core.network

/**
 * Sealed class representing all possible typed errors from the music sources.
 * Extends Exception so it works with kotlin.Result<T> (Result.failure requires Throwable).
 *
 * Each error type maps to a specific ТЗ Section 8 requirement:
 *   - 401 Unauthorized
 *   - 404 Not Found
 *   - 429 Too Many Requests
 *   - Timeout
 *   - No network
 *   - Unavailable stream
 *   - Empty response
 *   - Session recovery failure
 */
sealed class MusicSourceError(msg: String) : Exception(msg) {
    /** The music source that produced this error (soundcloud / audius / jamendo / all) */
    abstract val source: String

    class Unauthorized(
        override val source: String
    ) : MusicSourceError("401 Unauthorized — token invalid for $source")

    class NotFound(
        override val source: String,
        val resourceId: String = ""
    ) : MusicSourceError("404 Not Found for $source (id=$resourceId)")

    class TooManyRequests(
        override val source: String,
        val retryAfterMs: Long = 30_000L
    ) : MusicSourceError("429 Too Many Requests for $source, retry after ${retryAfterMs}ms")

    class Timeout(
        override val source: String
    ) : MusicSourceError("Request timeout for $source")

    class NoNetwork(
        override val source: String = "all"
    ) : MusicSourceError("No network connection")

    class StreamUnavailable(
        override val source: String,
        val streamUrl: String = ""
    ) : MusicSourceError("Stream unavailable for $source: $streamUrl")

    class EmptyResponse(
        override val source: String
    ) : MusicSourceError("Empty response from $source")

    class SessionRecoveryFailed(
        override val source: String = "auth"
    ) : MusicSourceError("Session recovery failed — please log in again")

    class HttpError(
        override val source: String,
        val code: Int,
        val errorDetail: String = ""
    ) : MusicSourceError("HTTP $code error from $source: $errorDetail")

    class Unknown(
        override val source: String,
        val detail: String = ""
    ) : MusicSourceError("Unknown error from $source: $detail")
}

/**
 * Maps an HTTP status code to a typed MusicSourceError.
 */
fun httpCodeToMusicError(code: Int, source: String, resourceId: String = ""): MusicSourceError =
    when (code) {
        401      -> MusicSourceError.Unauthorized(source)
        404      -> MusicSourceError.NotFound(source, resourceId)
        429      -> MusicSourceError.TooManyRequests(source)
        408, 504 -> MusicSourceError.Timeout(source)
        else     -> MusicSourceError.HttpError(source, code)
    }
