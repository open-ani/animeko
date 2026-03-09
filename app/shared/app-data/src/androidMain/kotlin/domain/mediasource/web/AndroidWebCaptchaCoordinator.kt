/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.domain.media.resolver.WebResource
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.platform.Context
import me.him188.ani.app.platform.LocalContext
import java.io.ByteArrayInputStream
import java.util.concurrent.ConcurrentSkipListSet

class AndroidWebCaptchaCoordinator(
    private val context: Context,
) : WebCaptchaCoordinator {
    private data class SolvedSessionEntry(
        val key: String,
    )

    private data class InteractiveSolveState(
        val request: WebCaptchaRequest,
        val session: AndroidCaptchaSession,
        val deferred: CompletableDeferred<WebCaptchaSolveResult>,
        val token: Int,
    )

    private var interactiveSolveState by mutableStateOf<InteractiveSolveState?>(null)
    private var nextToken by mutableIntStateOf(1)
    private var attachedContext by mutableStateOf<Context?>(null)
    private val solvedResults = linkedMapOf<String, WebCaptchaSolveResult.Solved>()
    private val sessions = linkedMapOf<String, AndroidCaptchaSession>()
    private val solvedByMediaSource = linkedMapOf<String, SolvedSessionEntry>()

    override fun getSolvedCookies(
        mediaSourceId: String,
        pageUrl: String,
    ): List<String> {
        val key = storageKey(mediaSourceId, pageUrl)
        return solvedResults[key]?.cookies.orEmpty()
    }

    override suspend fun extractVideoResourceInSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
        timeoutMillis: Long,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource? {
        val session = findSolvedSession(mediaSourceId, pageUrl) ?: return null
        return session.extractVideoResource(pageUrl, timeoutMillis, resourceMatcher)
    }

    override suspend fun loadPageInSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
    ): WebCaptchaLoadedPage? {
        val session = findSolvedSession(mediaSourceId, pageUrl) ?: return null
        return session.loadPage(pageUrl)
    }

    override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
        solvedResults[request.storageKey()]?.let {
            return it
        }
        val session = getOrCreateSession(request)
        val result = solveWithSession(session, request)
        rememberSolved(request, session, result)
        return result
    }

    override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
        solvedResults[request.storageKey()]?.let {
            return it
        }

        val session = getOrCreateSession(request, preferUiContext = true)
        val deferred = CompletableDeferred<WebCaptchaSolveResult>()
        val token = nextToken++
        session.addPageObserver(token) { page ->
            val kind = page.detectMeaningfulCaptcha(request)
            if (kind == null && deferred.isActive) {
                deferred.complete(
                    WebCaptchaSolveResult.Solved(
                        page.finalUrl,
                        session.getCookies(page.finalUrl),
                    ),
                )
            }
        }
        interactiveSolveState = InteractiveSolveState(request, session, deferred, token)
        withContext(Dispatchers.Main) {
            session.loadUrl(request.pageUrl)
        }

        return try {
            while (deferred.isActive) {
                session.snapshotCurrentPage()?.let { page ->
                    val kind = page.detectMeaningfulCaptcha(request)
                    if (kind == null) {
                        deferred.complete(
                            WebCaptchaSolveResult.Solved(
                                page.finalUrl,
                                session.getCookies(page.finalUrl),
                            ),
                        )
                        break
                    }
                }
                delay(1000)
            }
            deferred.await().also { rememberSolved(request, session, it) }
        } finally {
            session.removePageObserver(token)
            if (interactiveSolveState?.deferred == deferred) {
                interactiveSolveState = null
            }
        }
    }

    override fun resetSolvedSession(mediaSourceId: String) {
        interactiveSolveState = interactiveSolveState?.takeUnless { it.request.mediaSourceId == mediaSourceId }
        solvedByMediaSource.remove(mediaSourceId)
        solvedResults.keys
            .filter { it.startsWith("$mediaSourceId@") }
            .toList()
            .forEach { key ->
                solvedResults.remove(key)
                sessions.remove(key)
            }
    }

    @Composable
    override fun ComposeContent() {
        val composeContext = LocalContext.current
        DisposableEffect(composeContext) {
            attachedContext = composeContext
            onDispose {
                if (attachedContext === composeContext) {
                    attachedContext = null
                }
            }
        }

        val state = interactiveSolveState ?: return
        val coroutineScope = rememberCoroutineScope()
        val dismiss: () -> Unit = {
            if (state.deferred.isActive) {
                state.deferred.complete(WebCaptchaSolveResult.Cancelled)
            }
            interactiveSolveState = null
        }
        BackHandler(onBack = dismiss)
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = Color.Black,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(56.dp),
                    ) {
                        TextButton(onClick = dismiss) {
                            Text("返回", color = Color.White)
                        }
                        Text(
                            text = requestTitle(state.request.pageUrl),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    val page = state.session.snapshotCurrentPage()
                                    val finalUrl = page?.finalUrl ?: state.request.pageUrl
                                    if (state.deferred.isActive) {
                                        state.deferred.complete(
                                            WebCaptchaSolveResult.Solved(
                                                finalUrl,
                                                state.session.getCookies(finalUrl),
                                            ),
                                        )
                                    }
                                    interactiveSolveState = null
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterEnd),
                        ) {
                            Text("✓", color = Color.White)
                        }
                    }
                    AndroidView(
                        factory = {
                            state.session.webView.also { webView ->
                                (webView.parent as? ViewGroup)?.removeView(webView)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    private suspend fun solveWithSession(
        session: AndroidCaptchaSession,
        request: WebCaptchaRequest,
    ): WebCaptchaSolveResult {
        val initialPage = session.loadPage(request.pageUrl, timeoutMillis = 8_000)
            ?: return WebCaptchaSolveResult.StillBlocked(request.kind)
        var lastKind = initialPage.detectMeaningfulCaptcha(request)
        if (lastKind == null) {
            return WebCaptchaSolveResult.Solved(
                initialPage.finalUrl,
                session.getCookies(initialPage.finalUrl),
            )
        }

        repeat(6) {
            delay(1000)
            val currentPage = session.snapshotCurrentPage() ?: return@repeat
            val currentKind = currentPage.detectMeaningfulCaptcha(request)
            if (currentKind == null) {
                return WebCaptchaSolveResult.Solved(
                    currentPage.finalUrl,
                    session.getCookies(currentPage.finalUrl),
                )
            }
            lastKind = currentKind
        }

        return WebCaptchaSolveResult.StillBlocked(lastKind ?: request.kind)
    }

    private suspend fun getOrCreateSession(
        request: WebCaptchaRequest,
        preferUiContext: Boolean = false,
    ): AndroidCaptchaSession {
        val key = request.storageKey()
        sessions[key]?.let { session ->
            if (!preferUiContext || session.usesUiContext || attachedContext == null) {
                return session
            }
        }
        return withContext(Dispatchers.Main) {
            val existing = sessions[key]
            if (existing != null && (!preferUiContext || existing.usesUiContext || attachedContext == null)) {
                return@withContext existing
            }

            val uiContext = attachedContext
            val sessionContext = if (preferUiContext) {
                uiContext ?: context
            } else {
                uiContext ?: context
            }
            AndroidCaptchaSession(
                context = sessionContext,
                usesUiContext = sessionContext === uiContext,
            ).also {
                sessions[key] = it
            }
        }
    }

    private fun rememberSolved(
        request: WebCaptchaRequest,
        session: AndroidCaptchaSession,
        result: WebCaptchaSolveResult,
    ) {
        if (result is WebCaptchaSolveResult.Solved) {
            val key = request.storageKey()
            sessions[key] = session
            solvedResults[key] = result
            solvedByMediaSource[request.mediaSourceId] = SolvedSessionEntry(key)
        }
    }

    private fun findSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
    ): AndroidCaptchaSession? {
        val selectedKey = selectSolvedSessionKey(
            mediaSourceId = mediaSourceId,
            pageUrl = pageUrl,
            solvedKeys = solvedResults.keys,
            solvedByMediaSource = solvedByMediaSource.mapValues { it.value.key },
        ) ?: return null
        return sessions[selectedKey]
    }

    private fun storageKey(
        mediaSourceId: String,
        pageUrl: String,
    ): String {
        return WebCaptchaRequest(
            mediaSourceId = mediaSourceId,
            pageUrl = pageUrl,
            kind = WebCaptchaKind.Unknown,
        ).storageKey()
    }

    private fun requestTitle(pageUrl: String): String {
        return runCatching { java.net.URI(pageUrl).host }
            .getOrNull()
            .orEmpty()
            .ifBlank { "验证码验证" }
    }

    private class AndroidCaptchaSession(
        context: Context,
        val usesUiContext: Boolean,
    ) {
        private data class VideoExtractionState(
            val deferred: CompletableDeferred<WebResource>,
            val resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
            val loadedNestedUrls: MutableSet<String>,
        )

        val webView = WebView(context)

        private var pendingLoad by mutableStateOf<CompletableDeferred<WebCaptchaLoadedPage>?>(null)
        private val pageObservers = linkedMapOf<Int, (WebCaptchaLoadedPage) -> Unit>()
        private var videoExtractionState: VideoExtractionState? = null

        init {
            configure(webView)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    dispatchCurrentPage(view)
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true && pendingLoad?.isActive == true) {
                        pendingLoad?.complete(
                            WebCaptchaLoadedPage(
                                finalUrl = view?.url ?: request.url.toString(),
                                html = "",
                            ),
                        )
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val url = request.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                    if (handleVideoResource(view, url)) {
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
                    handleVideoResource(view, url)
                    super.onLoadResource(view, url)
                }
            }
        }

        suspend fun loadPage(
            pageUrl: String,
            timeoutMillis: Long = 15_000,
        ): WebCaptchaLoadedPage? {
            val initialPage = withContext(Dispatchers.Main) {
                val deferred = CompletableDeferred<WebCaptchaLoadedPage>()
                pendingLoad = deferred
                webView.loadUrl(pageUrl)
                try {
                    withTimeoutOrNull(timeoutMillis.coerceAtMost(5_000)) {
                        deferred.await()
                    }
                } finally {
                    if (pendingLoad == deferred) {
                        pendingLoad = null
                    }
                }
            }
            return settleLoadedPage(pageUrl, initialPage, timeoutMillis)
        }

        suspend fun loadUrl(pageUrl: String) = withContext(Dispatchers.Main) {
            webView.loadUrl(pageUrl)
        }

        suspend fun snapshotCurrentPage(): WebCaptchaLoadedPage? = withContext(Dispatchers.Main) {
            val currentUrl = webView.url ?: return@withContext null
            val deferred = CompletableDeferred<WebCaptchaLoadedPage>()
            webView.evaluateJavascript(
                "(function(){var d=document.documentElement; return d ? d.outerHTML : '';})()",
            ) { rawHtml ->
                deferred.complete(
                    WebCaptchaLoadedPage(
                        finalUrl = currentUrl,
                        html = decodeJavascriptString(rawHtml),
                    ),
                )
            }
            deferred.await()
        }

        suspend fun extractVideoResource(
            pageUrl: String,
            timeoutMillis: Long,
            resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
        ): WebResource? = withContext(Dispatchers.Main) {
            val deferred = CompletableDeferred<WebResource>()
            val state = VideoExtractionState(
                deferred = deferred,
                resourceMatcher = resourceMatcher,
                loadedNestedUrls = ConcurrentSkipListSet<String>().apply {
                    add(pageUrl)
                },
            )
            videoExtractionState = state
            try {
                webView.loadUrl(pageUrl)
                withTimeoutOrNull(timeoutMillis) {
                    deferred.await()
                }
            } finally {
                if (videoExtractionState == state) {
                    videoExtractionState = null
                }
            }
        }

        fun addPageObserver(
            token: Int,
            observer: (WebCaptchaLoadedPage) -> Unit,
        ) {
            pageObservers[token] = observer
        }

        fun removePageObserver(token: Int) {
            pageObservers.remove(token)
        }

        fun getCookies(pageUrl: String): List<String> {
            CookieManager.getInstance().flush()
            return CookieManager.getInstance()
                .getCookie(pageUrl)
                ?.split(";")
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                .orEmpty()
        }

        @SuppressLint("SetJavaScriptEnabled")
        private fun configure(webView: WebView) {
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }

        private fun dispatchCurrentPage(webView: WebView) {
            if (webView.url.isNullOrEmpty()) return
            webView.evaluateJavascript(
                "(function(){var d=document.documentElement; return d ? d.outerHTML : '';})()",
            ) { rawHtml ->
                val page = WebCaptchaLoadedPage(
                    finalUrl = webView.url.orEmpty(),
                    html = decodeJavascriptString(rawHtml),
                )
                pendingLoad?.takeIf { it.isActive }?.complete(page)
                pageObservers.values.forEach { it(page) }
            }
        }

        private fun decodeJavascriptString(rawHtml: String?): String {
            val value = rawHtml ?: return ""
            return runCatching {
                Json.parseToJsonElement(value).jsonPrimitive.content
            }.getOrDefault(value)
        }

        private fun handleVideoResource(
            webView: WebView,
            url: String,
        ): Boolean {
            val state = videoExtractionState ?: return false
            return when (state.resourceMatcher(url)) {
                WebViewVideoExtractor.Instruction.Continue -> false
                WebViewVideoExtractor.Instruction.FoundResource -> {
                    state.deferred.complete(WebResource(url))
                    true
                }

                WebViewVideoExtractor.Instruction.LoadPage -> {
                    if (webView.url == url) {
                        return false
                    }
                    if (!state.loadedNestedUrls.add(url)) {
                        return false
                    }
                    webView.post {
                        if (state.deferred.isActive) {
                            webView.loadUrl(url)
                        }
                    }
                    false
                }
            }
        }

        private suspend fun settleLoadedPage(
            pageUrl: String,
            initialPage: WebCaptchaLoadedPage?,
            timeoutMillis: Long,
        ): WebCaptchaLoadedPage? {
            val request = WebCaptchaRequest(
                mediaSourceId = "",
                pageUrl = pageUrl,
                kind = WebCaptchaKind.Unknown,
            )
            val deadlineMillis = System.currentTimeMillis() + timeoutMillis
            var lastPage = initialPage
            var retryCount = 0
            var nextRetryAtMillis = System.currentTimeMillis()

            while (System.currentTimeMillis() <= deadlineMillis) {
                val currentPage = snapshotCurrentPage() ?: lastPage
                if (currentPage != null) {
                    lastPage = currentPage
                }
                val candidate = lastPage
                if (candidate != null && candidate.isUsableSolvedPage(request)) {
                    return candidate
                }
                if (
                    candidate != null &&
                    candidate.isFallbackHomePageFor(request) &&
                    retryCount < 2 &&
                    System.currentTimeMillis() >= nextRetryAtMillis
                ) {
                    retryCount++
                    nextRetryAtMillis = System.currentTimeMillis() + 750
                    loadUrl(pageUrl)
                }
                delay(250)
            }

            return lastPage?.takeIf { it.isUsableSolvedPage(request) }
        }
    }
}
