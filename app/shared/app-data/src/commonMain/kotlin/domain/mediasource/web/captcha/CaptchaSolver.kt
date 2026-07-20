/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.utils.ktor.ScopedHttpClient

/**
 * [WebSessionManager.solve] 的结果.
 */
sealed interface SolveOutcome {
    data object Solved : SolveOutcome

    /** 仍被挡. [reason] 为最后一次判定的原因 (可能为 `null`). */
    data class Failed(val reason: BlockReason?) : SolveOutcome

    /** 用户关闭了对话框. */
    data object Cancelled : SolveOutcome

    /** 当前平台不支持交互解决 (无浏览器实现). */
    data object Unsupported : SolveOutcome
}

/**
 * 自动解决策略 (预留接缝, v1 无实现).
 *
 * `solve(interactive = false)` 遍历注入的策略列表, 按 [canAttempt] 过滤, 从便宜到贵依次 [attempt],
 * 首个让 [SolveContext.evaluate] 返回 [PageVerdict.Ok] 的策略胜出.
 * 所有策略共用同一个 `PageEvaluator` 判定成功, 因此 "自动解成功" 与 "解完能继续搜索" 恒等.
 */
interface CaptchaSolver {
    val id: String

    /** 便宜的预判 (按 kind / host 允许表). */
    fun canAttempt(reason: BlockReason.Captcha, host: String): Boolean

    /** 尝试解决; 成功与否由 [SolveContext.evaluate] 判定. */
    suspend fun attempt(ctx: SolveContext): SolveOutcome
}

class SolveContext internal constructor(
    val request: SolveRequest,
    /** jar 背书、UA 已对齐的 HTTP client. 纯 HTTP 策略只需要它. */
    val http: ScopedHttpClient,
    /** 懒创建浏览器; 纯 HTTP 策略永不调用. */
    val acquireBrowser: suspend () -> CaptchaBrowser,
    /** 唯一真相来源: 判定 [LoadedPage] 是否已解决. */
    val evaluate: suspend (LoadedPage) -> PageVerdict<*>,
)

/**
 * 备用取数路由 (预留接缝, v1 无实现).
 *
 * "换一条路取数": HTML 页被验证页挡住时改走站点公开 API 等, 绕过验证码而非解决它.
 * [WebSessionManager.fetchPage] 在发起常规请求之前查询命中 host 的路由;
 * 路由失败 (返回 `null`) 则回落到常规请求 → 验证码流程.
 *
 * 路由是硬编码的 per-site 适配, 隔离在这一层, 不污染通用解析与 solver 抽象.
 */
interface SearchRoute {
    val id: String

    fun matches(host: String): Boolean

    /** 直接取数并返回判决; 不适用或失败时返回 `null`. */
    suspend fun <T> fetch(
        url: String,
        expectation: PageExpectation<T>,
        http: ScopedHttpClient,
    ): PageVerdict<T>?
}
