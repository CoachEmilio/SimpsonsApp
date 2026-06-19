package com.example.simpsonsapp.di

import android.content.Context
import androidx.room.Room
import com.example.simpsonsapp.data.local.AppDatabase
import com.example.simpsonsapp.data.remote.SimpsonsApi
import com.example.simpsonsapp.data.repository.EpisodeRepositoryImpl
import com.example.simpsonsapp.domain.repository.EpisodeRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
//aca entiendo que falto un url para retrofit y ese enfpoint para la api
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideSimpsonsApi(): SimpsonsApi {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        //si esto lo considero una API, deberia tener una http url no?
        return Retrofit.Builder()
            //aca nos falta un http url para que se alimnete repository con  el retrofit que es remoto
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SimpsonsApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "simpsons_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideEpisodeRepository(
        simpsonsApi: SimpsonsApi,
        appDatabase: AppDatabase
    ): EpisodeRepository {
        return EpisodeRepositoryImpl(simpsonsApi, appDatabase)
    }
}
