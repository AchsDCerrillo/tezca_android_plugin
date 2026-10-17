package __PKG__.feature.__FPKG__.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * Hilt wiring of the __Feature_Title__ feature: `@Binds` for interfaces implemented in this module
 * (data sources, repositories) and `@Provides` in the companion for objects built by a library
 * (Retrofit services, Room database and DAOs). Empty in kmp-sdk mode unless the feature has its
 * own bindings (SDK use cases are bridged in :core:sdk).
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class __Feature__Module {
    // tezca-android:binds

    companion object {
        // tezca-android:provides
    }
}
