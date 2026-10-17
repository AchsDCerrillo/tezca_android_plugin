package __APP_ID__

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
//@if sdk
import __PKG__.core.sdk.SdkInitializer
import javax.inject.Inject
//@endif

@HiltAndroidApp
class __APP_CLASS__Application : Application() {
//@if sdk

    @Inject lateinit var sdkInitializer: SdkInitializer

    override fun onCreate() {
        super.onCreate()
        // The SDK must be ready before any use case is resolved (Hilt providers call into it lazily).
        sdkInitializer.initialize(this)
    }
//@endif
}
