package __PKG__.core.network

/**
 * Supplies tokens to Ktor's bearer Auth plugin. `:core:network` only declares it
 * (`@BindsOptionalOf`); the feature that owns the session binds an implementation backed by
 * DataStore (see make-preferences). Without one, requests go out unauthenticated.
 */
interface TokenProvider {
    /** Current access token, read from memory (hydrated from DataStore). */
    fun currentAccessToken(): String?

    /** Called after a 401. Return a fresh access token, or null to give up. */
    suspend fun refreshAccessToken(): String?
}
