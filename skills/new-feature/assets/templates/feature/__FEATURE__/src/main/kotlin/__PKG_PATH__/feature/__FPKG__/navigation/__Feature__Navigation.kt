package __PKG__.feature.__FPKG__.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import __PKG__.core.navigation.Navigator

// Navigation keys of the __Feature_Title__ feature. Keys are @Serializable so the back stack survives
// process death; arguments are constructor properties (`data class OrderDetailKey(val id: Long)`).
// tezca-android:keys

/**
 * Registers every screen of the __Feature_Title__ feature in the app's NavDisplay. Hand-offs to other
 * features are callback parameters (wired in AppNavDisplay), never imports of their keys.
 */
fun EntryProviderScope<NavKey>.__feature__Entries(
    navigator: Navigator,
    // tezca-android:entry-callbacks
) {
    // tezca-android:entries
}
