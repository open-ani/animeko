/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tools.datasourcetestmcp.captcha

import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.DesktopOnnxImageCaptchaRecognizer
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.WebCaptchaKind
import me.him188.ani.app.domain.mediasource.web.captcha.BrowserImageCaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser
import me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.DesktopCaptchaBrowserFactory
import me.him188.ani.app.domain.mediasource.web.captcha.GirigiriSearchRoute
import me.him188.ani.app.domain.mediasource.web.captcha.MacCmsImageCaptchaSolver
import me.him188.ani.app.domain.mediasource.web.captcha.SolveOutcome
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceCookieJar
import me.him188.ani.app.domain.mediasource.web.captcha.WebSourceIdentityRegistry
import me.him188.ani.tools.datasourcetestmcp.McpCefApp
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.xml.Document
import java.io.File
import kotlin.time.Duration

/**
 * Web 数据源的取页会话: 与 App 走同一条链路 —— [WebSessionManager] 统一取页, [PageEvaluator] 统一判决,
 * 被验证码挡住时用与 App 完全相同的 solver 链自动解 (纯 HTTP 的 MacCMS 协议优先, 再退到浏览器 DOM).
 *
 * 与 App 的唯一差别是没有人工兜底: MCP 是无人值守的评测工具, 自动解不掉就抛 [CaptchaUnsolvedException],
 * 由调用方**终止这个数据源的流程** —— 而不是拿着验证页继续往下解析, 把 "被挡" 误报成 "selector 写错了".
 *
 * @param client 必须以 [cookieJar] 作为 cookie 存储, 并已装上 [identityRegistry] 的 per-host UA 覆写;
 * 否则浏览器解出来的会话 (cookie + UA) 传不到 HTTP 侧, 解完还是被挡.
 */
class WebSourceSession(
    val cookieJar: WebSourceCookieJar,
    val sessionManager: WebSessionManager,
) {
    /**
     * MCP server 的正式构造: 与 App 相同的 solver 链 + 按需初始化的 JCEF 浏览器.
     */
    constructor(
        client: ScopedHttpClient,
        cookieJar: WebSourceCookieJar,
        identityRegistry: WebSourceIdentityRegistry,
        backgroundScope: CoroutineScope,
        cefWorkDir: File = McpCefApp.defaultWorkDir(),
    ) : this(
        cookieJar,
        createDesktopWebSessionManager(client, cookieJar, identityRegistry, backgroundScope, cefWorkDir),
    )

    /**
     * 取一个页面, 必要时自动解验证码.
     *
     * - 限流: 等一轮 [requestInterval] 后重试一次 (与 App 的 `SelectorMediaSource.fetchPageOrThrow` 一致);
     * - 验证码: 自动 solve 后重试一次; 解不掉、或解完重试仍被挡, 抛 [CaptchaUnsolvedException];
     * - 其余判决 (404 / 403 / 空结果) 原样返回, 由调用方按各自的步骤语义解释.
     */
    suspend fun <T> fetchPage(
        mediaSourceId: String,
        url: String,
        expectation: PageExpectation<T>,
        requestInterval: Duration,
    ): WebPageFetchResult<T> {
        var verdict = sessionManager.fetchPage(url, expectation)

        verdict.blockedBy<BlockReason.RateLimited>()?.let { rateLimited ->
            // 限流不是验证码: 不开浏览器, 等一轮再试
            delay(rateLimited.retryAfter ?: requestInterval)
            verdict = sessionManager.fetchPage(url, expectation)
        }

        val captcha = verdict.blockedBy<BlockReason.Captcha>()
            ?: return WebPageFetchResult(url, verdict, autoSolvedCaptcha = null)

        val outcome = sessionManager.solve(
            SolveRequest(
                mediaSourceId = mediaSourceId,
                pageUrl = url,
                kind = captcha.kind,
                expectation = expectation,
            ),
            interactive = false, // 无人值守, 没有可以弹的交互对话框
        )
        if (outcome != SolveOutcome.Solved) {
            throw CaptchaUnsolvedException(url, captcha.kind, outcome)
        }

        verdict = sessionManager.fetchPage(url, expectation)
        verdict.blockedBy<BlockReason.Captcha>()?.let { stillBlocked ->
            // solve 报成功但页面仍被挡: 会话没有真的通过, 同样终止
            throw CaptchaUnsolvedException(url, stillBlocked.kind, SolveOutcome.Failed(stillBlocked))
        }
        return WebPageFetchResult(url, verdict, autoSolvedCaptcha = captcha.kind)
    }

    /**
     * 丢弃 [url] 所在 host 的暖会话与 cookie, 下次从干净状态重新解.
     */
    suspend fun invalidate(url: String) {
        sessionHostOf(url)?.let { sessionManager.invalidate(it) }
    }
}

/**
 * 与 App (`CommonKoinModule`) 相同的策略与顺序: 先便宜的纯 HTTP 协议, 再退到浏览器 DOM.
 */
private fun createDesktopWebSessionManager(
    client: ScopedHttpClient,
    cookieJar: WebSourceCookieJar,
    identityRegistry: WebSourceIdentityRegistry,
    backgroundScope: CoroutineScope,
    cefWorkDir: File,
): WebSessionManager {
    val evaluator = PageEvaluator()
    val browserFactory = LazyCefCaptchaBrowserFactory(cefWorkDir)
    val recognizer = DesktopOnnxImageCaptchaRecognizer()
    return WebSessionManager(
        browserFactory = browserFactory,
        evaluator = evaluator,
        cookieJar = cookieJar,
        identityRegistry = identityRegistry,
        client = client,
        backgroundScope = backgroundScope,
        solvers = listOf(
            MacCmsImageCaptchaSolver(recognizer),
            BrowserImageCaptchaSolver(recognizer),
        ),
        searchRoutes = listOf(GirigiriSearchRoute(evaluator)),
        maxSessions = browserFactory.recommendedMaxSessions,
    )
}

/**
 * 一次 [WebSourceSession.fetchPage] 的结果.
 */
class WebPageFetchResult<T>(
    val url: String,
    val verdict: PageVerdict<T>,
    /** 本次取页遇到并已自动解决掉的验证码类型; 没遇到验证码为 `null`. */
    val autoSolvedCaptcha: WebCaptchaKind?,
) {
    /** 页面 DOM; 被挡时为 `null`. */
    val document: Document? get() = verdict.documentOrNull()

    val blockReason: BlockReason? get() = (verdict as? PageVerdict.Blocked)?.reason
}

/**
 * 自动解验证码失败. MCP 无人值守, 唯一正确的反应是终止这个数据源的流程.
 */
class CaptchaUnsolvedException(
    val pageUrl: String,
    val kind: WebCaptchaKind,
    val outcome: SolveOutcome,
) : Exception("Captcha ($kind) at $pageUrl was not solved automatically: ${outcome.describe()}") {
    val host: String? = sessionHostOf(pageUrl)

    /** 可以直接放进 MCP 结果里的一句话结论. */
    val summary: String
        get() = "${host ?: pageUrl} 开了人机验证 ($kind), 自动解决失败 (${outcome.describe()}), 已终止该数据源的流程"
}

fun BlockReason.describe(): String = when (this) {
    is BlockReason.Captcha -> "人机验证 ($kind)"
    is BlockReason.RateLimited -> "被限流" + (retryAfter?.let { " (Retry-After ${it.inWholeSeconds}s)" }.orEmpty())
    BlockReason.NotFound -> "404 Not Found"
    is BlockReason.Forbidden -> "HTTP $status Forbidden"
}

fun PageVerdict<*>.documentOrNull(): Document? = when (this) {
    is PageVerdict.Ok<*> -> document
    is PageVerdict.EmptyContent -> document
    is PageVerdict.Blocked -> null
}

/**
 * host, 小写并去掉 `www.` 前缀. 与 app-data 内部的 `normalizedSessionHost` 同义 (那个是 internal).
 */
internal fun sessionHostOf(url: String): String? = runCatching { Url(url).host }.getOrNull()
    ?.lowercase()
    ?.removePrefix("www.")
    ?.takeIf { it.isNotBlank() }

private fun SolveOutcome.describe(): String = when (this) {
    SolveOutcome.Solved -> "已解决"
    SolveOutcome.Cancelled -> "已取消"
    SolveOutcome.Unsupported -> "当前环境没有可用的浏览器"
    is SolveOutcome.Failed -> "所有自动策略均未通过" + (reason?.let { " (最后判定: ${it.describe()})" }.orEmpty())
}

private inline fun <reified R : BlockReason> PageVerdict<*>.blockedBy(): R? =
    (this as? PageVerdict.Blocked)?.reason as? R

/**
 * 按需初始化 JCEF 的浏览器工厂: server 启动时不碰 CEF, 只有真的要用浏览器解验证码时才初始化.
 */
private class LazyCefCaptchaBrowserFactory(
    private val workDir: File,
) : CaptchaBrowserFactory {
    private val delegate = DesktopCaptchaBrowserFactory()

    override val isSupported: Boolean get() = delegate.isSupported
    override val recommendedMaxSessions: Int get() = delegate.recommendedMaxSessions

    override suspend fun create(): CaptchaBrowser {
        McpCefApp.initialize(workDir)
        return delegate.create()
    }
}
