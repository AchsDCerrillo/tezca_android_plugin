package __PKG__.core.sdk

import java.io.IOException
import java.net.SocketTimeoutException
import kotlin.coroutines.cancellation.CancellationException
import __PKG__.core.common.result.AppError

/**
 * Maps whatever the SDK reports as a failure (its result wrapper usually carries a raw Throwable)
 * to the app's [AppError], so ViewModels show errors with `AppError.toUiText()` like everywhere
 * else. Extend the `when` with the SDK's own exception types (unauthorized, server response with
 * a code…) as you meet them.
 */
fun Throwable?.toAppError(): AppError = when (this) {
    null -> AppError.Unknown(null)
    is CancellationException -> throw this
    is SocketTimeoutException -> AppError.Network.Timeout
    is IOException -> AppError.Network.NoInternet
    else -> when {
        // Ktor exceptions surface with these class names without the app depending on Ktor.
        this::class.simpleName?.contains("Timeout") == true -> AppError.Network.Timeout
        this::class.simpleName?.contains("UnresolvedAddress") == true -> AppError.Network.NoInternet
        else -> AppError.Unknown(message)
    }
}
