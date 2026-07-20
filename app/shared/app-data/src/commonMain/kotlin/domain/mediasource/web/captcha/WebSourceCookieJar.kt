/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.util.date.GMTDate
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import me.him188.ani.app.domain.mediasource.web.normalizedSessionHost
import me.him188.ani.utils.platform.currentTimeMillis

/**
 * Web 数据源的统一 cookie 真相: HTTP 请求 (通过 `CookieJarFeature` 在 HttpClient 构造时注入)、
 * 播放器 WebView 注入 ([getCookieHeaderValues])、浏览器解决成果导入 ([addBrowserCookies]) 共用这一份.
 *
 * 域匹配规则: 精确 host (去 `www.` 前缀) 或 `.domain` 后缀匹配,
 * 保证 `cf_clearance` 这类域级 cookie 能覆盖到播放页所在子域.
 *
 * 有意不做磁盘持久化: CEF / WebView 自身的 cookie store 天然持久,
 * 重启后首次被挡时浏览器仍持有 clearance, 可快速重新通过.
 */
class WebSourceCookieJar : CookiesStorage {
    private class StoredCookie(
        val name: String,
        val value: String,
        /** 归一化 (小写, 去 `www.` 与前导 `.`) 后的 host 或 domain. */
        val domain: String,
        /** `true`: 精确 host 匹配 (无 domain 属性); `false`: 后缀匹配 (域级 cookie). */
        val hostOnly: Boolean,
        val path: String,
        val expiresEpochMillis: Long?,
        val secure: Boolean,
        val httpOnly: Boolean,
    )

    private val lock = SynchronizedObject()
    private val cookies = mutableListOf<StoredCookie>()

    override suspend fun get(requestUrl: Url): List<Cookie> {
        val host = requestUrl.host.normalizeHost() ?: return emptyList()
        val isSecure = requestUrl.protocol.name == "https"
        val path = requestUrl.encodedPath.ifBlank { "/" }
        return synchronized(lock) {
            evictExpiredLocked()
            cookies.filter { it.matches(host, path, isSecure) }
                .map { it.toKtorCookie() }
        }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) {
        val requestHost = requestUrl.host.normalizeHost() ?: return
        addCookieImpl(requestHost, cookie.domain, cookie.name, cookie.value, cookie.path, run {
            cookie.expires?.timestamp ?: cookie.maxAge?.let { currentTimeMillis() + it * 1000L }
        }, cookie.secure, cookie.httpOnly)
    }

    override fun close() {
    }

    /**
     * 导入浏览器解决验证码后收集到的 cookies. [pageUrl] 用于无 domain 属性时的降级归属.
     */
    fun addBrowserCookies(pageUrl: String, browserCookies: List<BrowserCookie>) {
        val pageHost = normalizedSessionHost(pageUrl) ?: return
        for (c in browserCookies) {
            if (c.name.isBlank()) continue
            addCookieImpl(
                requestHost = pageHost,
                domainAttribute = c.domain,
                name = c.name,
                value = c.value,
                path = c.path,
                expiresEpochMillis = c.expiresEpochMillis,
                secure = c.secure,
                httpOnly = c.httpOnly,
            )
        }
    }

    /**
     * 同步获取 [url] 可见的 cookies, 以 `name=value` 形式返回.
     * 供 `matcher.patchConfig` 给播放器 WebView 注入 (非 suspend 上下文).
     */
    fun getCookieHeaderValues(url: String): List<String> {
        val parsed = runCatching { Url(url) }.getOrNull() ?: return emptyList()
        val host = parsed.host.normalizeHost() ?: return emptyList()
        val isSecure = parsed.protocol.name == "https"
        val path = parsed.encodedPath.ifBlank { "/" }
        return synchronized(lock) {
            evictExpiredLocked()
            cookies.filter { it.matches(host, path, isSecure) }
                .map { "${it.name}=${it.value}" }
        }
    }

    /**
     * 丢弃 [host] (及其子域) 的所有 cookies. 用于 solve 成功后又被挡时的自动失效.
     */
    fun clearForHost(host: String) {
        val normalized = host.normalizeHost() ?: return
        synchronized(lock) {
            cookies.removeAll { it.domain == normalized || it.domain.endsWith(".$normalized") }
        }
    }

    private fun addCookieImpl(
        requestHost: String,
        domainAttribute: String?,
        name: String,
        value: String,
        path: String?,
        expiresEpochMillis: Long?,
        secure: Boolean,
        httpOnly: Boolean,
    ) {
        val domainNormalized = domainAttribute?.normalizeHost()
        val hostOnly = domainNormalized == null
        val effectiveDomain = domainNormalized ?: requestHost
        val stored = StoredCookie(
            name = name,
            value = value,
            domain = effectiveDomain,
            hostOnly = hostOnly,
            path = path?.ifBlank { null } ?: "/",
            expiresEpochMillis = expiresEpochMillis,
            secure = secure,
            httpOnly = httpOnly,
        )
        synchronized(lock) {
            cookies.removeAll { it.name == name && it.domain == effectiveDomain && it.path == stored.path }
            // 过期时间在过去 = 删除该 cookie
            if (expiresEpochMillis == null || expiresEpochMillis > currentTimeMillis()) {
                cookies.add(stored)
            }
        }
    }

    private fun StoredCookie.matches(host: String, requestPath: String, isSecure: Boolean): Boolean {
        if (secure && !isSecure) return false
        val domainMatches = if (hostOnly) {
            // 精确匹配 (host 已归一化去 www)
            host == domain
        } else {
            host == domain || host.endsWith(".$domain")
        }
        if (!domainMatches) return false
        return requestPath == path ||
                (requestPath.startsWith(path) && (path.endsWith("/") || requestPath.getOrNull(path.length) == '/'))
    }

    private fun StoredCookie.toKtorCookie(): Cookie = Cookie(
        name = name,
        value = value,
        domain = if (hostOnly) null else domain,
        path = path,
        expires = expiresEpochMillis?.let { GMTDate(it) },
        secure = secure,
        httpOnly = httpOnly,
    )

    private fun evictExpiredLocked() {
        val now = currentTimeMillis()
        cookies.removeAll { it.expiresEpochMillis != null && it.expiresEpochMillis <= now }
    }

    private fun String.normalizeHost(): String? {
        return lowercase().removePrefix(".").removePrefix("www.").takeIf { it.isNotBlank() }
    }
}

/**
 * per-host User-Agent 对齐注册表.
 *
 * `cf_clearance` 绑定 User-Agent: solve 成功时记录浏览器的真实 UA ([setUserAgent]),
 * `WebSourceIdentityFeature` 对该 host 的后续 HTTP 请求覆写 `User-Agent`,
 * 保证 HTTP 侧身份与清掉挑战的浏览器完全一致.
 */
class WebSourceIdentityRegistry {
    private val lock = SynchronizedObject()
    private val userAgents = mutableMapOf<String, String>()

    /**
     * 记录 [host] 的 UA. [host] 会被归一化 (小写, 去 `www.`).
     */
    fun setUserAgent(host: String, userAgent: String) {
        val normalized = normalizedSessionHost("https://$host") ?: return
        if (userAgent.isBlank()) return
        synchronized(lock) {
            userAgents[normalized] = userAgent
        }
    }

    /**
     * 查询 [host] 应使用的 UA. 支持父域匹配: `play.example.com` 会命中为 `example.com` 记录的 UA.
     */
    fun userAgentFor(host: String): String? {
        val normalized = normalizedSessionHost("https://$host") ?: return null
        return synchronized(lock) {
            var candidate: String? = normalized
            while (candidate != null) {
                userAgents[candidate]?.let { return@synchronized it }
                candidate = candidate.substringAfter('.', "").takeIf { it.contains('.') }
            }
            null
        }
    }
}
