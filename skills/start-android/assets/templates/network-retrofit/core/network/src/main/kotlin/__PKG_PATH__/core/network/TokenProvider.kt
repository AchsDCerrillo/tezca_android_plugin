package __PKG__.core.network

/**
 * Supplies the access token to [AuthInterceptor] and refreshes it from [TokenAuthenticator].
 * `:core:network` only declares it (`@BindsOptionalOf`); the feature that owns the session binds
 * an implementation backed by DataStore (see make-preferences). Without one, requests go out
 * unauthenticated.
 */
interface TokenProvider {
    /**
     * Called on OkHttp threads for every request, so it must not block or do I/O: return a value
     * cached in memory (hydrated from DataStore at startup and updated on login/refresh).
     */
    fun currentAccessToken(): String?

    /**
     * Called after a 401 from a background OkHttp thread. Return a fresh access token, or null to
     * give up (the 401 then reaches the caller as AppError.Network.Unauthorized).
     */
    suspend fun refreshAccessToken(): String?
}
