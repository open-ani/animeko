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
import me.him188.ani.utils.xml.Html

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
    val searchProbe: WebCaptchaSearchProbe? = null,
)

/**
 * 只在“搜索页验证码”场景下使用。
 * 当浏览器页 URL 变化或刷新时，我们会尝试按当前 source 的 selector 解析页面；
 * 只有真的能解析出条目，才认为这次验证码处理已经完成。
 */
data class WebCaptchaSearchProbe(
    val searchConfig: SelectorSearchConfig,
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

        val hasInlineVerifyInput =
            "name=\"verify\"" in lowerHtml ||
                "name='verify'" in lowerHtml ||
                "placeholder=\"请输入验证码\"" in html ||
                "placeholder='请输入验证码'" in html ||
                "placeholder=\"請輸入驗證碼\"" in html ||
                "placeholder='請輸入驗證碼'" in html
        val hasInlineVerifyImage =
            "class=\"ds-verify-img\"" in lowerHtml ||
                "class='ds-verify-img'" in lowerHtml ||
                "/verify/index.html" in lowerHtml
        val hasInlineVerifySubmit =
            "class=\"verify-submit\"" in lowerHtml ||
                "class='verify-submit'" in lowerHtml ||
                "data-type=\"search\"" in lowerHtml ||
                "data-type='search'" in lowerHtml ||
                "提交驗證" in html ||
                "提交验证" in html
        if (
            hasInlineVerifyImage &&
            hasInlineVerifyInput &&
            hasInlineVerifySubmit
        ) {
            return WebCaptchaKind.Image
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

internal fun normalizedComparableUrl(pageUrl: String): String? {
    val url = runCatching { Url(pageUrl) }.getOrNull() ?: return null
    val origin = normalizedStorageOrigin(pageUrl) ?: return null
    val path = url.encodedPath.trimEnd('/').ifBlank { "/" }
    val query = url.encodedQuery.takeIf { it.isNotBlank() } ?: ""
    return buildString {
        append(origin)
        append(path)
        if (query.isNotBlank()) {
            append('?')
            append(query)
        }
    }
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

internal fun WebCaptchaLoadedPage.matchesRequestedUrl(pageUrl: String): Boolean {
    return normalizedComparableUrl(finalUrl) == normalizedComparableUrl(pageUrl)
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

internal fun WebCaptchaLoadedPage.hasSearchResults(
    searchProbe: WebCaptchaSearchProbe,
): Boolean {
    // We intentionally probe the live page with the source's real selector instead of
    // relying on captcha heuristics. Unknown WAF pages may look "captcha-free" but still
    // not be the actual search result page we need.
    val document = runCatching { Html.parse(html) }.getOrNull() ?: return false
    if (document.isSearchCooldownPage()) {
        return false
    }
    val subjects = selectSubjectsForCaptchaProbe(document, searchProbe.searchConfig) ?: return false
    return subjects.isNotEmpty()
}

internal fun WebCaptchaLoadedPage.shouldAutoCompleteInteractiveSolve(
    request: WebCaptchaRequest,
): Boolean {
    val searchProbe = request.searchProbe
    if (searchProbe != null) {
        // For search pages, only close the browser after the current page can be parsed
        // into real search results. This avoids closing on unknown captcha/waf pages.
        if (!matchesRequestedUrl(request.pageUrl)) {
            return false
        }
        return hasSearchResults(searchProbe)
    }
    return detectMeaningfulCaptcha(request) == null
}

internal fun WebCaptchaLoadedPage.shouldMarkAutoSolveAsSolved(
    request: WebCaptchaRequest,
): Boolean {
    val searchProbe = request.searchProbe
    if (searchProbe != null) {
        if (!matchesRequestedUrl(request.pageUrl)) {
            return false
        }
        return hasSearchResults(searchProbe)
    }
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
