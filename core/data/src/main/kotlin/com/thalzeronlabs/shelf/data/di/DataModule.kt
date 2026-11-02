package com.thalzeronlabs.shelf.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.thalzeronlabs.shelf.data.AndroidConnectivityMonitor
import com.thalzeronlabs.shelf.data.ConnectivityMonitor
import com.thalzeronlabs.shelf.data.DefaultShelfRepository
import com.thalzeronlabs.shelf.data.ShelfRepository
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
import com.thalzeronlabs.shelf.data.sync.SyncScheduler
import com.thalzeronlabs.shelf.model.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient): OpenLibraryApi = Retrofit.Builder()
        .baseUrl(OpenLibraryApi.BASE_URL)
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().build()))
        .build()
        .create(OpenLibraryApi::class.java)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): ShelfDatabase =
        Room.databaseBuilder(context, ShelfDatabase::class.java, ShelfDatabase.NAME).build()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    fun clock(): Clock = Clock(System::currentTimeMillis)

    @Provides @Singleton
    fun repository(db: ShelfDatabase, api: OpenLibraryApi, clock: Clock, sync: SyncScheduler): ShelfRepository =
        DefaultShelfRepository(db, api, clock, sync)
}

@Module
@InstallIn(SingletonComponent::class)
interface ConnectivityModule {
    @Binds
    fun connectivity(monitor: AndroidConnectivityMonitor): ConnectivityMonitor
}
