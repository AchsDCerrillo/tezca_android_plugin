package __PKG__.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack

/**
 * Thin wrapper over the Navigation 3 back stack that feature entries receive. The back stack is
 * saveable (keys are @Serializable), so it survives configuration changes and process death.
 */
class Navigator(val backStack: NavBackStack<NavKey>) {

    val current: NavKey? get() = backStack.lastOrNull()

    fun navigate(key: NavKey) {
        backStack.add(key)
    }

    /** Pops the top entry; the root entry stays (the system back gesture then leaves the app). */
    fun back() {
        if (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
    }

    /** Clears the stack and starts over at [key]: login -> home, logout -> login. */
    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }

    /** Pops entries until the top is an instance of [T] (kept when [inclusive] is false). */
    inline fun <reified T : NavKey> popUpTo(inclusive: Boolean = false) {
        val index = backStack.indexOfLast { it is T }
        if (index < 0) return
        val keep = if (inclusive) index else index + 1
        while (backStack.size > keep.coerceAtLeast(1)) backStack.removeAt(backStack.lastIndex)
    }
}

@Composable
fun rememberNavigator(startKey: NavKey): Navigator {
    val backStack = rememberNavBackStack(startKey)
    return remember(backStack) { Navigator(backStack) }
}
