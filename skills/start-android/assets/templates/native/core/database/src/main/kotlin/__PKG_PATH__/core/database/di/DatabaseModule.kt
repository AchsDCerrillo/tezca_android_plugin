package __PKG__.core.database.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import __PKG__.core.database.CoreDatabase
import __PKG__.core.database.DatabaseFactory
import __PKG__.core.database.sync.SyncMetadataDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideCoreDatabase(factory: DatabaseFactory): CoreDatabase = factory.create("core.db")

    @Provides
    fun provideSyncMetadataDao(database: CoreDatabase): SyncMetadataDao = database.syncMetadataDao()
}
