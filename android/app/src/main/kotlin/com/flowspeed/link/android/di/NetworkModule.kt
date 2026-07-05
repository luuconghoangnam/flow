package com.flowspeed.link.android.di

import com.flowspeed.link.shared.storage.BaseAppSettingsStorage
import com.flowspeed.link.shared.util.AppHostNameVerifier
import com.flowspeed.link.shared.util.AppSSLFactoryProvider
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.internal.tls.OkHostnameVerifier
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Network DI module (Android).
 *
 * Provides: OkHttpClient (shared singleton), SSL factory, hostname verifier.
 * Forces HTTP/1.1 to avoid HTTP/2 head-of-line blocking for parallel part downloads.
 */
val networkModule = module {
    single {
        val appSettingsStorage: BaseAppSettingsStorage = get()
        AppSSLFactoryProvider(ignoreSSLCertificates = appSettingsStorage.ignoreSSLCertificates)
    }
    single {
        val appSettingsStorage: BaseAppSettingsStorage = get()
        AppHostNameVerifier(
            delegateHostnameVerifier = OkHostnameVerifier,
            ignoreHostNameVerification = appSettingsStorage.ignoreSSLCertificates
        )
    }
    single<OkHttpClient> {
        val sslFactory: AppSSLFactoryProvider = get()
        val hostnameVerifier: AppHostNameVerifier = get()
        OkHttpClient.Builder()
            .protocols(listOf(Protocol.HTTP_1_1))
            .dispatcher(Dispatcher().apply {
                maxRequests = Int.MAX_VALUE
                maxRequestsPerHost = Int.MAX_VALUE
            })
            .connectionPool(
                okhttp3.ConnectionPool(
                    maxIdleConnections = 5,
                    keepAliveDuration = 30,
                    timeUnit = TimeUnit.SECONDS
                )
            )
            .sslSocketFactory(sslFactory.createSSLSocketFactory(), sslFactory.trustManager)
            .hostnameVerifier(hostnameVerifier)
            .build()
    }
}
