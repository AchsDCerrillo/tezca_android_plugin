package __PKG__.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Replaces Dispatchers.Main (used by viewModelScope) with a test dispatcher. Pass the same
 * [testDispatcher] to classes that take an injected `@Dispatcher` so the whole test shares one clock:
 *
 * ```
 * @get:Rule val mainRule = MainDispatcherRule()
 * private val repo = FakeUserRepository()
 * private val viewModel by lazy { UserViewModel(GetUserUseCase(repo)) }
 * ```
 */
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(testDispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
