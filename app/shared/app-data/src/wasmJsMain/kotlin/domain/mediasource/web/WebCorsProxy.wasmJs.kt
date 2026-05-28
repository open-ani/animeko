package me.him188.ani.app.domain.mediasource.web

internal actual fun rewriteWebCorsProxyUrl(url: String): String {
    if (!url.startsWith("https://") && !url.startsWith("http://")) {
        return url
    }
    return "${webLocationOrigin()}/__animeko_proxy?url=${encodeURIComponent(url)}"
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun webLocationOrigin(): String = js("window.location.origin")

@OptIn(ExperimentalWasmJsInterop::class)
private fun encodeURIComponent(value: String): String = js("encodeURIComponent(value)")
