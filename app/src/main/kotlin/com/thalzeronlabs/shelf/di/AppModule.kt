package com.thalzeronlabs.shelf.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.squareup.moshi.Moshi
import com.thalzeronlabs.shelf.data.DefaultShelfRepository
import com.thalzeronlabs.shelf.data.ShelfRepository
import com.thalzeronlabs.shelf.data.local.BookDao
import com.thalzeronlabs.shelf.data.local.ShelfDatabase
import com.thalzeronlabs.shelf.data.remote.OpenLibraryApi
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
object AppModule {

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
        Room.databaseBuilder(context, ShelfDatabase::class.java, "shelf.db").build()

    @Provides
    fun bookDao(db: ShelfDatabase): BookDao = db.books()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton
    fun repository(api: OpenLibraryApi, dao: BookDao): ShelfRepository =
        DefaultShelfRepository(api, dao, System::currentTimeMillis)
}
