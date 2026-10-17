package __PKG__.core.sdk

import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicBoolean
import __PKG__.core.common.config.AppConfig

/**
 * Starts the KMP SDK once, from Application.onCreate, with the app's environment. Keep every call
 * into the SDK's own bootstrap here so the rest of the app never touches its DI container.
 */
@Singleton
class SdkInitializer @Inject constructor(
    private val config: AppConfig,
) {
    private val started = AtomicBoolean(false)

    fun initialize(context: Context) {
        if (!started.compareAndSet(false, true)) return
        // tezca-android:sdk-init — replace with the SDK's real entry point (start-android step 5), e.g.
        // `SetupDi.start(context, SdkConfiguration(baseUrl = config.baseUrl))`. If the SDK uses
        // Firebase, the google-services plugin + google-services.json must be set up first.
    }
}
