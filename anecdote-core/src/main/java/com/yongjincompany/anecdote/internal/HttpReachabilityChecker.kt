package com.yongjincompany.anecdote.internal

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class HttpReachabilityResult(
    val reachable: Boolean,
    val latencyMs: Long?,
)

internal fun interface HttpReachabilityChecker {
    suspend fun check(url: String): HttpReachabilityResult
}

internal class OkHttpReachabilityChecker(
    private val client: OkHttpClient,
    private val clock: Clock = SystemClock,
) : HttpReachabilityChecker {

    override suspend fun check(url: String): HttpReachabilityResult {
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        val start = clock.now()
        return try {
            client.newCall(request).await().use { response ->
                HttpReachabilityResult(
                    reachable = isServerResponse(response.code),
                    latencyMs = clock.now() - start,
                )
            }
        } catch (e: IOException) {
            HttpReachabilityResult(reachable = false, latencyMs = null)
        }
    }

    /**
     * Any HTTP response code (even 4xx/5xx) means the server was reached.
     * Only transport-level failures (DNS, connection refused, timeout) count as unreachable.
     */
    private fun isServerResponse(code: Int): Boolean = code in 100..599

    internal companion object {
        fun defaultClient(timeoutMs: Long): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { runCatching { cancel() } }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response)
        }
    })
}
