package __PKG__.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Retries requests that are safe to repeat (GET, HEAD, OPTIONS, PUT, DELETE) on 5xx / 429 or I/O
 * failures, with exponential backoff. POST/PATCH are never retried automatically: repeating them
 * can create duplicates (two orders, two payments).
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 300,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method !in IDEMPOTENT) return chain.proceed(request)

        var attempt = 0
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (attempt >= maxRetries || chain.call().isCanceled()) throw e
                null
            }
            if (response != null && (!response.isRetryable() || attempt >= maxRetries)) return response
            response?.close()
            Thread.sleep(baseDelayMs shl attempt)
            attempt++
        }
    }

    private fun Response.isRetryable() = code in 500..599 || code == 429

    private companion object {
        val IDEMPOTENT = setOf("GET", "HEAD", "OPTIONS", "PUT", "DELETE")
    }
}
