# make-preferences templates

Base package `com.acme.shop`.

## Contents
1. Feature setting
2. `:core:session` module
3. Session model + data source
4. SessionTokenProvider (no refresh / refresh)
5. Logout
6. Start destination from the session

## 1. Feature setting: `feature/orders/.../data/local/OrdersPreferencesDataSource.kt`
```kotlin
package com.acme.shop.feature.orders.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.common.result.localCall
import com.acme.shop.feature.orders.domain.model.OrderStatus
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal interface OrdersPreferencesDataSource {
    val lastStatusFilter: Flow<OrderStatus?>
    suspend fun setLastStatusFilter(status: OrderStatus?): DataResult<Unit>
}

internal class OrdersPreferencesDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OrdersPreferencesDataSource {

    private object Keys {
        val LAST_STATUS_FILTER = stringPreferencesKey("orders.last_status_filter")
    }

    override val lastStatusFilter: Flow<OrderStatus?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[Keys.LAST_STATUS_FILTER]?.let { name -> OrderStatus.entries.firstOrNull { it.name == name } } }
        .distinctUntilChanged()

    override suspend fun setLastStatusFilter(status: OrderStatus?) = localCall {
        dataStore.edit { prefs ->
            if (status == null) prefs.remove(Keys.LAST_STATUS_FILTER) else prefs[Keys.LAST_STATUS_FILTER] = status.name
        }
        Unit
    }
}
```

## 2. `:core:session` module
`settings.gradle.kts` (below `include(":core:datastore")`): `include(":core:session")`. `:app`: `implementation(projects.core.session)`. Features get it automatically: `":core:session"` is already in `CORE_MODULES` of `build-logic/.../AndroidFeatureConventionPlugin.kt`.

`core/session/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.tezca.android.library)
    alias(libs.plugins.tezca.android.hilt)
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.datastore)
    implementation(projects.core.network)      // TokenProvider
    implementation(projects.core.database)     // LogoutUseCase clears SyncMetadataStore
    testImplementation(projects.core.testing)
}
```

## 3. Session model + data source: `core/session/src/main/kotlin/com/acme/shop/core/session/`
`Session.kt`:
```kotlin
package com.acme.shop.core.session

data class Session(
    val userId: String,
    val accessToken: String,
    val refreshToken: String?,
)
```
`SessionPreferencesDataSource.kt`:
```kotlin
package com.acme.shop.core.session

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.common.result.localCall
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

interface SessionPreferencesDataSource {
    /** Emits null while signed out. */
    val session: Flow<Session?>
    suspend fun save(session: Session): DataResult<Unit>
    suspend fun updateTokens(accessToken: String, refreshToken: String?): DataResult<Unit>
    suspend fun clear(): DataResult<Unit>
}

internal class SessionPreferencesDataSourceImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SessionPreferencesDataSource {

    private object Keys {
        val USER_ID = stringPreferencesKey("session.user_id")
        val ACCESS_TOKEN = stringPreferencesKey("session.access_token")
        val REFRESH_TOKEN = stringPreferencesKey("session.refresh_token")
    }

    override val session: Flow<Session?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            val userId = prefs[Keys.USER_ID]
            val access = prefs[Keys.ACCESS_TOKEN]
            if (userId == null || access == null) null else Session(userId, access, prefs[Keys.REFRESH_TOKEN])
        }
        .distinctUntilChanged()

    override suspend fun save(session: Session) = localCall {
        dataStore.edit { prefs ->
            prefs[Keys.USER_ID] = session.userId
            prefs[Keys.ACCESS_TOKEN] = session.accessToken
            session.refreshToken?.let { prefs[Keys.REFRESH_TOKEN] = it } ?: prefs.remove(Keys.REFRESH_TOKEN)
        }
        Unit
    }

    override suspend fun updateTokens(accessToken: String, refreshToken: String?) = localCall {
        dataStore.edit { prefs ->
            prefs[Keys.ACCESS_TOKEN] = accessToken
            refreshToken?.let { prefs[Keys.REFRESH_TOKEN] = it }
        }
        Unit
    }

    override suspend fun clear() = localCall {
        dataStore.edit { prefs ->
            prefs.remove(Keys.USER_ID)
            prefs.remove(Keys.ACCESS_TOKEN)
            prefs.remove(Keys.REFRESH_TOKEN)
        }
        Unit
    }
}
```
`ObserveSessionUseCase.kt` (features and `:app` depend on this, not on the data source):
```kotlin
package com.acme.shop.core.session

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveSessionUseCase @Inject constructor(private val prefs: SessionPreferencesDataSource) {
    operator fun invoke(): Flow<Session?> = prefs.session
}
```

## 4. SessionTokenProvider: `core/session/.../SessionTokenProvider.kt`
**No refresh endpoint** (default):
```kotlin
package com.acme.shop.core.session

import com.acme.shop.core.common.dispatchers.ApplicationScope
import com.acme.shop.core.network.TokenProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Keeps the access token in memory (hydrated from DataStore as soon as the app starts) because
 * OkHttp's interceptor reads it synchronously for every request.
 */
@Singleton
internal class SessionTokenProvider @Inject constructor(
    private val prefs: SessionPreferencesDataSource,
    @ApplicationScope scope: CoroutineScope,
) : TokenProvider {

    private val accessToken: StateFlow<String?> = prefs.session
        .map { it?.accessToken }
        .stateIn(scope, SharingStarted.Eagerly, null)

    override fun currentAccessToken(): String? = accessToken.value

    /** No refresh endpoint: a 401 means the session is over. Clearing it sends the UI back to login. */
    override suspend fun refreshAccessToken(): String? {
        prefs.clear()
        return null
    }
}
```
**Refresh endpoint** — replace `refreshAccessToken` (the auth remote data source uses the public Retrofit/HttpClient from make-api §S; its interface lives in `:core:session` so the feature implements it):
```kotlin
interface TokenRefresher {   // :core:session — implemented by the auth feature's remote data source
    suspend fun refresh(refreshToken: String): DataResult<Session>
}

override suspend fun refreshAccessToken(): String? {
    val current = prefs.session.first() ?: return null
    val refreshToken = current.refreshToken ?: return null.also { prefs.clear() }
    return when (val result = refresher.refresh(refreshToken)) {
        is DataResult.Success -> result.data.accessToken.also { prefs.save(result.data) }
        is DataResult.Failure -> null.also { prefs.clear() }
    }
}
```
(Inject `private val refresher: TokenRefresher`; the auth feature binds `@Binds abstract fun bindTokenRefresher(impl: AuthRemoteDataSourceImpl): TokenRefresher`.)

`SessionModule.kt`:
```kotlin
package com.acme.shop.core.session

import com.acme.shop.core.network.TokenProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SessionModule {
    @Binds
    abstract fun bindSessionPreferences(impl: SessionPreferencesDataSourceImpl): SessionPreferencesDataSource

    /** Satisfies :core:network's @BindsOptionalOf TokenProvider: requests now carry the token. */
    @Binds
    abstract fun bindTokenProvider(impl: SessionTokenProvider): TokenProvider
}
```

## 5. Logout: `core/session/src/main/kotlin/com/acme/shop/core/session/LogoutUseCase.kt`
```kotlin
package com.acme.shop.core.session

import com.acme.shop.core.common.result.DataResult
import com.acme.shop.core.database.sync.SyncMetadataStore
import javax.inject.Inject

class LogoutUseCase @Inject constructor(
    private val prefs: SessionPreferencesDataSource,
    private val syncMetadata: SyncMetadataStore,
) {
    suspend operator fun invoke(): DataResult<Unit> {
        syncMetadata.clear()          // the next user must not see "fresh" caches of this one
        return prefs.clear()          // signedIn → false: AppNavDisplay restarts at login (§6)
    }
}
```
Feature caches with per-user data should be cleared too (each feature's local data source `clear()`; with several, bind them `@IntoSet` as a `LogoutCleanup` interface and iterate here). Ktor only: inject `HttpClient` and call `client.clearAuthTokens()`.

## Writing the session (login feature)
The login feature's repository (data layer) injects `SessionPreferencesDataSource` from `:core:session` and saves the session after a successful login — data layer to data layer is allowed; ViewModels still only see `LoginUseCase`:
```kotlin
internal class AuthRepositoryImpl @Inject constructor(
    private val remote: AuthRemoteDataSource,
    private val session: SessionPreferencesDataSource,
    @param:Dispatcher(AppDispatcher.IO) private val io: CoroutineDispatcher,
) : AuthRepository {
    override suspend fun login(email: String, password: String): DataResult<Unit> = withContext(io) {
        remote.login(email, password).flatMap { session.save(it) }
    }
}
```

## 6. Start destination from the session (`:app`)
Wire this once both the login feature and the first signed-in feature exist (their keys are needed).

Catalog already has `androidx-core-splashscreen`; add `implementation(libs.androidx.core.splashscreen)` to `app/build.gradle.kts`.

`app/src/main/res/values/themes.xml` (splash theme on the activity, real theme after it):
```xml
<style name="Theme.AcmeShop" parent="android:Theme.Material.Light.NoActionBar" />

<style name="Theme.AcmeShop.Starting" parent="Theme.SplashScreen">
    <item name="postSplashScreenTheme">@style/Theme.AcmeShop</item>
</style>
```
and in the manifest `<activity android:name=".MainActivity" android:theme="@style/Theme.AcmeShop.Starting" …>`.

`app/.../navigation/AppStartViewModel.kt`:
```kotlin
package com.acme.shop.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.acme.shop.core.session.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class AppStartViewModel @Inject constructor(observeSession: ObserveSessionUseCase) : ViewModel() {
    /** null until the session has been read once; then follows sign-in / sign-out / expired session. */
    val signedIn: StateFlow<Boolean?> = observeSession()
        .map { it != null }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
```
`MainActivity.kt`:
```kotlin
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.acme.shop.feature.onboarding.navigation.LoginKey
import com.acme.shop.feature.orders.navigation.OrderListKey
import com.acme.shop.navigation.AppStartViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val startViewModel: AppStartViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { startViewModel.signedIn.value == null }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val signedIn by startViewModel.signedIn.collectAsStateWithLifecycle()
                signedIn?.let { isSignedIn ->
                    // key(): a session change (login, logout, expired token cleared by
                    // SessionTokenProvider) starts a fresh back stack at the right root.
                    key(isSignedIn) {
                        AppNavDisplay(startKey = if (isSignedIn) OrderListKey else LoginKey)
                    }
                }
            }
        }
    }
}
```
`AppNavDisplay` becomes `fun AppNavDisplay(startKey: NavKey, modifier: Modifier = Modifier)` with `val navigator = rememberNavigator(startKey)` on the `// tezca-android:start-destination` line; remove the `entry<WelcomeKey>` line, its imports and the `welcome/` package. Because the session drives the root, the login screen needs no "go home" callback: saving the session is enough. Keep `onSignedIn`-style callbacks only for flows that continue inside the signed-in area (e.g. onboarding steps after registration).
