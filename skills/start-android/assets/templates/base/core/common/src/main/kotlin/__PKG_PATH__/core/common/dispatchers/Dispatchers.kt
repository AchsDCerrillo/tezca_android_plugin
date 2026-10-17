package __PKG__.core.common.dispatchers

import javax.inject.Qualifier

/**
 * Inject dispatchers instead of referencing `Dispatchers.IO` directly, so tests can swap them for
 * a `TestDispatcher`:
 *
 * ```
 * class UserRepositoryImpl @Inject constructor(@Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher)
 * ```
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Dispatcher(val dispatcher: AppDispatcher)

enum class AppDispatcher { IO, Default, Main }

/** Long-lived scope that survives screens (fire-and-forget sync, writes that must finish). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
