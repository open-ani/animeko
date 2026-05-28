package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.utils.ktor.ScopedHttpClient

internal actual suspend fun storeCaptchaCookies(
    client: ScopedHttpClient,
    pageUrl: String,
    cookies: List<String>,
) {
}
