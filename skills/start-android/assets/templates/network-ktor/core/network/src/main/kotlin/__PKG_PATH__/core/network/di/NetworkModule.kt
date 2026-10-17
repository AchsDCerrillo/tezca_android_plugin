package __PKG__.core.network.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.Optional
import javax.inject.Singleton
import __PKG__.core.common.config.AppConfig
import __PKG__.core.network.HttpClientFactory
import __PKG__.core.network.PublicApi
import __PKG__.core.network.TokenProvider

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    /** Bound by the feature that owns the session (make-preferences); optional until then. */
    @BindsOptionalOf
    abstract fun optionalTokenProvider(): TokenProvider

    companion object {
        @Provides
        @Singleton
        fun provideHttpClient(config: AppConfig, tokenProvider: Optional<TokenProvider>): HttpClient =
            HttpClientFactory.create(OkHttp.create(), config, tokenProvider.orElse(null))

        /** Same server, no token and no refresh-on-401: for login, register and token refresh. */
        @Provides
        @Singleton
        @PublicApi
        fun providePublicHttpClient(config: AppConfig): HttpClient =
            HttpClientFactory.create(OkHttp.create(), config, tokenProvider = null)
    }
}
