package __PKG__.core.common.result

/** Every failure the data layer can report. The UI maps it to text (core:ui `AppError.toUiText()`). */
sealed interface AppError {

    sealed interface Network : AppError {
        data object NoInternet : Network
        data object Timeout : Network
        data object Unauthorized : Network
        data object Forbidden : Network
        data object NotFound : Network
        data object Serialization : Network
        data class Client(val code: Int, val body: String?) : Network
        data class Server(val code: Int, val body: String?) : Network
    }

    sealed interface Local : AppError {
        data object NotFound : Local
        data class Database(val message: String?) : Local
    }

    /**
     * A business rule failed (invalid coupon, plan already active...). [code] is a stable key the
     * UI maps to a string resource; [message] is optional detail for logs or server-provided text.
     */
    data class Domain(val code: String, val message: String? = null) : AppError

    data class Unknown(val message: String?) : AppError
}
