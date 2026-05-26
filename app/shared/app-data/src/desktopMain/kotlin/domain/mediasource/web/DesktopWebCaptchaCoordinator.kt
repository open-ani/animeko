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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.time.Duration.Companion.milliseconds

class DesktopWebCaptchaCoordinator(
    private val topBar: DesktopCaptchaTopBar = DefaultDesktopCaptchaTopBar,
) : WebCaptchaCoordinator {
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
        return findSolvedResult(mediaSourceId, pageUrl)?.cookies.orEmpty()
    }

    override suspend fun extractVideoResourceInSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
        timeoutMillis: Long,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource? {
        val selectedKey = findSolvedSessionKey(mediaSourceId, pageUrl) ?: return null
        sessions[selectedKey]?.let { session ->
            return session.extractVideoResource(pageUrl, timeoutMillis, resourceMatcher)
        }
        solvedResults[selectedKey] ?: return null
        return withTemporarySession { session ->
            session.extractVideoResource(pageUrl, timeoutMillis, resourceMatcher)
        }
    }

    override suspend fun loadPageInSolvedSession(
        mediaSourceId: String,
        pageUrl: String,
    ): WebCaptchaLoadedPage? {
        val selectedKey = findSolvedSessionKey(mediaSourceId, pageUrl) ?: return null
        sessions[selectedKey]?.let { session ->
            return session.loadPage(pageUrl)
        }
        solvedResults[selectedKey] ?: return null
        return withTemporarySession { session ->
            session.loadPage(pageUrl)
        }
    }

    override suspend fun tryAutoSolve(request: WebCaptchaRequest): WebCaptchaSolveResult {
        solvedResults[request.storageKey()]?.let {
            return it
        }

        val key = request.storageKey()
        val session = getOrCreateSession(request)
        return try {
            val result = solveWithSession(session, request)
            rememberSolved(request, session, result)
            result
        } finally {
            disposeSession(key, session)
        }
    }

    override suspend fun solveInteractively(request: WebCaptchaRequest): WebCaptchaSolveResult {
        solvedResults[request.storageKey()]?.let {
            return it
        }

        val key = request.storageKey()
        val session = getOrCreateSession(request)
        val deferred = CompletableDeferred<WebCaptchaSolveResult>()
        val token = nextToken++
        session.addPageObserver(token) { page ->
            // Search pages may bounce through challenge, notice, or redirect pages.
            // We only auto-close when the current page can be parsed into search results.
            if (page.shouldAutoCompleteInteractiveSolve(request) && deferred.isActive) {
                deferred.complete(
                    WebCaptchaSolveResult.Solved(
                        page.finalUrl,
                        runBlocking { collectCookiesForResult(session, request, page.finalUrl) },
                    ),
                )
            }
        }
        interactiveSolveState = InteractiveSolveState(request, session, deferred, token)

        var keepSession = false
        return try {
            while (deferred.isActive) {
                session.snapshotCurrentPage()?.let { page ->
                    if (page.shouldAutoCompleteInteractiveSolve(request)) {
                        deferred.complete(
                            WebCaptchaSolveResult.Solved(
                                page.finalUrl,
                                collectCookiesForResult(session, request, page.finalUrl),
                            ),
                        )
                        break
                    }
                }
                delay(1000.milliseconds)
            }
            deferred.await().also { result ->
                rememberSolved(request, session, result)
                keepSession = result is WebCaptchaSolveResult.Solved
            }
        } finally {
            session.removePageObserver(token)
            if (interactiveSolveState?.deferred == deferred) {
                interactiveSolveState = null
            }
            if (!keepSession) {
                disposeSession(key, session)
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
                sessions.remove(key)?.cancel()
            }
    }

    override fun cancelAutoResolutionRequests() {
        val interactiveSession = interactiveSolveState?.session
        val autoSessionKeys = sessions
            .filterValues { it !== interactiveSession }
            .keys
            .toList()
        autoSessionKeys.forEach { key ->
            sessions.remove(key)?.cancel()
            solvedResults.remove(key)
        }
        solvedByMediaSource.entries.removeAll { (_, entry) ->
            entry.key in autoSessionKeys
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

        LaunchedEffect(state) {
            state.session.loadUrlWhenReady(state.request.pageUrl)
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
                loadingState = state.session.loadingState,
                onDismiss = dismiss,
                onRefresh = {
                    state.session.refresh(state.request.pageUrl)
                },
                onConfirm = {
                    coroutineScope.launch {
                        val finalUrl = withContext(Dispatchers.IO) {
                            state.session.currentUrl() ?: state.request.pageUrl
                        }
                        if (state.deferred.isActive) {
                            state.deferred.complete(
                                WebCaptchaSolveResult.Solved(
                                    finalUrl,
                                    collectCookiesForResult(state.session, state.request, finalUrl),
                                ),
                            )
                        }
                        interactiveSolveState = null
                    }
                },
                topBar = topBar,
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
        if (page.shouldMarkAutoSolveAsSolved(request)) {
            return WebCaptchaSolveResult.Solved(
                page.finalUrl,
                collectCookiesForResult(session, request, page.finalUrl),
            )
        }

        repeat(6) {
            delay(1000.milliseconds)
            val currentPage = session.snapshotCurrentPage() ?: return@repeat
            val currentKind = currentPage.detectMeaningfulCaptcha(request)
            if (currentPage.shouldMarkAutoSolveAsSolved(request)) {
                return WebCaptchaSolveResult.Solved(
                    currentPage.finalUrl,
                    collectCookiesForResult(session, request, currentPage.finalUrl),
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

    private fun disposeSession(
        key: String,
        session: DesktopCaptchaSession,
    ) {
        if (interactiveSolveState?.session === session) {
            return
        }
        if (sessions[key] === session) {
            sessions.remove(key)
        }
        if (sessions.none { it.value === session }) {
            session.cancel()
        }
    }

    private suspend fun <T> withTemporarySession(
        block: suspend (DesktopCaptchaSession) -> T,
    ): T {
        val session = createSession()
        return try {
            block(session)
        } finally {
            session.cancel()
        }
    }

    private fun findSolvedResult(
        mediaSourceId: String,
        pageUrl: String,
    ): WebCaptchaSolveResult.Solved? {
        val selectedKey = findSolvedSessionKey(mediaSourceId, pageUrl) ?: return null
        return solvedResults[selectedKey]
    }

    private fun findSolvedSessionKey(
        mediaSourceId: String,
        pageUrl: String,
    ): String? {
        return selectSolvedSessionKey(
            mediaSourceId = mediaSourceId,
            pageUrl = pageUrl,
            solvedKeys = solvedResults.keys,
            solvedByMediaSource = solvedByMediaSource.mapValues { it.value.key },
        )
    }

    private suspend fun collectCookiesForResult(
        session: DesktopCaptchaSession,
        request: WebCaptchaRequest,
        finalUrl: String,
    ): List<String> {
        val urls = listOfNotNull(
            finalUrl,
            request.pageUrl,
            normalizedStorageOrigin(finalUrl),
            normalizedStorageOrigin(request.pageUrl),
        ).distinct()

        val cookies = mutableListOf<String>()
        for (url in urls) {
            cookies += session.collectCookies(url)
        }
        return mergeCookies(cookies)
    }

    private fun mergeCookies(cookies: Iterable<String>): List<String> {
        val merged = linkedMapOf<String, String>()
        for (cookie in cookies) {
            val trimmed = cookie.trim()
            if (trimmed.isBlank()) continue
            val name = trimmed.substringBefore("=").trim()
            if (name.isBlank()) continue
            merged[name] = trimmed
        }
        return merged.values.toList()
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
                        override fun onLoadingStateChange(
                            browser: CefBrowser?,
                            isLoading: Boolean,
                            canGoBack: Boolean,
                            canGoForward: Boolean,
                        ) {
                            if (browser == null) return
                            session.updateLoadingState(isLoading)
                        }

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
                                    if (browser != null && request != null && session.handleVideoRequest(
                                            browser,
                                            request,
                                        )
                                    ) {
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
        private val navigationGeneration = atomic(0)
        var loadingState by mutableStateOf(DesktopCaptchaLoadingState())
            private set

        fun attach(client: CefClient, browser: CefBrowser) {
            this.client = client
            this.browser = browser
            component = browser.uiComponent
        }

        suspend fun loadPage(
            pageUrl: String,
            timeoutMillis: Long = 15_000,
        ): WebCaptchaLoadedPage? {
            val request = WebCaptchaRequest(
                mediaSourceId = "",
                pageUrl = pageUrl,
                kind = WebCaptchaKind.Unknown,
            )
            snapshotCurrentPage()?.takeIf {
                it.matchesRequestedUrl(pageUrl) && it.isUsableSolvedPage(request)
            }?.let {
                return it
            }
            val deferred = CompletableDeferred<WebCaptchaLoadedPage>()
            pendingLoad = deferred
            loadUrl(pageUrl)
            val initialPage = try {
                withTimeoutOrNull(timeoutMillis.coerceAtMost(5_000).milliseconds) {
                    deferred.await()
                }
            } finally {
                if (pendingLoad == deferred) {
                    pendingLoad = null
                }
            }
            return settleLoadedPage(pageUrl, initialPage, timeoutMillis)
        }

        suspend fun loadUrlWhenReady(pageUrl: String) {
            val generation = navigationGeneration.incrementAndGet()
            awaitComponentAttached()

            repeat(4) { attempt ->
                if (navigationGeneration.value != generation) {
                    return
                }
                val previousUrl = currentUrl()
                loadUrl(pageUrl)
                if (awaitLoadAccepted(previousUrl, generation)) {
                    return
                }
                delay(((attempt + 1) * 150).milliseconds)
            }
        }

        fun loadUrl(pageUrl: String) {
            AniCefApp.runOnCefContext {
                browser?.loadURL(pageUrl)
            }
        }

        fun refresh(fallbackUrl: String) {
            navigationGeneration.incrementAndGet()
            AniCefApp.runOnCefContext {
                val currentUrl = browser?.url
                    ?.takeIf { it.isNotBlank() && it != "about:blank" }
                    ?: fallbackUrl
                browser?.loadURL(currentUrl)
            }
        }

        fun updateLoadingState(isLoading: Boolean) {
            loadingState = DesktopCaptchaLoadingState(isLoading)
        }

        private suspend fun awaitComponentAttached() {
            withTimeoutOrNull(1_000.milliseconds) {
                while (!isComponentAttached()) {
                    delay(50.milliseconds)
                }
            }
        }

        private suspend fun isComponentAttached(): Boolean {
            return withTimeoutOrNull(300.milliseconds) {
                val deferred = CompletableDeferred<Boolean>()
                AniCefApp.runOnCefContext {
                    deferred.complete(component.parent != null || component.isDisplayable || component.isShowing)
                }
                deferred.await()
            } == true
        }

        private suspend fun awaitLoadAccepted(
            previousUrl: String?,
            generation: Int,
        ): Boolean {
            return withTimeoutOrNull(700.milliseconds) {
                while (navigationGeneration.value == generation) {
                    if (loadingState.isLoading) {
                        return@withTimeoutOrNull true
                    }
                    val currentUrl = currentUrl()
                    if (isLoadedBrowserUrl(currentUrl) && currentUrl != previousUrl) {
                        return@withTimeoutOrNull true
                    }
                    delay(50.milliseconds)
                }
                false
            } == true
        }

        suspend fun currentUrl(): String? {
            return withTimeoutOrNull(1_000.milliseconds) {
                val deferred = CompletableDeferred<String?>()
                AniCefApp.runOnCefContext {
                    deferred.complete(browser?.url)
                }
                deferred.await()
            }
        }

        suspend fun snapshotCurrentPage(): WebCaptchaLoadedPage? {
            return withTimeoutOrNull(1_000.milliseconds) {
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

        suspend fun extractVideoResource(
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
                withTimeoutOrNull(timeoutMillis.milliseconds) {
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

        suspend fun collectCookies(url: String): List<String> {
            return withTimeoutOrNull(1_000.milliseconds) {
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

        fun dispose() {
            AniCefApp.runOnCefContext {
                browser?.close(true)
                client?.dispose()
            }
        }

        fun cancel() {
            pendingLoad?.cancel()
            pendingLoad = null
            videoExtractionState?.deferred?.cancel()
            videoExtractionState = null
            dispose()
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
                delay(250.milliseconds)
            }

            return lastPage?.takeIf { it.isUsableSolvedPage(request) }
        }

        private fun isLoadedBrowserUrl(url: String?): Boolean {
            return !url.isNullOrBlank() &&
                    url != "about:blank" &&
                    !url.startsWith("chrome-error://")
        }
    }

    private companion object {
        val EmptyComponent = JPanel()
    }
}

internal data class DesktopCaptchaLoadingState(
    val isLoading: Boolean = false,
)

fun interface DesktopCaptchaTopBar {
    @Composable
    fun Content(
        pageUrl: String,
        onDismiss: () -> Unit,
        onRefresh: () -> Unit,
        onConfirm: () -> Unit,
    )
}

val DefaultDesktopCaptchaTopBar = DesktopCaptchaTopBar { pageUrl, onDismiss, onRefresh, onConfirm ->
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.align(Alignment.CenterStart)) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "刷新",
                    tint = Color.White,
                )
            }
        }
        Text(
            text = requestTitleForDesktopCaptcha(pageUrl),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 104.dp),
        )
        IconButton(
            onClick = onConfirm,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = "完成",
                tint = Color.White,
            )
        }
    }
}

@Composable
internal fun DesktopCaptchaDialogContent(
    pageUrl: String,
    loadingState: DesktopCaptchaLoadingState = DesktopCaptchaLoadingState(),
    onDismiss: () -> Unit,
    onRefresh: () -> Unit = {},
    onConfirm: () -> Unit,
    topBar: DesktopCaptchaTopBar = DefaultDesktopCaptchaTopBar,
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
            topBar.Content(pageUrl, onDismiss, onRefresh, onConfirm)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            ) {
                if (loadingState.isLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                content()
            }
        }
    }
}

internal fun requestTitleForDesktopCaptcha(pageUrl: String): String {
    return runCatching { java.net.URI(pageUrl).host }
        .getOrNull()
        .orEmpty()
        .ifBlank { "验证码验证" }
}
