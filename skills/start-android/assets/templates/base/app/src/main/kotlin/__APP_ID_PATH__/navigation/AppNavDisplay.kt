package __APP_ID__.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import __APP_ID__.welcome.WelcomeKey
import __APP_ID__.welcome.WelcomeScreen
import __PKG__.core.navigation.rememberNavigator

/**
 * The app's only NavDisplay. Every feature contributes its entries through
 * `<feature>Entries(navigator, ...)`; cross-feature hand-offs are callbacks wired here, so feature
 * modules never depend on each other.
 */
@Composable
fun AppNavDisplay(modifier: Modifier = Modifier) {
    // tezca-android:start-destination
    val navigator = rememberNavigator(WelcomeKey)

    NavDisplay(
        backStack = navigator.backStack,
        onBack = navigator::back,
        modifier = modifier,
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            // Scopes ViewModels to their NavEntry: popped screens clear their ViewModel.
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<WelcomeKey> { WelcomeScreen() }
            // tezca-android:feature-entries
        },
    )
}
