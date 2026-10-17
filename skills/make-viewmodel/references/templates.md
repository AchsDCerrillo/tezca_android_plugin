# make-viewmodel templates

Feature `orders`, package `com.acme.shop.feature.orders.presentation.<screen>`.

## Contents
1. List (observe + refresh)
2. Detail (NavKey argument, assisted injection)
3. Form (fields, validation, submit)
4. Search / filters (flatMapLatest)
5. Paginated list
6. SDK use cases (ResultState)

## 1. List
`presentation/orderlist/OrderListUiState.kt`:
```kotlin
package com.acme.shop.feature.orders.presentation.orderlist

import com.acme.shop.core.ui.UiText
import com.acme.shop.feature.orders.domain.model.Order

internal data class OrderListUiState(
    val orders: List<Order> = emptyList(),
    val isLoading: Boolean = true,          // first load, nothing cached yet
    val isRefreshing: Boolean = false,      // pull-to-refresh indicator
    val error: UiText? = null,              // full-screen error: only when there's nothing to show
)

internal sealed interface OrderListAction {
    data object Refresh : OrderListAction
    data object Retry : OrderListAction
    data class OrderClicked(val id: Long) : OrderListAction
}

internal sealed interface OrderListEvent {
    data class NavigateToDetail(val id: Long) : OrderListEvent
    data class ShowMessage(val message: UiText) : OrderListEvent
}
```
`presentation/orderlist/OrderListViewModel.kt`:
```kotlin
package com.acme.shop.feature.orders.presentation.orderlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.ui.toUiText
import com.acme.shop.feature.orders.domain.usecase.ObserveOrdersUseCase
import com.acme.shop.feature.orders.domain.usecase.RefreshOrdersUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class OrderListViewModel @Inject constructor(
    observeOrders: ObserveOrdersUseCase,
    private val refreshOrders: RefreshOrdersUseCase,
) : ViewModel() {

    /** UI-only state that doesn't come from the database. */
    private val sync = MutableStateFlow(SyncState())

    val state: StateFlow<OrderListUiState> = combine(observeOrders(), sync) { orders, sync ->
        OrderListUiState(
            orders = orders,
            isLoading = orders.isEmpty() && sync.inProgress,
            isRefreshing = sync.userRefresh && sync.inProgress,
            error = sync.error.takeIf { orders.isEmpty() },
        )
    }
        .onStart { refresh(force = false, userRefresh = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderListUiState())

    private val _events = Channel<OrderListEvent>(Channel.BUFFERED)
    val events: Flow<OrderListEvent> = _events.receiveAsFlow()

    fun onAction(action: OrderListAction) {
        when (action) {
            OrderListAction.Refresh -> refresh(force = true, userRefresh = true)
            OrderListAction.Retry -> refresh(force = true, userRefresh = false)
            is OrderListAction.OrderClicked -> viewModelScope.launch { _events.send(OrderListEvent.NavigateToDetail(action.id)) }
        }
    }

    private fun refresh(force: Boolean, userRefresh: Boolean) {
        if (sync.value.inProgress) return
        viewModelScope.launch {
            sync.value = SyncState(inProgress = true, userRefresh = userRefresh)
            when (val result = refreshOrders(force)) {
                is DataResult.Success -> sync.value = SyncState()
                is DataResult.Failure -> {
                    val message = result.error.toUiText()
                    sync.value = SyncState(error = message)
                    // With cached data on screen the error is a snackbar, not a full-screen state.
                    if (state.value.orders.isNotEmpty()) _events.send(OrderListEvent.ShowMessage(message))
                }
            }
        }
    }

    private data class SyncState(
        val inProgress: Boolean = false,
        val userRefresh: Boolean = false,
        val error: com.acme.shop.core.ui.UiText? = null,
    )
}
```
`onStart` runs when the screen subscribes (and again after it was stopped longer than 5 s — the TTL in the repository makes that refresh cheap).

## 2. Detail (NavKey argument)
`navigation/OrdersNavigation.kt` declares `@Serializable data class OrderDetailKey(val orderId: Long) : NavKey`.
```kotlin
@HiltViewModel(assistedFactory = OrderDetailViewModel.Factory::class)
internal class OrderDetailViewModel @AssistedInject constructor(
    @Assisted private val key: OrderDetailKey,
    observeOrder: ObserveOrderUseCase,
    private val refreshOrder: RefreshOrderUseCase,
    private val cancelOrder: CancelOrderUseCase,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(key: OrderDetailKey): OrderDetailViewModel
    }

    private val operation = MutableStateFlow(Operation())

    val state: StateFlow<OrderDetailUiState> = combine(observeOrder(key.orderId), operation) { order, op ->
        OrderDetailUiState(order = order, isLoading = order == null && op.loading, isCancelling = op.cancelling, error = op.error.takeIf { order == null })
    }
        .onStart { load() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderDetailUiState())

    private val _events = Channel<OrderDetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: OrderDetailAction) {
        when (action) {
            OrderDetailAction.Retry -> load()
            OrderDetailAction.Cancel -> cancel()
        }
    }

    private fun load() = viewModelScope.launch {
        operation.update { it.copy(loading = true, error = null) }
        val result = refreshOrder(key.orderId)
        operation.update { it.copy(loading = false, error = (result as? DataResult.Failure)?.error?.toUiText()) }
    }

    private fun cancel() = viewModelScope.launch {
        operation.update { it.copy(cancelling = true) }
        when (val result = cancelOrder(key.orderId)) {
            is DataResult.Success -> _events.send(OrderDetailEvent.Closed)
            is DataResult.Failure -> _events.send(OrderDetailEvent.ShowMessage(result.error.toUiText()))
        }
        operation.update { it.copy(cancelling = false) }
    }

    private data class Operation(val loading: Boolean = false, val cancelling: Boolean = false, val error: UiText? = null)
}
```
The entry resolves it with `hiltViewModel<OrderDetailViewModel, OrderDetailViewModel.Factory>(creationCallback = { it.create(key) })` (make-screen). The ViewModel is scoped to its NavEntry by `rememberViewModelStoreNavEntryDecorator()` in `AppNavDisplay`, so two detail entries get two ViewModels. Keys are saved with the back stack, so the argument survives process death without `SavedStateHandle`.

## 3. Form
```kotlin
internal data class CheckoutUiState(
    val notes: String = "",
    val notesError: UiText? = null,
    val isSubmitting: Boolean = false,
) {
    val canSubmit: Boolean get() = !isSubmitting && notesError == null
}

internal sealed interface CheckoutAction {
    data class NotesChanged(val value: String) : CheckoutAction
    data object Submit : CheckoutAction
}

internal sealed interface CheckoutEvent {
    data class OrderPlaced(val orderId: Long) : CheckoutEvent
    data class ShowMessage(val message: UiText) : CheckoutEvent
}

@HiltViewModel
internal class CheckoutViewModel @Inject constructor(
    private val placeOrder: PlaceOrderUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(CheckoutUiState())
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    private val _events = Channel<CheckoutEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: CheckoutAction) {
        when (action) {
            is CheckoutAction.NotesChanged -> _state.update {
                it.copy(notes = action.value, notesError = if (action.value.length > 500) UiText.Resource(R.string.orders_error_notes_too_long) else null)
            }
            CheckoutAction.Submit -> submit()
        }
    }

    private fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            when (val result = placeOrder(current.notes)) {
                is DataResult.Success -> _events.send(CheckoutEvent.OrderPlaced(result.data.id))
                is DataResult.Failure -> _events.send(CheckoutEvent.ShowMessage(result.error.toOrdersText()))
            }
            _state.update { it.copy(isSubmitting = false) }
        }
    }
}

/** Feature-specific domain errors first, generic text otherwise. */
internal fun AppError.toOrdersText(): UiText = when ((this as? AppError.Domain)?.code) {
    OrderErrorCodes.EMPTY_CART -> UiText.Resource(R.string.orders_error_empty_cart)
    OrderErrorCodes.NOTES_TOO_LONG -> UiText.Resource(R.string.orders_error_notes_too_long)
    else -> toUiText()
}
```
A form owns its state (`MutableStateFlow` + `asStateFlow()`) because nothing is observed from the database. Text fields hold their value in the state, so it survives rotation; for text that must survive process death too, keep it in `SavedStateHandle` (`savedStateHandle.getStateFlow("notes", "")`).

## 4. Search / filters
```kotlin
@HiltViewModel
internal class OrderSearchViewModel @Inject constructor(
    private val searchOrders: SearchOrdersUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val status = MutableStateFlow<OrderStatus?>(null)

    val state: StateFlow<OrderSearchUiState> = combine(query, status) { q, s -> q.trim() to s }
        .debounce(300)
        .distinctUntilChanged()
        .flatMapLatest { (q, s) -> searchOrders(q, s).map { results -> OrderSearchUiState(query = q, status = s, results = results) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrderSearchUiState())

    fun onAction(action: OrderSearchAction) {
        when (action) {
            is OrderSearchAction.QueryChanged -> query.value = action.value
            is OrderSearchAction.StatusSelected -> status.value = action.status
        }
    }
}
```
`flatMapLatest` cancels the previous search when the query changes, so results never arrive out of order. The text field shows `query` from a separate `StateFlow` (not debounced) if typing must feel instant: expose `val query = this.query.asStateFlow()`.

## 5. Paginated list
State adds `isLoadingMore`, `canLoadMore`, `loadMoreError: UiText?`; actions add `LoadMore`:
```kotlin
private fun loadMore() {
    if (paging.value.loading || !paging.value.canLoadMore) return
    viewModelScope.launch {
        paging.update { it.copy(loading = true, error = null) }
        when (val result = loadMoreOrders()) {
            is DataResult.Success -> paging.update { it.copy(loading = false, canLoadMore = result.data) }
            is DataResult.Failure -> paging.update { it.copy(loading = false, error = result.error.toUiText()) }
        }
    }
}
```
Reset `canLoadMore = true` after a successful forced refresh.

## 6. SDK use cases (kmp-sdk mode)
Rules for SDK flows:
- Map SDK models to small screen models (`SoundItem`) in the ViewModel or a `presentation/<X>UiModel.kt` mapper: previews and tests then don't need to build SDK classes.
- Map failures with `Throwable?.toAppError()` from `:core:sdk` (extend it with the SDK's exception types), then `toUiText()`.
- A retry must reset the state to loading (emit a marker in `onStart`), or the error stays on screen without a spinner.
- An SDK that emits a cached value first may emit an **empty** cached list: keep `isLoading` until a non-empty or network emission arrives, or the empty state flashes.

For an SDK use case returning `Flow<ResultState<Sound>>` that emits cached data first and then the network result:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = SoundDetailViewModel.Factory::class)
internal class SoundDetailViewModel @AssistedInject constructor(
    @Assisted private val key: SoundDetailKey,
    private val getSound: GetSoundUseCaseV2,      // bridged in :core:sdk SdkUseCaseModule
) : ViewModel() {

    @AssistedFactory
    interface Factory { fun create(key: SoundDetailKey): SoundDetailViewModel }

    private val retry = MutableStateFlow(0)

    val state: StateFlow<SoundDetailUiState> = retry
        .flatMapLatest {
            // null = "(re)loading" marker so a retry shows the spinner again.
            getSound(key.soundId).map<ResultState<Sound>, ResultState<Sound>?> { it }.onStart { emit(null) }
        }
        .scan(SoundDetailUiState()) { previous, result ->
            when (result) {
                null -> previous.copy(isLoading = true, error = null)
                is ResultState.Success -> previous.copy(sound = result.data.toSoundItem(), isLoading = false, error = null)
                // Keep what's on screen (e.g. cached data) and only show the error if there's nothing.
                is ResultState.Failure -> previous.copy(
                    isLoading = false,
                    error = if (previous.sound == null) result.exception.toAppError().toUiText() else null,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SoundDetailUiState())

    fun onAction(action: SoundDetailAction) {
        when (action) {
            SoundDetailAction.Retry -> retry.update { it + 1 }
        }
    }
}
```
Adapt the class names to the SDK's real result type. Errors without cached data become the full-screen state; errors after cached data can additionally send a `ShowMessage` event. Strings from `:core:ui` are `com.acme.shop.core.ui.R.string.*` (non-transitive R classes): import it with an alias (`import com.acme.shop.core.ui.R as CoreUiR`) when the feature's own `R` is also used.

**SDK pagination** (one `Flow<ResultState<Page<T>>>` per page, cached page first): keep `pages: MutableStateFlow<Map<Int, List<T>>>`; `loadPage(n)` collects the page's flow in a `Job` stored per page and replaces `pages[n]` on each emission; state = `pages.values.flatten()` in page order; `canLoadMore` from the SDK's total count (or "last page was full"); an empty cached first page keeps `isLoading = true` until the network emission.
