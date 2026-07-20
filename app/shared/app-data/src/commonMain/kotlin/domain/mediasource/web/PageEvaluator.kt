/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import me.him188.ani.app.domain.mediasource.web.format.SelectedChannelEpisodes
import me.him188.ani.utils.xml.Document
import me.him188.ani.utils.xml.Html
import kotlin.time.Duration

/**
 * 一次页面加载的结果, 可能来自直连 HTTP, 也可能来自浏览器 ([me.him188.ani.app.domain.mediasource.web.captcha.CaptchaBrowser]).
 */
data class LoadedPage(
    val finalUrl: String,
    val html: String,
    /**
     * HTTP 状态码. 浏览器加载时不可得, 为 `null`.
     */
    val status: Int? = null,
    /**
     * HTTP `Retry-After`. 仅在直连 HTTP 且响应携带时非 `null`.
     */
    val retryAfter: Duration? = null,
)

/**
 * 页面的期望: 调用方希望从页面中解析出什么内容.
 *
 * [PageEvaluator] 会优先尝试按照期望解析页面, 解析成功 ([PageVerdict.Ok]) 优先于一切启发式检测.
 */
sealed interface PageExpectation<out T> {
    /** 期望解析出条目列表 (搜索结果页) */
    data class SearchResults(val config: SelectorSearchConfig) : PageExpectation<List<WebSearchSubjectInfo>>

    /** 期望解析出剧集列表 (条目详情页) */
    data class SubjectDetails(
        val config: SelectorSearchConfig,
        /** episode 所属条目的完整 URL, 用于解析相对链接. */
        val subjectUrl: String,
    ) : PageExpectation<SelectedChannelEpisodes>

    /** 无 selector 可用时的兜底 (视频页等): 只要没有被挡特征就算 [PageVerdict.Ok]. */
    data object AnyContent : PageExpectation<Document>
}

/**
 * [PageEvaluator] 对一个页面的判决.
 */
sealed interface PageVerdict<out T> {
    /** 解析成功. */
    data class Ok<T>(val value: T, val document: Document) : PageVerdict<T>

    /**
     * 正常页面, 合法的 "无结果".
     * @param document 解析出的 DOM (若 html 可解析), 供调试工具用不同配置重新解析.
     */
    data class EmptyContent(val document: Document?) : PageVerdict<Nothing>

    /** 页面被挡 (验证码 / 限流 / 404 / 403). */
    data class Blocked(val reason: BlockReason) : PageVerdict<Nothing>
}

sealed interface BlockReason {
    data class Captcha(val kind: WebCaptchaKind) : BlockReason

    /** HTTP 429 或站内冷却页. */
    data class RateLimited(val retryAfter: Duration?) : BlockReason

    data object NotFound : BlockReason

    /** 4xx (通常是 403) 且无验证码特征. */
    data class Forbidden(val status: Int) : BlockReason
}

/**
 * 描述一次需要解决的验证码, 由 [BlockReason.Captcha] 产生.
 *
 * @see me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager.solve
 */
data class SolveRequest(
    val mediaSourceId: String,
    val pageUrl: String,
    val kind: WebCaptchaKind,
    /**
     * solve 成功与否的判定期望: 浏览器里的页面能以此期望解析出内容, 才算解决成功.
     */
    val expectation: PageExpectation<*>,
)

/**
 * 页面被挡时上抛的异常. `MediaFetcher` 按 [reason] 映射为对应的 fetch 状态.
 */
class BlockedException(
    val reason: BlockReason,
    val request: SolveRequest,
) : Exception("Blocked ($reason) @ ${request.pageUrl}")

/**
 * 唯一判决函数: 所有 "这个页面算不算被挡" 的判断都必须经过 [evaluate].
 *
 * 引擎解析路径、交互对话框的自动关闭、浏览器会话的页面加载共用同一个函数,
 * 保证 solve 的成功标准与 retry 的成功标准恒等.
 *
 * 判决顺序 (硬规则):
 * 1. HTTP 404 → [BlockReason.NotFound];
 * 2. 按 [PageExpectation] 解析, 解析出内容 → [PageVerdict.Ok], 直接结束 —— 即使启发式检测报警、即使状态码是 4xx;
 * 3. 站内冷却页 → [BlockReason.RateLimited];
 * 4. HTTP 429 → [BlockReason.RateLimited] (带 `Retry-After`);
 * 5. 启发式检测分类出验证码 → [BlockReason.Captcha];
 * 6. HTTP 403 无特征 → [BlockReason.Forbidden]; 468 → [BlockReason.Captcha] (Unknown);
 * 7. 以上都不是 → [PageVerdict.EmptyContent] (对 [PageExpectation.AnyContent] 则为 [PageVerdict.Ok]).
 */
class PageEvaluator {
    fun <T> evaluate(page: LoadedPage, expectation: PageExpectation<T>): PageVerdict<T> {
        // 1. 404
        if (page.status == 404) {
            return PageVerdict.Blocked(BlockReason.NotFound)
        }

        val document = runCatching { Html.parse(page.html) }.getOrNull()

        // 2. 解析优先: selector 能解析出内容就是最终真相.
        if (document != null) {
            parseByExpectation(document, page.finalUrl, expectation)?.let { value ->
                return PageVerdict.Ok(value, document)
            }
        }

        // 3. 站内冷却页
        if (document != null && document.isSearchCooldownPage()) {
            return PageVerdict.Blocked(BlockReason.RateLimited(retryAfter = null))
        }

        // 4. HTTP 429
        if (page.status == 429) {
            return PageVerdict.Blocked(BlockReason.RateLimited(page.retryAfter))
        }

        // 5. 启发式检测
        WebCaptchaDetector.detect(page.finalUrl, page.html)?.let { kind ->
            return PageVerdict.Blocked(BlockReason.Captcha(kind))
        }

        // 6. 无特征的被挡状态码
        when (page.status) {
            468 -> return PageVerdict.Blocked(BlockReason.Captcha(WebCaptchaKind.Unknown))
            403 -> return PageVerdict.Blocked(BlockReason.Forbidden(403))
        }

        // 7. 兜底
        if (expectation is PageExpectation.AnyContent && document != null && hasMeaningfulHtml(page.html)) {
            @Suppress("UNCHECKED_CAST")
            return PageVerdict.Ok(document, document) as PageVerdict<T>
        }
        return PageVerdict.EmptyContent(document)
    }

    private fun <T> parseByExpectation(
        document: Document,
        pageUrl: String,
        expectation: PageExpectation<T>,
    ): T? {
        @Suppress("UNCHECKED_CAST")
        return when (expectation) {
            is PageExpectation.SearchResults -> {
                selectSubjectsForCaptchaProbe(document, expectation.config)
                    ?.takeIf { it.isNotEmpty() } as T?
            }

            is PageExpectation.SubjectDetails -> {
                runCatching {
                    selectEpisodesImpl(document, expectation.subjectUrl, expectation.config)
                }.getOrNull()
                    ?.takeIf { it.episodes.isNotEmpty() } as T?
            }

            // AnyContent 没有强解析信号, 只能在排除全部被挡特征后才算 Ok (规则 7).
            PageExpectation.AnyContent -> null
        }
    }

    private fun hasMeaningfulHtml(html: String): Boolean {
        val trimmed = html.trim()
        if (trimmed.isBlank()) return false
        return trimmed.contains("<html", ignoreCase = true) ||
                trimmed.contains("<body", ignoreCase = true) ||
                trimmed.length >= 128
    }
}
