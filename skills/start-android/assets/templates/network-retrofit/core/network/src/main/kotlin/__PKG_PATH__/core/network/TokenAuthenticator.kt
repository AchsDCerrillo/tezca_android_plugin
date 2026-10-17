package __PKG__.core.network

import java.util.Optional
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Refreshes the token once after a 401 and retries the request. OkHttp calls this on a background
 * network thread, the one place where blocking is expected; requests themselves never block on the
 * token. Concurrent 401s share one refresh: whoever enters second sees the token already changed.
 */
class TokenAuthenticator @Inject constructor(
    private val tokenProvider: Optional<TokenProvider>,
) : Authenticator {
    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val provider = tokenProvider.orElse(null) ?: return null
        if (response.priorResponse != null) return null // already retried once: give up
        val sentToken = response.request.header(AuthInterceptor.AUTHORIZATION)?.removePrefix("Bearer ")

        val token = synchronized(lock) {
            val current = provider.currentAccessToken()
            if (current != null && current != sentToken) current else runBlocking { provider.refreshAccessToken() }
        } ?: return null

        return response.request.newBuilder().header(AuthInterceptor.AUTHORIZATION, "Bearer $token").build()
    }
}
