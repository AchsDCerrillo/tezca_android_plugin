package __PKG__.core.network

import java.util.Optional
import javax.inject.Inject
import okhttp3.Interceptor
import okhttp3.Response

/** Adds `Authorization: Bearer <token>` from the in-memory token, without blocking. */
class AuthInterceptor @Inject constructor(
    private val tokenProvider: Optional<TokenProvider>,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val token = tokenProvider.orElse(null)?.currentAccessToken()
        if (token == null || request.header(AUTHORIZATION) != null) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header(AUTHORIZATION, "Bearer $token").build())
    }

    internal companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
