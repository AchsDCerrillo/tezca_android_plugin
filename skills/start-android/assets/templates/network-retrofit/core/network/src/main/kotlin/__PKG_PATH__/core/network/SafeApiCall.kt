package __PKG__.core.network

import java.io.IOException
import java.io.InterruptedIOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import retrofit2.Response
import __PKG__.core.common.result.AppError
import __PKG__.core.common.result.DataResult

/**
 * Runs a Retrofit call and maps the outcome to [DataResult], so transport errors and HTTP status
 * codes are translated in exactly one place. Service methods return `Response<T>`:
 *
 * ```
 * @GET("orders/{id}") suspend fun getOrder(@Path("id") id: Long): Response<OrderDto>
 * override suspend fun getOrder(id: Long) = safeApiCall { api.getOrder(id) }.mapDto { it.toDomain() }
 * ```
 * For endpoints without a body (204) use `Response<Unit>`: success becomes `DataResult.Success(Unit)`.
 */
suspend inline fun <reified T : Any> safeApiCall(crossinline block: suspend () -> Response<T>): DataResult<T> {
    val response = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: InterruptedIOException) {
        return DataResult.Failure(AppError.Network.Timeout)
    } catch (e: IOException) {
        return DataResult.Failure(AppError.Network.NoInternet)
    } catch (e: SerializationException) {
        return DataResult.Failure(AppError.Network.Serialization)
    } catch (e: Exception) {
        return DataResult.Failure(AppError.Unknown(e.message))
    }
    return response.toDataResult()
}

inline fun <reified T : Any> Response<T>.toDataResult(): DataResult<T> {
    val code = code()
    return when {
        isSuccessful -> {
            val body = body()
            when {
                body != null -> DataResult.Success(body)
                T::class == Unit::class -> DataResult.Success(Unit as T)
                else -> DataResult.Failure(AppError.Network.Serialization)
            }
        }
        code == 401 -> DataResult.Failure(AppError.Network.Unauthorized)
        code == 403 -> DataResult.Failure(AppError.Network.Forbidden)
        code == 404 -> DataResult.Failure(AppError.Network.NotFound)
        code in 400..499 -> DataResult.Failure(AppError.Network.Client(code, errorBodyOrNull()))
        code in 500..599 -> DataResult.Failure(AppError.Network.Server(code, errorBodyOrNull()))
        else -> DataResult.Failure(AppError.Unknown("Unexpected HTTP $code"))
    }
}

fun Response<*>.errorBodyOrNull(): String? = runCatching { errorBody()?.string() }.getOrNull()

/**
 * Maps a successful DTO result to a domain model. A mapper that throws (bad date, unknown enum)
 * becomes [AppError.Network.Serialization] instead of crashing the caller.
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
