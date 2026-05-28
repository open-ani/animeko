package me.him188.ani.app.platform

import kotlinx.coroutines.runBlocking
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.HttpClientProvider
import org.koin.core.KoinApplication

internal actual fun KoinApplication.startProxyProviderBeforeInit() {
    runBlocking {
        when (val proxyProvider = koin.get<HttpClientProvider>()) {
            is DefaultHttpClientProvider -> proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }
}
