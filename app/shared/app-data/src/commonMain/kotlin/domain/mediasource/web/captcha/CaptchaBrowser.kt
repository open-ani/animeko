/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import me.him188.ani.app.domain.mediasource.web.LoadedPage

/**
 * 浏览器 cookie. CEF 能提供完整属性; Android WebView 只能拿到 name=value, 其余为 `null`.
 */
data class BrowserCookie(
    val name: String,
    val value: String,
    val domain: String? = null,
    val path: String? = null,
    val expiresEpochMillis: Long? = null,
    val secure: Boolean = false,
    val httpOnly: Boolean = false,
)

/**
 * 资源请求拦截决定, 用于视频资源嗅探.
 */
enum class InterceptDecision {
    /** 放行该请求 */
    Continue,

    /** 取消该请求 (已捕获到目标资源) */
    Block,
}

/**
 * 平台浏览器适配器. 平台只实现 "一个能被驱动的浏览器", 不含任何业务逻辑.
 *
 * ### 线程铁律
 *
 * - 所有方法都是 `suspend` (或立即返回), 内部自行 marshal 到 CEF/Main 线程;
 * - 浏览器回调线程 (CEF 的 EDT、Android 的 Main) 上只允许 `tryEmit` / `complete`,
 *   禁止任何形式的等待 (`runBlocking`、`invokeAndWait`、信号量等);
 * - [collectCookies] 用 `suspendCancellableCoroutine` 桥接回调, 由调用方协程消费, 永不阻塞 UI 线程.
 */
interface CaptchaBrowser : AutoCloseable {
    /**
     * 浏览器的真实 User-Agent, 用于 HTTP 侧身份对齐 (`cf_clearance` 绑定 UA).
     */
    val userAgent: String

    /**
     * 主 frame 每次加载完成时 emit (url, html). 回调线程上通过 `tryEmit` 发射.
     */
    val pageLoads: SharedFlow<LoadedPage>

    /**
     * 是否正在加载页面. 供交互对话框显示进度条.
     */
    val isLoading: StateFlow<Boolean>

    suspend fun navigate(url: String)

    /**
     * 当前页面的快照 (url + html). 页面未就绪时返回 `null`.
     */
    suspend fun currentPage(): LoadedPage?

    /**
     * 收集 [urls] 各自可见的 cookies (去重由调用方负责).
     */
    suspend fun collectCookies(urls: List<String>): List<BrowserCookie>

    /**
     * 设置资源请求拦截器 (视频资源嗅探). 传 `null` 清除.
     *
     * [handler] 会在浏览器网络线程被调用, 必须快速返回, 禁止阻塞.
     */
    fun setResourceInterceptor(handler: ((url: String) -> InterceptDecision)?)

    /**
     * 浏览器视图 (desktop `SwingPanel` / android `AndroidView`).
     */
    @Composable
    fun View(modifier: Modifier)
}

interface CaptchaBrowserFactory {
    /**
     * 当前平台是否支持浏览器. iOS 为 `false`.
     */
    val isSupported: Boolean

    /**
     * 平台推荐的最大暖会话数 (LRU 上限). 桌面 3, Android 2.
     */
    val recommendedMaxSessions: Int get() = 3

    /**
     * 创建一个新的浏览器实例. [isSupported] 为 `false` 时抛出 [UnsupportedOperationException].
     */
    suspend fun create(): CaptchaBrowser
}

/**
 * 无浏览器平台 (iOS) 与无头环境 (测试工具) 使用的工厂.
 */
object UnsupportedCaptchaBrowserFactory : CaptchaBrowserFactory {
    override val isSupported: Boolean get() = false
    override suspend fun create(): CaptchaBrowser =
        throw UnsupportedOperationException("CaptchaBrowser is not supported on this platform")
}
