package com.akiba.app.di

import android.content.Context
import com.akiba.app.BuildConfig
import com.akiba.app.data.local.PrefKeys
import com.akiba.app.data.remote.api.*
import com.akiba.app.data.remote.api.AiApiService
import com.akiba.app.data.remote.api.ParseApiService
import com.akiba.app.data.remote.api.NotificationApiService
import com.akiba.app.data.remote.api.PaymentApiService
import com.akiba.app.data.local.dataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val authInterceptor = okhttp3.Interceptor { chain ->
            val token = runBlocking {
                context.dataStore.data.first()[PrefKeys.ACCESS_TOKEN]
            }
            val request = chain.request().newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
            }.build()
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        return OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(5, 30, TimeUnit.SECONDS))
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    @Provides @Singleton
    fun provideParseApiService(retrofit: Retrofit): ParseApiService =
        retrofit.create(ParseApiService::class.java)

    @Provides @Singleton
    fun provideAiApiService(retrofit: Retrofit): AiApiService =
        retrofit.create(AiApiService::class.java)

    @Provides @Singleton
    fun provideNotificationApiService(retrofit: Retrofit): NotificationApiService =
        retrofit.create(NotificationApiService::class.java)

    @Provides @Singleton
    fun providePaymentApiService(retrofit: Retrofit): PaymentApiService =
        retrofit.create(PaymentApiService::class.java)

    @Provides @Singleton
    fun provideAuthApiService(retrofit: Retrofit): AuthApiService =
        retrofit.create(AuthApiService::class.java)

    @Provides @Singleton
    fun provideTransactionApiService(retrofit: Retrofit): TransactionApiService =
        retrofit.create(TransactionApiService::class.java)

    @Provides @Singleton
    fun provideBudgetApiService(retrofit: Retrofit): BudgetApiService =
        retrofit.create(BudgetApiService::class.java)

    @Provides @Singleton
    fun provideSavingsApiService(retrofit: Retrofit): SavingsApiService =
        retrofit.create(SavingsApiService::class.java)
}
