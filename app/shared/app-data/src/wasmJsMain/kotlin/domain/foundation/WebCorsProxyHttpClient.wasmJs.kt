package me.him188.ani.app.domain.foundation

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.http.takeFrom

internal actual fun HttpClient.installWebCorsProxy() {
    plugin(HttpSend).intercept { request ->
        val url = request.url.toString()
        if (url == "https://mikanime.tv" || url.startsWith("https://mikanime.tv/") ||
            url == "https://mikanani.me" || url.startsWith("https://mikanani.me/")
        ) {
            request.url.takeFrom("${webLocationOrigin()}/__animeko_proxy?url=${encodeURIComponent(url)}")
        }
        return@intercept execute(request)
    }
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun webLocationOrigin(): String = js("window.location.origin")

@OptIn(ExperimentalWasmJsInterop::class)
private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")
