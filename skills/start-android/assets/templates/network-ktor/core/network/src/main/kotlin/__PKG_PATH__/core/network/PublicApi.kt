package __PKG__.core.network

import javax.inject.Qualifier

/**
 * Qualifies the network client that never sends the session token and never tries to refresh it.
 * Use it for endpoints called without a session (login, register, password reset, token refresh):
 * a 401 there means "wrong credentials", not "session expired".
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class PublicApi
