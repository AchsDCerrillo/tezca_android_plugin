# make-screen templates

Feature `orders`, screens `orderlist` (list) and `orderdetail` (detail), `checkout` (form). ViewModels and UiState from make-viewmodel.

## Contents
1. Navigation keys + entries
2. List screen (pull-to-refresh, empty/error, snackbar events)
3. Detail screen (NavKey argument)
4. Form screen (IME, validation, submit)
5. Strings
6. Previews
7. Wiring in :app

## 1. `navigation/OrdersNavigation.kt`
```kotlin
package com.acme.shop.feature.orders.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.acme.shop.core.navigation.Navigator
import com.acme.shop.feature.orders.presentation.orderdetail.OrderDetailRoute
import com.acme.shop.feature.orders.presentation.orderlist.OrderListRoute
import kotlinx.serialization.Serializable

// tezca-android:keys
@Serializable
data object OrderListKey : NavKey

@Serializable
data class OrderDetailKey(val orderId: Long) : NavKey

fun EntryProviderScope<NavKey>.ordersEntries(
    navigator: Navigator,
    // tezca-android:entry-callbacks
) {
    // tezca-android:entries
    entry<OrderListKey> {
        OrderListRoute(onOrderClick = { id -> navigator.navigate(OrderDetailKey(id)) })
    }
    entry<OrderDetailKey> { key ->
        OrderDetailRoute(key = key, onBack = navigator::back)
    }
}
```
Keys stay public (`:app` may navigate to them); Routes are `internal`. Use the id type of your data (`Long` here; SDKs often use `Int`). A hand-off to another feature is a new parameter below `// tezca-android:entry-callbacks` (§7).

## 2. List: `presentation/orderlist/OrderListScreen.kt`
```kotlin
package com.acme.shop.feature.orders.presentation.orderlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acme.shop.core.ui.ObserveAsEvents
import com.acme.shop.core.ui.UiText
import com.acme.shop.core.ui.components.EmptyContent
import com.acme.shop.core.ui.components.ErrorContent
import com.acme.shop.core.ui.components.LoadingContent
import com.acme.shop.core.ui.rememberUiTextResolver
import com.acme.shop.core.ui.theme.Dimens
import com.acme.shop.core.ui.theme.AppTheme
import com.acme.shop.feature.orders.R
import com.acme.shop.feature.orders.domain.model.Order

/** Stateful entry point: owns the ViewModel, events and navigation callbacks. */
@Composable
internal fun OrderListRoute(
    onOrderClick: (Long) -> Unit,
    viewModel: OrderListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    // Resolve UiText for snackbars in composition, then show it from the event handler.
    val messages = rememberUiTextResolver()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is OrderListEvent.NavigateToDetail -> onOrderClick(event.id)
            is OrderListEvent.ShowMessage -> snackbarHostState.showSnackbar(messages(event.message))
        }
    }

    OrderListScreen(state = state, onAction = viewModel::onAction, snackbarHostState = snackbarHostState)
}

/** Stateless and previewable: everything it needs comes in as parameters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderListScreen(
    state: OrderListUiState,
    onAction: (OrderListAction) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.orders_list_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        when {
            state.isLoading -> LoadingContent(contentModifier)
            state.error != null -> ErrorContent(state.error, onRetry = { onAction(OrderListAction.Retry) }, modifier = contentModifier)
            else -> PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { onAction(OrderListAction.Refresh) },
                modifier = contentModifier,
            ) {
                if (state.orders.isEmpty()) {
                    EmptyContent(UiText.Resource(R.string.orders_list_empty))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = Dimens.sm)) {
                        items(state.orders, key = { it.id }) { order ->
                            OrderRow(order, onClick = { onAction(OrderListAction.OrderClicked(order.id)) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrderRow(order: Order, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.orders_list_item_title, order.number)) },
        supportingContent = { Text(order.status.label()) },
        trailingContent = { Text(order.total.format()) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
```
**Paginated list** (state has `isLoadingMore`, `canLoadMore`, `loadMoreError`; action `LoadMore` — make-viewmodel §5): add a footer item after the rows inside the `LazyColumn`:
```kotlin
if (state.canLoadMore) {
    item(key = "load_more") {
        LoadMoreFooter(isLoading = state.isLoadingMore, error = state.loadMoreError, onLoadMore = { onAction(OrderListAction.LoadMore) })
    }
}

/** Requests the next page as soon as it becomes visible; on failure shows a retry button instead. */
@Composable
private fun LoadMoreFooter(isLoading: Boolean, error: UiText?, onLoadMore: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(Dimens.md), contentAlignment = Alignment.Center) {
        if (error != null) {
            TextButton(onClick = onLoadMore) { Text(stringResource(R.string.orders_list_load_more_retry)) }
        } else {
            if (!isLoading) LaunchedEffect(Unit) { onLoadMore() }
            CircularProgressIndicator()
        }
    }
}
```

Helpers in the same feature (`presentation/OrderFormatting.kt`):
```kotlin
@Composable
internal fun OrderStatus.label(): String = stringResource(
    when (this) {
        OrderStatus.PENDING -> R.string.orders_status_pending
        OrderStatus.PAID -> R.string.orders_status_paid
        OrderStatus.SHIPPED -> R.string.orders_status_shipped
        OrderStatus.CANCELLED -> R.string.orders_status_cancelled
    },
)

/** Locale-aware currency formatting; formatting is presentation, so it lives here, not in domain. */
internal fun Money.format(locale: java.util.Locale = java.util.Locale.getDefault()): String =
    java.text.NumberFormat.getCurrencyInstance(locale).apply { currency = java.util.Currency.getInstance(this@format.currency) }
        .format(cents / 100.0)
```
`rememberUiTextResolver()` comes from `:core:ui` (`UiText.kt`): it resolves `UiText` inside event handlers, where `asString()` (a composable) can't be called.

## 3. Detail: `presentation/orderdetail/OrderDetailScreen.kt`
```kotlin
@Composable
internal fun OrderDetailRoute(
    key: OrderDetailKey,
    onBack: () -> Unit,
    viewModel: OrderDetailViewModel = hiltViewModel<OrderDetailViewModel, OrderDetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(key) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val messages = rememberUiTextResolver()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            OrderDetailEvent.Closed -> onBack()
            is OrderDetailEvent.ShowMessage -> snackbarHostState.showSnackbar(messages(event.message))
        }
    }
    OrderDetailScreen(state, viewModel::onAction, onBack, snackbarHostState = snackbarHostState)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OrderDetailScreen(
    state: OrderDetailUiState,
    onAction: (OrderDetailAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.orders_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.orders_detail_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)
        val order = state.order
        when {
            order != null -> OrderDetailContent(order, isCancelling = state.isCancelling, onCancel = { onAction(OrderDetailAction.Cancel) }, modifier = contentModifier)
            state.error != null -> ErrorContent(state.error, onRetry = { onAction(OrderDetailAction.Retry) }, modifier = contentModifier)
            else -> LoadingContent(contentModifier)
        }
    }
}
```
`Icons.AutoMirrored` flips in RTL locales. The back arrow has a content description; decorative images use `contentDescription = null`.

## 4. Form: `presentation/checkout/CheckoutScreen.kt` (content part)
```kotlin
Column(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .imePadding()                         // keeps the button above the keyboard (edge-to-edge)
        .verticalScroll(rememberScrollState())
        .padding(Dimens.md),
    verticalArrangement = Arrangement.spacedBy(Dimens.md),
) {
    OutlinedTextField(
        value = state.notes,
        onValueChange = { onAction(CheckoutAction.NotesChanged(it)) },
        label = { Text(stringResource(R.string.orders_checkout_notes_label)) },
        isError = state.notesError != null,
        supportingText = state.notesError?.let { error -> { Text(error.asString()) } },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(
        onClick = { onAction(CheckoutAction.Submit) },
        enabled = state.canSubmit,
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimens.minTouchTarget),
    ) {
        if (state.isSubmitting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        else Text(stringResource(R.string.orders_checkout_submit))
    }
}
```
Login-type forms: `KeyboardType.Email` / `KeyboardType.Password` + `PasswordVisualTransformation()`, `autofill` semantics (`Modifier.semantics { contentType = ContentType.EmailAddress }`) so password managers work.

## 5. Strings: `feature/orders/src/main/res/values/strings.xml`
```xml
<resources>
    <!-- Orders strings. Prefix every key with orders_ (resources share one namespace app-wide). -->
    <!-- tezca-android:strings -->
    <string name="orders_list_title">Orders</string>
    <string name="orders_list_empty">You have no orders yet.</string>
    <string name="orders_list_item_title">Order %1$s</string>
    <string name="orders_status_pending">Pending</string>
    <string name="orders_status_paid">Paid</string>
    <string name="orders_status_shipped">Shipped</string>
    <string name="orders_status_cancelled">Cancelled</string>
    <string name="orders_detail_title">Order details</string>
    <string name="orders_detail_back">Back</string>
</resources>
```
and the same keys in `values-es/strings.xml` (and every other `values-<locale>`):
```xml
    <string name="orders_list_title">Pedidos</string>
    <string name="orders_list_empty">Aún no tienes pedidos.</string>
    <string name="orders_list_item_title">Pedido %1$s</string>
    …
```
Placeholders are positional (`%1$s`), apostrophes escaped (`\'`), plurals with `<plurals>` + `pluralStringResource`.

## 6. Previews
```kotlin
private val sampleOrders = listOf(
    Order(1, "A-1001", OrderStatus.PAID, Money(2599, "USD"), notes = null, createdAt = Instant.fromEpochMilliseconds(0)),
    Order(2, "A-1002", OrderStatus.SHIPPED, Money(1250, "USD"), notes = "Gift", createdAt = Instant.fromEpochMilliseconds(0)),
)

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OrderListScreenPreview() {
    AppTheme(dynamicColor = false) {
        OrderListScreen(state = OrderListUiState(orders = sampleOrders, isLoading = false), onAction = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun OrderListScreenEmptyPreview() {
    AppTheme(dynamicColor = false) { OrderListScreen(state = OrderListUiState(isLoading = false), onAction = {}) }
}
```
`dynamicColor = false` keeps previews deterministic (brand colors).

## 7. Wiring in `:app`
`new-feature` already added `ordersEntries(navigator = navigator)`. When a screen hands off to another feature, add a callback parameter to `ordersEntries` (below `// tezca-android:entry-callbacks`), pass it to the Route, and wire it here — `:app` depends on both features, so it may use the other feature's key:
```kotlin
ordersEntries(
    navigator = navigator,
    onBrowseCatalog = { navigator.navigate(CatalogKey) },
)
```
