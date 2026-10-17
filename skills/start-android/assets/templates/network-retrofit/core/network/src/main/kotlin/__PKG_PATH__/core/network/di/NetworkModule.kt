package __PKG__.core.network.di

import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import __PKG__.core.common.config.AppConfig
import __PKG__.core.network.AuthInterceptor
import __PKG__.core.network.NetworkJson
import __PKG__.core.network.PublicApi
import __PKG__.core.network.RetryInterceptor
import __PKG__.core.network.TokenAuthenticator
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
        fun provideOkHttpClient(
            config: AppConfig,
            authInterceptor: AuthInterceptor,
            tokenAuthenticator: TokenAuthenticator,
        ): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .addInterceptor(RetryInterceptor())
            .authenticator(tokenAuthenticator)
            .apply {
                // Bodies are logged in debug builds only, and never the Authorization header.
                if (config.isDebug) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BODY
                            redactHeader("Authorization")
                        },
                    )
                }
            }
            .build()

        @Provides
        @Singleton
        fun provideRetrofit(config: AppConfig, client: OkHttpClient): Retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
            .build()

        /** Same server, no token and no refresh-on-401: for login, register and token refresh. */
        @Provides
        @Singleton
        @PublicApi
        fun providePublicRetrofit(config: AppConfig, client: OkHttpClient): Retrofit {
            val publicClient = client.newBuilder()
                .apply {
                    interceptors().removeAll { it is AuthInterceptor }
                    authenticator(okhttp3.Authenticator.NONE)
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(config.baseUrl)
                .client(publicClient)
                .addConverterFactory(NetworkJson.asConverterFactory("application/json".toMediaType()))
                .build()
        }
    }
}
