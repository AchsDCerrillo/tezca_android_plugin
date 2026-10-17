package __PKG__.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import __PKG__.core.ui.R
import __PKG__.core.ui.UiText
import __PKG__.core.ui.theme.AppTheme
import __PKG__.core.ui.theme.Dimens

@Composable
fun LoadingContent(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.core_ui_loading)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.semantics { contentDescription = description })
    }
}

@Composable
fun ErrorContent(message: UiText, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(Dimens.lg),
        verticalArrangement = Arrangement.spacedBy(Dimens.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message.asString(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Button(onClick = onRetry) { Text(stringResource(R.string.core_ui_retry)) }
        }
    }
}

@Composable
fun EmptyContent(message: UiText, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().padding(Dimens.lg), contentAlignment = Alignment.Center) {
        Text(message.asString(), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorContentPreview() {
    AppTheme { ErrorContent(UiText.Resource(R.string.core_ui_error_no_internet), onRetry = {}) }
}
