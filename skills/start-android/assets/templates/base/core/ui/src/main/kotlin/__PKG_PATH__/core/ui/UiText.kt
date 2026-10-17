package __PKG__.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource

/**
 * Text produced outside composition (ViewModel, mapper) and resolved in the UI, so ViewModels never
 * hold a Context and every user-facing string stays a translatable resource.
 */
sealed interface UiText {
    data class Resource(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    /** Server-provided or user-entered text that is already localized. */
    data class Dynamic(val value: String) : UiText

    @Composable
    fun asString(): String = when (this) {
        is Resource -> stringResource(id, *args.toTypedArray())
        is Dynamic -> value
    }
}

/**
 * Resolves [UiText] outside composition — event handlers, `snackbarHostState.showSnackbar(...)` —
 * with the current configuration's resources.
 */
@Composable
fun rememberUiTextResolver(): (UiText) -> String {
    val resources = LocalResources.current
    return remember(resources) {
        { text ->
            when (text) {
                is UiText.Resource -> resources.getString(text.id, *text.args.toTypedArray())
                is UiText.Dynamic -> text.value
            }
        }
    }
}
