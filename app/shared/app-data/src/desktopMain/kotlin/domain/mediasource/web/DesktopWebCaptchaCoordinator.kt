/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.atomicfu.atomic
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import me.him188.ani.app.domain.media.resolver.WebResource
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.platform.AniCefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefRendering
import org.cef.browser.CefRequestContext
import org.cef.callback.CefCookieVisitor
import org.cef.callback.CefStringVisitor
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefCookie
import org.cef.network.CefCookieManager
import org.cef.network.CefRequest
import java.awt.Component
import javax.swing.JPanel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

class DesktopWebCaptchaCoordinator : WebCaptchaCoordinator {
    private data class SolvedSessionEntry(
        val key: String,
    )

    private data class InteractiveSolveState(
        val request: WebCaptchaRequest,
        val session: DesktopCaptchaSession,
        val deferred: CompletableDeferred<WebCaptchaSolveResult>,
        val token: Int,
    )

    private var interactiveSolveState by mutableStateOf<InteractiveSolveState?>(null)
    private var nextToken by mutableIntStateOf(1)
    private val solvedResults = linkedMapOf<String, WebCaptchaSolveResult.Solved>()
    private val sessions = linkedMapOf<String, DesktopCaptchaSession>()
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

        val session = getOrCreateSession(request)
        val deferred = CompletableDeferred<WebCaptchaSolveResult>()
        val token = nextToken++
        session.addPageObserver(token) { page ->
            val kind = page.detectMeaningfulCaptcha(request)
            if (kind == null && deferred.isActive) {
                deferred.complete(
                    WebCaptchaSolveResult.Solved(
                        page.finalUrl,
                        session.collectCookies(page.finalUrl),
                    ),
                )
            }
        }
        interactiveSolveState = InteractiveSolveState(request, session, deferred, token)
        session.loadUrl(request.pageUrl)

        return try {
            while (deferred.isActive) {
                session.snapshotCurrentPage()?.let { page ->
                    val kind = page.detectMeaningfulCaptcha(request)
                    if (kind == null) {
                        deferred.complete(
                            WebCaptchaSolveResult.Solved(
                                page.finalUrl,
                                session.collectCookies(page.finalUrl),
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

    @Composable
    override fun ComposeContent() {
        val state = interactiveSolveState ?: return
        val coroutineScope = rememberCoroutineScope()
        val dismiss: () -> Unit = {
            if (state.deferred.isActive) {
                state.deferred.complete(WebCaptchaSolveResult.Cancelled)
            }
            interactiveSolveState = null
        }
        Dialog(
            onDismissRequest = dismiss,
            properties = DialogProperties(
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
        ) {
            DesktopCaptchaDialogContent(
                pageUrl = state.request.pageUrl,
                onDismiss = dismiss,
                onConfirm = {
                    coroutineScope.launch {
                        val page = state.session.snapshotCurrentPage()
                        val finalUrl = page?.finalUrl ?: state.request.pageUrl
                        if (state.deferred.isActive) {
                            state.deferred.complete(
                                WebCaptchaSolveResult.Solved(
                                    finalUrl,
                                    state.session.collectCookies(finalUrl),
                                ),
                            )
                        }
                        interactiveSolveState = null
                    }
                },
            ) {
                SwingPanel(
                    background = Color.Transparent,
                    factory = { state.session.component },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private suspend fun solveWithSession(
        session: DesktopCaptchaSession,
        request: WebCaptchaRequest,
    ): WebCaptchaSolveResult {
        val page = session.loadPage(request.pageUrl, timeoutMillis = 8_000)
            ?: return WebCaptchaSolveResult.StillBlocked(request.kind)
        var lastKind = page.detectMeaningfulCaptcha(request)
        if (lastKind == null) {
            return WebCaptchaSolveResult.Solved(
                page.finalUrl,
                session.collectCookies(page.finalUrl),
            )
        }

        repeat(6) {
            delay(1000)
            val currentPage = session.snapshotCurrentPage() ?: return@repeat
            val currentKind = currentPage.detectMeaningfulCaptcha(request)
            if (currentKind == null) {
                return WebCaptchaSolveResult.Solved(
                    currentPage.finalUrl,
                    session.collectCookies(currentPage.finalUrl),
                )
            }
            lastKind = currentKind
        }

        return WebCaptchaSolveResult.StillBlocked(lastKind ?: request.kind)
    }

    private fun getOrCreateSession(request: WebCaptchaRequest): DesktopCaptchaSession {
        val key = request.storageKey()
        return sessions.getOrPut(key) { createSession() }
    }

    private fun rememberSolved(
        request: WebCaptchaRequest,
        session: DesktopCaptchaSession,
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
    ): DesktopCaptchaSession? {
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

    private fun createSession(): DesktopCaptchaSession {
        return runBlocking {
            AniCefApp.suspendCoroutineOnCefContext {
                val client = AniCefApp.createClient()
                    ?: return@suspendCoroutineOnCefContext DesktopCaptchaSession(EmptyComponent)

                val session = DesktopCaptchaSession(EmptyComponent)
                client.addLoadHandler(
                    object : CefLoadHandlerAdapter() {
                        override fun onLoadEnd(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            httpStatusCode: Int,
                        ) {
                            if (browser == null || frame?.isMain != true) return
                            session.dispatchLoadedPage(browser)
                        }
                    },
                )
                client.addRequestHandler(
                    object : CefRequestHandlerAdapter() {
                        override fun getResourceRequestHandler(
                            browser: CefBrowser?,
                            frame: CefFrame?,
                            request: CefRequest?,
                            isNavigation: Boolean,
                            isDownload: Boolean,
                            requestInitiator: String?,
                            disableDefaultHandling: BoolRef?,
                        ): CefResourceRequestHandlerAdapter {
                            return object : CefResourceRequestHandlerAdapter() {
                                override fun onBeforeResourceLoad(
                                    browser: CefBrowser?,
                                    frame: CefFrame?,
                                    request: CefRequest?,
                                ): Boolean {
                                    if (browser != null && request != null && session.handleVideoRequest(browser, request)) {
                                        return true
                                    }
                                    return super.onBeforeResourceLoad(browser, frame, request)
                                }
                            }
                        }
                    },
                )

                val browser = client.createBrowser(
                    "about:blank",
                    CefRendering.DEFAULT,
                    true,
                    CefRequestContext.getGlobalContext(),
                )
                browser.setCloseAllowed()
                browser.createImmediately()
                session.attach(client, browser)
                session
            }
        }
    }

    private class DesktopCaptchaSession(
        var component: Component,
    ) {
        private data class VideoExtractionState(
            val deferred: CompletableDeferred<WebResource>,
            val resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
            val loadedNestedUrls: MutableSet<String>,
            val lastUrl: kotlinx.atomicfu.AtomicRef<String?>,
        )

        private var client: CefClient? = null
        private var browser: CefBrowser? = null
        private var pendingLoad: CompletableDeferred<WebCaptchaLoadedPage>? = null
        private val pageObservers = linkedMapOf<Int, (WebCaptchaLoadedPage) -> Unit>()
        private var videoExtractionState: VideoExtractionState? = null

        fun attach(client: CefClient, browser: CefBrowser) {
            this.client = client
            this.browser = browser
            component = browser.uiComponent
        }

        suspend fun loadPage(
            pageUrl: String,
            timeoutMillis: Long = 15_000,
        ): WebCaptchaLoadedPage? {
            val deferred = CompletableDeferred<WebCaptchaLoadedPage>()
            pendingLoad = deferred
            loadUrl(pageUrl)
            val initialPage = try {
                withTimeoutOrNull(timeoutMillis.coerceAtMost(5_000)) {
                    deferred.await()
                }
            } finally {
                if (pendingLoad == deferred) {
                    pendingLoad = null
                }
            }
            return settleLoadedPage(pageUrl, initialPage, timeoutMillis)
        }

        fun loadUrl(pageUrl: String) {
            AniCefApp.runOnCefContext {
                browser?.loadURL(pageUrl)
            }
        }

        fun snapshotCurrentPage(): WebCaptchaLoadedPage? {
            return runBlocking {
                withTimeoutOrNull(1_000) {
                    val deferred = CompletableDeferred<WebCaptchaLoadedPage?>()
                    AniCefApp.runOnCefContext {
                        val currentBrowser = browser
                        val currentUrl = currentBrowser?.url
                        if (currentBrowser == null || currentUrl.isNullOrBlank()) {
                            deferred.complete(null)
                            return@runOnCefContext
                        }
                        currentBrowser.getSource(
                            object : CefStringVisitor {
                                override fun visit(source: String?) {
                                    deferred.complete(
                                        WebCaptchaLoadedPage(
                                            finalUrl = currentUrl,
                                            html = source.orEmpty(),
                                        ),
                                    )
                                }
                            },
                        )
                    }
                    deferred.await()
                }
            }
        }

        fun extractVideoResource(
            pageUrl: String,
            timeoutMillis: Long,
            resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
        ): WebResource? {
            val deferred = CompletableDeferred<WebResource>()
            val state = VideoExtractionState(
                deferred = deferred,
                resourceMatcher = resourceMatcher,
                loadedNestedUrls = linkedSetOf(pageUrl),
                lastUrl = atomic(pageUrl),
            )
            videoExtractionState = state
            loadUrl(pageUrl)
            return try {
                runBlocking {
                    withTimeoutOrNull(timeoutMillis) {
                        deferred.await()
                    }
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

        fun dispatchLoadedPage(browser: CefBrowser) {
            browser.getSource(
                object : CefStringVisitor {
                    override fun visit(source: String?) {
                        val page = WebCaptchaLoadedPage(
                            finalUrl = browser.url.orEmpty(),
                            html = source.orEmpty(),
                        )
                        pendingLoad?.takeIf { it.isActive }?.complete(page)
                        pageObservers.values.forEach { it(page) }
                    }
                },
            )
        }

        fun handleVideoRequest(
            browser: CefBrowser,
            request: CefRequest,
        ): Boolean {
            val state = videoExtractionState ?: return false
            val url = request.url
            return when (state.resourceMatcher(url)) {
                WebViewVideoExtractor.Instruction.Continue -> false
                WebViewVideoExtractor.Instruction.FoundResource -> {
                    state.deferred.complete(WebResource(url))
                    true
                }

                WebViewVideoExtractor.Instruction.LoadPage -> {
                    if (browser.url == url || state.lastUrl.value == url) {
                        return false
                    }
                    if (!state.loadedNestedUrls.add(url)) {
                        return false
                    }
                    state.lastUrl.value = url
                    AniCefApp.runOnCefContext {
                        browser.executeJavaScript(
                            "window.location.href=${Json.encodeToString(String.serializer(), url)};",
                            "",
                            1,
                        )
                    }
                    true
                }
            }
        }

        fun collectCookies(url: String): List<String> {
            return runBlocking {
                withTimeoutOrNull(1_000) {
                    val deferred = CompletableDeferred<List<String>>()
                    AniCefApp.runOnCefContext {
                        val cookies = mutableListOf<String>()
                        val visitor = object : CefCookieVisitor {
                            override fun visit(
                                cookie: CefCookie?,
                                count: Int,
                                total: Int,
                                deleteCookie: BoolRef?,
                            ): Boolean {
                                cookie ?: return count + 1 < total
                                cookies += "${cookie.name}=${cookie.value}"
                                if (count + 1 >= total && deferred.isActive) {
                                    deferred.complete(cookies)
                                }
                                return count + 1 < total
                            }
                        }
                        val scheduled = CefCookieManager.getGlobalManager()
                            .visitUrlCookies(url, true, visitor)
                        if (!scheduled && deferred.isActive) {
                            deferred.complete(emptyList())
                        }
                    }
                    deferred.await()
                } ?: emptyList()
            }
        }

        fun dispose() {
            AniCefApp.runOnCefContext {
                browser?.close(true)
                client?.dispose()
            }
        }

        private fun settleLoadedPage(
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

            while (System.currentTimeMillis() <= deadlineMillis) {
                val currentPage = snapshotCurrentPage() ?: lastPage
                if (currentPage != null) {
                    lastPage = currentPage
                }
                val candidate = lastPage
                if (
                    candidate != null &&
                    candidate.isRelevantFor(request) &&
                    candidate.hasMeaningfulHtml() &&
                    WebCaptchaDetector.detect(candidate.finalUrl, candidate.html) == null
                ) {
                    return candidate
                }
                Thread.sleep(250)
            }

            return lastPage
        }
    }

    private companion object {
        val EmptyComponent = JPanel()
    }
}

@Composable
internal fun DesktopCaptchaDialogContent(
    pageUrl: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit,
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
                    .height(56.dp),
            ) {
                TextButton(onClick = onDismiss) {
                    Text("返回", color = Color.White)
                }
                Text(
                    text = requestTitleForDesktopCaptcha(pageUrl),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
                TextButton(
                    onClick = onConfirm,
                    modifier = Modifier.align(Alignment.CenterEnd),
                ) {
                    Text("✓", color = Color.White)
                }
            }
            content()
        }
    }
}

internal fun requestTitleForDesktopCaptcha(pageUrl: String): String {
    return runCatching { java.net.URI(pageUrl).host }
        .getOrNull()
        .orEmpty()
        .ifBlank { "验证码验证" }
}
