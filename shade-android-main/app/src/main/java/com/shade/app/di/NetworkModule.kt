package com.shade.app.di

import android.content.Context
import android.util.Log
import com.shade.app.BuildConfig
import com.shade.app.data.remote.api.AuditService
import com.shade.app.data.remote.api.AuthService
import com.shade.app.data.remote.api.GroupService
import com.shade.app.data.remote.api.KeysService
import com.shade.app.data.remote.api.MediaService
import com.shade.app.data.remote.api.MessageService
import com.shade.app.data.remote.api.TranslationService
import com.shade.app.data.remote.api.UserService
import com.shade.app.data.remote.api.WebSessionService
import com.shade.app.data.remote.interceptor.TokenRefreshAuthenticator
import com.shade.app.data.remote.websocket.ShadeWebSocketManager
import com.shade.app.data.remote.websocket.ShadeWebSocketManagerImpl
import com.shade.app.util.ConnectivityObserver
import com.shade.app.util.NetworkConnectivityObserver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.Duration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthService(retrofit: Retrofit): AuthService {
        return retrofit.create(AuthService::class.java)
    }

    @Provides
    @Singleton
    fun provideUserService(retrofit: Retrofit): UserService {
        return retrofit.create(UserService::class.java)
    }

    @Provides
    @Singleton
    fun provideAuditService(retrofit: Retrofit): AuditService {
        return retrofit.create(AuditService::class.java)
    }

    @Provides
    @Singleton
    fun provideTranslationService(retrofit: Retrofit): TranslationService {
        return retrofit.create(TranslationService::class.java)
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        tokenRefreshAuthenticator: TokenRefreshAuthenticator
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .authenticator(tokenRefreshAuthenticator)
            .pingInterval(Duration.ofSeconds(30))
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(60))

        // Sertifika pinning — CERT_PIN_HASH doluysa etkinleştir
        val certPinHash = BuildConfig.CERT_PIN_HASH
        if (certPinHash.isNotBlank()) {
            val apiHost = BuildConfig.API_URL
                .removePrefix("https://")
                .removePrefix("http://")
                .substringBefore("/")
            val pinner = CertificatePinner.Builder()
                .add(apiHost, "sha256/$certPinHash")
                .build()
            builder.certificatePinner(pinner)
            Log.i("NetworkModule", "Certificate pinning enabled for $apiHost")
        } else {
            Log.w("NetworkModule", "CERT_PIN_HASH not set — certificate pinning disabled")
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideWebSocketManager(impl: ShadeWebSocketManagerImpl): ShadeWebSocketManager = impl

    @Provides
    @Singleton
    fun provideMediaService(retrofit: Retrofit): MediaService {
        return retrofit.create(MediaService::class.java)
    }

    @Provides
    @Singleton
    fun provideMessageService(retrofit: Retrofit): MessageService {
        return retrofit.create(MessageService::class.java)
    }

    @Provides
    @Singleton
    fun provideWebSessionService(retrofit: Retrofit): WebSessionService {
        return retrofit.create(WebSessionService::class.java)
    }

    @Provides
    @Singleton
    fun provideGroupService(retrofit: Retrofit): GroupService {
        return retrofit.create(GroupService::class.java)
    }

    @Provides
    @Singleton
    fun provideKeysService(retrofit: Retrofit): KeysService {
        return retrofit.create(KeysService::class.java)
    }

    @Provides
    @Singleton
    fun provideConnectivityObserver(@ApplicationContext context: Context): ConnectivityObserver {
        return NetworkConnectivityObserver(context)
    }
}
