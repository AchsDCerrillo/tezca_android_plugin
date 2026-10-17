package __PKG__.core.common.result

import kotlin.coroutines.cancellation.CancellationException

/**
 * Result of any data operation (network, database, preferences). Repositories and use cases return
 * it instead of throwing, so every caller handles failures exhaustively with `when`.
 */
sealed interface DataResult<out T> {
    data class Success<out T>(val data: T) : DataResult<T>
    data class Failure(val error: AppError) : DataResult<Nothing>
}

inline fun <T, R> DataResult<T>.map(transform: (T) -> R): DataResult<R> = when (this) {
    is DataResult.Success -> DataResult.Success(transform(data))
    is DataResult.Failure -> this
}

inline fun <T, R> DataResult<T>.flatMap(transform: (T) -> DataResult<R>): DataResult<R> = when (this) {
    is DataResult.Success -> transform(data)
    is DataResult.Failure -> this
}

inline fun <T> DataResult<T>.onSuccess(action: (T) -> Unit): DataResult<T> {
    if (this is DataResult.Success) action(data)
    return this
}

inline fun <T> DataResult<T>.onFailure(action: (AppError) -> Unit): DataResult<T> {
    if (this is DataResult.Failure) action(error)
    return this
}

fun <T> DataResult<T>.getOrNull(): T? = (this as? DataResult.Success)?.data

fun <T> DataResult<T>.errorOrNull(): AppError? = (this as? DataResult.Failure)?.error

fun DataResult<*>.asEmpty(): DataResult<Unit> = map { }

/**
 * Runs a local operation (Room, DataStore, file) and maps exceptions to [AppError.Local.Database].
 * Cancellation is rethrown so structured concurrency keeps working.
 */
inline fun <T> localCall(block: () -> T): DataResult<T> = try {
    DataResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    DataResult.Failure(AppError.Local.Database(e.message))
}
