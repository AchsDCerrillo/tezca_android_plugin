package __PKG__.core.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.authProvider
import io.ktor.client.plugins.auth.providers.BearerAuthProvider
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.resources.Resources
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import __PKG__.core.common.config.AppConfig

/**
 * Single place where the Ktor client is configured. The engine is passed in (OkHttp in the app,
 * MockEngine in tests); configuring it inside `engine {}` would silently fall back to the default.
 */
object HttpClientFactory {

    private val idempotentMethods = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options, HttpMethod.Put, HttpMethod.Delete)

    fun create(
        engine: HttpClientEngine,
        config: AppConfig,
        tokenProvider: TokenProvider?,
        json: Json = NetworkJson,
        maxRetries: Int = 2,
    ): HttpClient = HttpClient(engine) {
        // Status codes are mapped to AppError in safeCall, so never throw on non-2xx.
        expectSuccess = false

        install(ContentNegotiation) { json(json) }
        install(Resources)

        install(HttpTimeout) {
            connectTimeoutMillis = 15_000
            requestTimeoutMillis = 30_000
            socketTimeoutMillis = 30_000
        }

        if (maxRetries > 0) install(HttpRequestRetry) {
            // Only retry requests that are safe to repeat: POST/PATCH could create duplicates.
            retryIf(maxRetries = maxRetries) { request, response ->
                request.method in idempotentMethods &&
                    (response.status.value in 500..599 || response.status == HttpStatusCode.TooManyRequests)
            }
            retryOnExceptionIf(maxRetries = maxRetries) { request, _ -> request.method in idempotentMethods }
            exponentialDelay()
        }

        install(Logging) {
            logger = Logger.ANDROID
            level = if (config.isDebug) LogLevel.BODY else LogLevel.NONE
            sanitizeHeader { header -> header == HttpHeaders.Authorization }
        }

        if (tokenProvider != null) install(Auth) {
            bearer {
                loadTokens { tokenProvider.currentAccessToken()?.let { BearerTokens(it, null) } }
                refreshTokens { tokenProvider.refreshAccessToken()?.let { BearerTokens(it, null) } }
                // Send the token up front to our own API instead of waiting for a 401 challenge.
                sendWithoutRequest { request -> request.url.host == Url(config.baseUrl).host }
            }
        }

        defaultRequest {
            url(config.baseUrl)
            contentType(ContentType.Application.Json)
        }
    }
}

/**
 * Ktor's bearer provider caches the tokens it loaded. Call this after logout or a user switch so
 * the next request asks [TokenProvider] again instead of sending the previous user's token.
 */
fun HttpClient.clearAuthTokens() {
    authProvider<BearerAuthProvider>()?.clearToken()
}
