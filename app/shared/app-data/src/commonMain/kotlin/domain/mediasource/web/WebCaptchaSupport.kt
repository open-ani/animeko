/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.compose.runtime.Composable
import io.ktor.http.Url
import me.him188.ani.app.domain.media.resolver.WebResource
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.utils.xml.Document

enum class WebCaptchaKind {
    Image,
    Cloudflare,
    CloudflareTurnstile,
    Unknown,
}

data class WebCaptchaRequest(
    val mediaSourceId: String,
    val pageUrl: String,
    val kind: WebCaptchaKind,
)

data class WebCaptchaLoadedPage(
    val finalUrl: String,
    val html: String,
)

sealed interface WebCaptchaSolveResult {
    data class Solved(
        val finalUrl: String,
        val cookies: List<String>,
    ) : WebCaptchaSolveResult

    data class StillBlocked(
        val kind: WebCaptchaKind,
    ) : WebCaptchaSolveResult

    data object Cancelled : WebCaptchaSolveResult

    data object Unsupported : WebCaptchaSolveResult
}

interface WebCaptchaCoordinator {
    @Composable
    fun ComposeContent() {
    }

    fun getSolvedCookies(
        mediaSourceId: String,
        pageUrl: String,
    ): List<String> {
        return emptyList()
    }

    suspend fun extractVideoResourceInSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
        timeoutMillis: Long,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource? {
        return null
    }

    suspend fun loadPageInSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
    ): WebCaptchaLoadedPage? {
        return null
    }

    suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult

    suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult

    fun resetSolvedSession(mediaSourceId: String) {
    }
}

object NoopWebCaptchaCoordinator : WebCaptchaCoordinator {
    override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
        return WebCaptchaSolveResult.Unsupported
    }

    override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
        return WebCaptchaSolveResult.Unsupported
    }
}

class CaptchaRequiredException(
    val request: WebCaptchaRequest,
) : Exception("Captcha required: ${request.kind} @ ${request.pageUrl}")

class WebPageCaptchaException(
    val url: String,
    val kind: WebCaptchaKind,
) : Exception("Captcha detected while loading $url: $kind")

object WebCaptchaDetector {
    fun detect(pageUrl: String, html: String): WebCaptchaKind? {
        val lowerHtml = html.lowercase()
        val lowerUrl = pageUrl.lowercase()

        if (
            "cf-turnstile" in lowerHtml ||
            "turnstile.render" in lowerHtml ||
            "challenges.cloudflare.com/turnstile" in lowerHtml
        ) {
            return WebCaptchaKind.CloudflareTurnstile
        }

        val hasChallengeUrlMarker =
            "__cf_chl_" in lowerUrl ||
                "/cdn-cgi/challenge-platform/h/" in lowerUrl ||
                "/cdn-cgi/challenge-platform/orchestrate/" in lowerUrl
        val hasBlockingTitle =
            "<title>just a moment" in lowerHtml ||
                "checking your browser before accessing" in lowerHtml
        val hasBlockingText =
            "challenge-error-text" in lowerHtml ||
                "enable javascript and cookies to continue" in lowerHtml ||
                "cf-browser-verification" in lowerHtml ||
                "id=\"cf-challenge\"" in lowerHtml ||
                "id='cf-challenge'" in lowerHtml
        val hasChallengeScript =
            "window._cf_chl_opt" in lowerHtml ||
                "__cf_chl_" in lowerHtml ||
                "/cdn-cgi/challenge-platform/h/" in lowerHtml ||
                "/cdn-cgi/challenge-platform/orchestrate/" in lowerHtml

        if (
            hasChallengeUrlMarker ||
            hasBlockingText ||
            hasChallengeScript ||
            (hasBlockingTitle && (
                "challenge-platform" in lowerHtml ||
                    "cf-ray" in lowerHtml ||
                    "window.__cf\$cv\$params" in lowerHtml
                ))
        ) {
            return WebCaptchaKind.Cloudflare
        }

        val hasSafeLineChallenge =
            "/.safeline/static/favicon.png" in lowerHtml ||
                "id=\"slg-box\"" in lowerHtml ||
                "id=\"slg-title\"" in lowerHtml ||
                ("window.product_data" in lowerHtml && "slg-bg" in lowerHtml)
        if (hasSafeLineChallenge) {
            return WebCaptchaKind.Unknown
        }

        if (
            "captcha" in lowerHtml && (
                "<img" in lowerHtml ||
                    "verification code" in lowerHtml ||
                    "verify" in lowerHtml
                )
        ) {
            return WebCaptchaKind.Image
        }

        return null
    }
}

fun WebCaptchaKind.displayName(): String = when (this) {
    WebCaptchaKind.Image -> "图片验证码"
    WebCaptchaKind.Cloudflare -> "Cloudflare 验证"
    WebCaptchaKind.CloudflareTurnstile -> "Cloudflare Turnstile 验证"
    WebCaptchaKind.Unknown -> "验证码"
}

internal fun WebCaptchaRequest.storageKey(): String {
    val host = normalizedSessionHost(pageUrl) ?: pageUrl.lowercase()
    return "$mediaSourceId@$host"
}

internal fun selectSolvedSessionKey(
    mediaSourceId: String,
    pageUrl: String,
    solvedKeys: Set<String>,
    solvedByMediaSource: Map<String, String>,
): String? {
    val exactKey = WebCaptchaRequest(
        mediaSourceId = mediaSourceId,
        pageUrl = pageUrl,
        kind = WebCaptchaKind.Unknown,
    ).storageKey()
    if (exactKey in solvedKeys) {
        return exactKey
    }
    val fallbackKey = solvedByMediaSource[mediaSourceId] ?: return null
    if (fallbackKey !in solvedKeys) {
        return null
    }
    return fallbackKey
}

internal fun normalizedSessionHost(pageUrl: String): String? {
    return runCatching { Url(pageUrl).host.lowercase() }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
}

internal fun normalizedStorageOrigin(pageUrl: String): String? {
    val url = runCatching { Url(pageUrl) }.getOrNull() ?: return null
    val host = url.host.lowercase().removePrefix("www.")
    if (host.isBlank()) return null
    val defaultPort = url.protocol.defaultPort
    val port = if (url.port == defaultPort) "" else ":${url.port}"
    return "${url.protocol.name}://$host$port"
}

internal fun WebCaptchaLoadedPage.isRelevantFor(request: WebCaptchaRequest): Boolean {
    if (
        finalUrl.isBlank() ||
        finalUrl == "about:blank" ||
        finalUrl.startsWith("chrome-error://")
    ) {
        return false
    }
    val requestHost = normalizedSessionHost(request.pageUrl) ?: return true
    val pageHost = normalizedSessionHost(finalUrl) ?: return false
    return pageHost == requestHost
}

internal fun WebCaptchaLoadedPage.hasMeaningfulHtml(): Boolean {
    val trimmed = html.trim()
    if (trimmed.isBlank()) {
        return false
    }
    return trimmed.contains("<html", ignoreCase = true) ||
        trimmed.contains("<body", ignoreCase = true) ||
        trimmed.length >= 128
}

internal fun WebCaptchaLoadedPage.detectMeaningfulCaptcha(
    request: WebCaptchaRequest,
): WebCaptchaKind? {
    if (!isRelevantFor(request) || !hasMeaningfulHtml()) {
        return request.kind
    }
    if (isFallbackHomePageFor(request)) {
        return request.kind
    }
    return WebCaptchaDetector.detect(finalUrl, html)
}

internal fun WebCaptchaLoadedPage.isUsableSolvedPage(
    request: WebCaptchaRequest,
): Boolean {
    return detectMeaningfulCaptcha(request) == null
}

internal fun WebCaptchaLoadedPage.isFallbackHomePageFor(request: WebCaptchaRequest): Boolean {
    val requestUrl = runCatching { Url(request.pageUrl) }.getOrNull() ?: return false
    val pageUrl = runCatching { Url(finalUrl) }.getOrNull() ?: return false
    val requestOrigin = normalizedStorageOrigin(request.pageUrl) ?: return false
    val pageOrigin = normalizedStorageOrigin(finalUrl) ?: return false
    if (requestOrigin != pageOrigin) {
        return false
    }
    val requestPath = requestUrl.encodedPath.trimEnd('/').ifBlank { "/" }
    val pagePath = pageUrl.encodedPath.trimEnd('/').ifBlank { "/" }
    return requestPath != "/" && pagePath == "/"
}

internal fun Document.isSearchCooldownPage(): Boolean {
    val normalizedText = text()
        .replace(Regex("\\s+"), " ")
        .trim()
    val hasCooldownMessage = normalizedText.contains("请不要频繁操作") ||
        normalizedText.contains("請不要頻繁操作")
    val hasSearchIntervalMessage = normalizedText.contains("搜索时间间隔") ||
        normalizedText.contains("搜索時間間隔")
    val hasCooldownContainer = select(".msg-jump").isNotEmpty()
    val hasHistoryBackRedirect = select("a[href^=\"javascript:history.back\"]").isNotEmpty()
    return hasCooldownMessage &&
        hasSearchIntervalMessage &&
        (hasCooldownContainer || hasHistoryBackRedirect)
}
