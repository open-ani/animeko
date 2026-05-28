package me.him188.ani.app.platform

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.DefaultHttpClientProvider
import me.him188.ani.app.domain.foundation.HttpClientProvider
import org.koin.core.KoinApplication

internal actual fun KoinApplication.startProxyProviderBeforeInit() {
    when (val proxyProvider = koin.get<HttpClientProvider>()) {
        is DefaultHttpClientProvider -> CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            proxyProvider.startProxyListening(holdingInstanceMatrixSequence())
        }
    }
}
