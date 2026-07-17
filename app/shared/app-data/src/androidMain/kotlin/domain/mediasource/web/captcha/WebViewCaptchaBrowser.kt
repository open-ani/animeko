/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.platform.Context
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds

/**
 * android.webkit.WebView 实现的 [CaptchaBrowser].
 *
 * 线程模型: WebView 的方法只在 Main 线程调用 (suspend 方法内部 marshal);
 * WebViewClient 回调 (Main 线程) 上只做 `tryEmit` / `resume`.
 */
class WebViewCaptchaBrowser private constructor(
    val webView: WebView,
) : CaptchaBrowser {
    private val _pageLoads = MutableSharedFlow<LoadedPage>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val pageLoads: SharedFlow<LoadedPage> get() = _pageLoads

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> get() = _isLoading

    private val interceptor = atomic<((String) -> InterceptDecision)?>(null)

    override val userAgent: String
        get() = webView.settings.userAgentString ?: WebSettings.getDefaultUserAgent(webView.context)

    override suspend fun navigate(url: String) = withContext(Dispatchers.Main.immediate) {
        webView.loadUrl(url)
    }

    override suspend fun currentPage(): LoadedPage? = withContext(Dispatchers.Main.immediate) {
        val currentUrl = webView.url ?: return@withContext null
        withTimeoutOrNull(2.seconds) {
            suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(OUTER_HTML_SCRIPT) { rawHtml ->
                    if (cont.isActive) {
                        cont.resume(LoadedPage(finalUrl = currentUrl, html = decodeJavascriptString(rawHtml)))
                    }
                }
            }
        }
    }

    override suspend fun executeJavaScript(script: String) = withContext(Dispatchers.Main.immediate) {
        webView.evaluateJavascript(script, null)
    }

    override suspend fun collectCookies(urls: List<String>): List<BrowserCookie> =
        withContext(Dispatchers.Main.immediate) {
            val manager = CookieManager.getInstance()
            manager.flush()
            // Android 降级路径: 只能拿到 name=value, 无 domain/expiry 等属性
            urls.flatMap { url ->
                manager.getCookie(url)
                    ?.split(";")
                    ?.mapNotNull { raw ->
                        val trimmed = raw.trim()
                        if (trimmed.isBlank()) return@mapNotNull null
                        val name = trimmed.substringBefore("=").trim()
                        if (name.isBlank()) return@mapNotNull null
                        BrowserCookie(
                            name = name,
                            value = trimmed.substringAfter("=", missingDelimiterValue = ""),
                        )
                    }
                    .orEmpty()
            }
        }

    override fun setResourceInterceptor(handler: ((String) -> InterceptDecision)?) {
        interceptor.value = handler
    }

    @Composable
    override fun View(modifier: Modifier) {
        AndroidView(
            factory = {
                webView.also { view ->
                    (view.parent as? ViewGroup)?.removeView(view)
                }
            },
            modifier = modifier,
        )
    }

    override fun close() {
        webView.post {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.destroy()
        }
    }

    private fun setup() {
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                _isLoading.value = true
                super.onPageStarted(view, url, favicon)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                _isLoading.value = false
                val finalUrl = view.url?.takeIf { it.isNotEmpty() } ?: return
                view.evaluateJavascript(OUTER_HTML_SCRIPT) { rawHtml ->
                    _pageLoads.tryEmit(
                        LoadedPage(finalUrl = finalUrl, html = decodeJavascriptString(rawHtml)),
                    )
                }
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                val url = request.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                val handler = interceptor.value
                if (handler != null && handler(url) == InterceptDecision.Block) {
                    return WebResourceResponse(
                        "text/plain",
                        "UTF-8",
                        500,
                        "Internal Server Error",
                        emptyMap(),
                        ByteArrayInputStream(ByteArray(0)),
                    )
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onLoadResource(view: WebView, url: String) {
                // shouldInterceptRequest 覆盖不到的资源 (如 media) 由这里兜底通知
                interceptor.value?.invoke(url)
                super.onLoadResource(view, url)
            }
        }
    }

    private fun decodeJavascriptString(rawHtml: String?): String {
        val value = rawHtml ?: return ""
        return runCatching {
            Json.parseToJsonElement(value).jsonPrimitive.content
        }.getOrDefault(value)
    }

    companion object {
        private const val OUTER_HTML_SCRIPT =
            "(function(){var d=document.documentElement; return d ? d.outerHTML : '';})()"

        @SuppressLint("SetJavaScriptEnabled")
        internal suspend fun create(context: Context): WebViewCaptchaBrowser =
            withContext(Dispatchers.Main.immediate) {
                val webView = WebView(context)
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                }
                WebViewCaptchaBrowser(webView).apply { setup() }
            }
    }
}

class AndroidCaptchaBrowserFactory(
    private val context: Context,
) : CaptchaBrowserFactory {
    override val isSupported: Boolean get() = true

    override val recommendedMaxSessions: Int get() = 2

    override suspend fun create(): CaptchaBrowser = WebViewCaptchaBrowser.create(context)
}
