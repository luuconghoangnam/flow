package com.flowspeed.link.desktop.di

import com.flowspeed.link.desktop.storage.AppSettingsStorage
import com.flowspeed.link.shared.util.AppSSLFactoryProvider
import com.flowspeed.link.shared.util.AppHostNameVerifier
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.internal.tls.OkHostnameVerifier
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Network DI module.
 *
 * Provides: OkHttpClient (shared singleton), SSL factory, hostname verifier.
 * Connection pool is configured to release idle connections after 30s
 * to minimize memory usage when the app is idle.
 */
val networkModule = module {
    single {
        val appSettingsStorage: AppSettingsStorage = get()
        AppSSLFactoryProvider(ignoreSSLCertificates = appSettingsStorage.ignoreSSLCertificates)
    }
    single {
        val appSettingsStorage: AppSettingsStorage = get()
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
