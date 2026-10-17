package __PKG__.core.network

import __PKG__.core.common.result.AppError
import __PKG__.core.common.result.DataResult
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.ContentConvertException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

/**
 * Runs a Ktor call and maps the outcome to [DataResult]. Every API implementation goes through it,
 * so transport errors and HTTP status codes are translated in exactly one place.
 *
 * ```
 * override suspend fun getUser(id: Long): DataResult<UserDto> = safeCall { client.get(UsersResource.Id(id = id)) }
 * ```
 */
suspend inline fun <reified T> safeCall(block: () -> HttpResponse): DataResult<T> {
    val response = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpRequestTimeoutException) {
        return DataResult.Failure(AppError.Network.Timeout)
    } catch (e: ConnectTimeoutException) {
        return DataResult.Failure(AppError.Network.Timeout)
    } catch (e: SocketTimeoutException) {
        return DataResult.Failure(AppError.Network.Timeout)
    } catch (e: IOException) {
        return DataResult.Failure(AppError.Network.NoInternet)
    } catch (e: SerializationException) {
        return DataResult.Failure(AppError.Network.Serialization)
    } catch (e: ContentConvertException) {
        return DataResult.Failure(AppError.Network.Serialization)
    } catch (e: Exception) {
        return DataResult.Failure(AppError.Unknown(e.message))
    }
    return response.toDataResult()
}

suspend inline fun <reified T> HttpResponse.toDataResult(): DataResult<T> =
    when (status.value) {
        in 200..299 -> try {
            DataResult.Success(body<T>())
        } catch (e: SerializationException) {
            DataResult.Failure(AppError.Network.Serialization)
        } catch (e: NoTransformationFoundException) {
            DataResult.Failure(AppError.Network.Serialization)
        } catch (e: ContentConvertException) {
            // Ktor 3 wraps kotlinx.serialization errors in JsonConvertException.
            DataResult.Failure(AppError.Network.Serialization)
        }
        401 -> DataResult.Failure(AppError.Network.Unauthorized)
        403 -> DataResult.Failure(AppError.Network.Forbidden)
        404 -> DataResult.Failure(AppError.Network.NotFound)
        in 400..499 -> DataResult.Failure(AppError.Network.Client(status.value, bodyAsTextOrNull()))
        in 500..599 -> DataResult.Failure(AppError.Network.Server(status.value, bodyAsTextOrNull()))
        else -> DataResult.Failure(AppError.Unknown("Unexpected HTTP ${status.value}"))
    }

/**
 * Maps a successful DTO result to a domain model. A mapper that throws (bad date, unknown enum)
 * becomes [AppError.Network.Serialization] instead of crashing the caller.
 *
 * ```
 * override suspend fun getUser(id: Long) = api.getUser(id).mapDto { it.toDomain() }
 * ```
 */
inline fun <T, R> DataResult<T>.mapDto(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> try {
        DataResult.Success(transform(data))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DataResult.Failure(AppError.Network.Serialization)
    }
    is DataResult.Failure -> this
}

@PublishedApi
internal suspend fun HttpResponse.bodyAsTextOrNull(): String? = runCatching { bodyAsText() }.getOrNull()
