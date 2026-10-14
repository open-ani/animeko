/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.him188.ani.app.platform.AniCefApp
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.network.CefRequest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

// CEF 只有全局的 User-Agent 设置. 为单个浏览器指定 UA 需要两处覆写:
// - [applyUserAgent]: 在 `onBeforeResourceLoad` 中覆写请求头, 对所有 frame 的请求生效;
// - [overrideNavigatorUserAgent]: 通过 DevTools 覆写页面脚本读到的 `navigator.userAgent`.
//   只作用于主 frame 所在的渲染进程, 跨站 iframe 中的脚本仍读到 CEF 默认的 UA.

private val logger = logger("CefUserAgent")

/**
 * 以 [userAgent] 发出这个请求. `null` 时不做改变.
 */
internal fun CefRequest.applyUserAgent(userAgent: String?) {
    if (userAgent != null) setHeaderByName("User-Agent", userAgent, true)
}

/**
 * 返回在这个 client 创建的浏览器就绪 (`onAfterCreated`) 时完成的信号. 须在 CEF 线程、创建浏览器之前调用.
 */
internal fun CefClient.browserCreatedSignal(): CompletableDeferred<Unit> {
    val created = CompletableDeferred<Unit>()
    addLifeSpanHandler(
        object : CefLifeSpanHandlerAdapter() {
            override fun onAfterCreated(browser: CefBrowser?) {
                created.complete(Unit)
            }
        },
    )
    return created
}

/**
 * 让页面脚本读到的 `navigator.userAgent` 为 [userAgent]. 须在浏览器就绪后、加载目标页面前调用.
 *
 * 覆写在浏览器的 DevTools 会话上, 随浏览器关闭而失效. 失败时只记录日志: 请求头仍由 [applyUserAgent] 覆写.
 */
internal suspend fun CefBrowser.overrideNavigatorUserAgent(userAgent: String) {
    val params = buildJsonObject { put("userAgent", userAgent) }.toString()
    try {
        val result = AniCefApp.suspendCoroutineOnCefContext {
            devToolsClient?.executeDevToolsMethod("Emulation.setUserAgentOverride", params)
        }
        if (result == null) {
            logger.warn { "DevTools client is unavailable, navigator.userAgent is not overridden" }
            return
        }
        withTimeoutOrNull(3.seconds) { result.await() }
            ?: logger.warn { "Timed out overriding navigator.userAgent" }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(e) { "Failed to override navigator.userAgent" }
    }
}

/**
 * 浏览器未覆写时实际使用的 User-Agent. 失败时返回 `null`. 须在浏览器就绪后调用.
 *
 * `onBeforeResourceLoad` 时请求头里还没有 `User-Agent` (由网络层稍后添加), 因此只能向 DevTools 查询.
 */
internal suspend fun CefBrowser.queryDefaultUserAgent(): String? {
    return try {
        val result = AniCefApp.suspendCoroutineOnCefContext {
            devToolsClient?.executeDevToolsMethod("Browser.getVersion")
        } ?: return null
        val json = withTimeoutOrNull(3.seconds) { result.await() } ?: return null
        Json.parseToJsonElement(json).jsonObject["userAgent"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.warn(e) { "Failed to query the default User-Agent" }
        null
    }
}
