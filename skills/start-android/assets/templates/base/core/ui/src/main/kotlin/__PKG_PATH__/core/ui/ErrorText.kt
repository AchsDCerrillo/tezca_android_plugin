package __PKG__.core.ui

import __PKG__.core.common.result.AppError

/** Default user-facing message for each [AppError]. Features map their own Domain codes first. */
fun AppError.toUiText(): UiText = when (this) {
    AppError.Network.NoInternet -> UiText.Resource(R.string.core_ui_error_no_internet)
    AppError.Network.Timeout -> UiText.Resource(R.string.core_ui_error_timeout)
    AppError.Network.Unauthorized -> UiText.Resource(R.string.core_ui_error_unauthorized)
    AppError.Network.Forbidden -> UiText.Resource(R.string.core_ui_error_forbidden)
    AppError.Network.NotFound, AppError.Local.NotFound -> UiText.Resource(R.string.core_ui_error_not_found)
    is AppError.Network.Server -> UiText.Resource(R.string.core_ui_error_server)
    is AppError.Domain -> message?.let(UiText::Dynamic) ?: UiText.Resource(R.string.core_ui_error_generic)
    AppError.Network.Serialization, is AppError.Network.Client, is AppError.Local.Database, is AppError.Unknown ->
        UiText.Resource(R.string.core_ui_error_generic)
}
