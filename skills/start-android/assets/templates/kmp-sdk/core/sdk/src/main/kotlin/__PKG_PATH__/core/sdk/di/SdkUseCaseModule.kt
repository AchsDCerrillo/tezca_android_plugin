package __PKG__.core.sdk.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/**
 * Bridges the SDK's use cases into Hilt so ViewModels inject them like any other dependency (and
 * tests replace them with fakes). make-usecase adds one provider per use case below the marker:
 *
 * ```
 * @Provides
 * fun provideGetOrdersUseCase(): GetOrdersUseCase = SdkLocator.get()
 * ```
 * The SDK's own container decides the real lifetime (Koin `single`s are app-wide whatever Hilt
 * component asks for them); ViewModelComponent just keeps these bindings visible to ViewModels only.
 * Providers run lazily, when a ViewModel is created — but resolving one usually builds part of the
 * SDK's graph, so everything the SDK needs at that point (initialization, Firebase…) must be ready.
 */
@Module
@InstallIn(ViewModelComponent::class)
object SdkUseCaseModule {
    // tezca-android:sdk-usecases
}
