/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.accept
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.media.resolver.WebResource
import me.him188.ani.app.domain.media.resolver.WebViewVideoExtractor
import me.him188.ani.app.domain.mediasource.web.BlockReason
import me.him188.ani.app.domain.mediasource.web.LoadedPage
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.app.domain.mediasource.web.PageExpectation
import me.him188.ani.app.domain.mediasource.web.PageVerdict
import me.him188.ani.app.domain.mediasource.web.SolveRequest
import me.him188.ani.app.domain.mediasource.web.normalizedSessionHost
import me.him188.ani.app.domain.mediasource.web.normalizedStorageOrigin
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.currentTimeMillis
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * 交互解决对话框的状态. app 根部唯一 dialog host 消费 [WebSessionManager.interactiveUi].
 */
class InteractiveSolveUi internal constructor(
    val request: SolveRequest,
    val browser: CaptchaBrowser,
    val onConfirm: () -> Unit,
    val onDismiss: () -> Unit,
    val onRefresh: () -> Unit,
) {
    val title: String get() = normalizedSessionHost(request.pageUrl) ?: "验证码验证"
}

/**
 * Web 数据源的页面加载与验证码解决编排核心.
 *
 * - [fetchPage]: 引擎的唯一页面入口. 直连 HTTP 优先, 被挡且有暖会话时用浏览器重载,
 *   60s 粘滞窗口内直接走浏览器; 站点恢复后自动降级回直连.
 * - [solve]: 解决编排. interactive 必定呈现对话框 (无缓存入口); auto 遍历 [solvers] (v1 为空).
 * - 会话注册表: key 为 host (去 `www.`), 每 host 最多一个活浏览器, LRU 上限 + 闲置 TTL 自动回收.
 * - 无 solvedResults 缓存: "已解决" 只体现为 jar 里的 cookie 和注册表里的暖会话.
 *
 * @param solvers 自动解决策略链 (预留接缝, v1 注入空列表).
 * @param searchRoutes 备用取数路由 (预留接缝, v1 注入空列表).
 */
class WebSessionManager(
    private val browserFactory: CaptchaBrowserFactory,
    private val evaluator: PageEvaluator,
    val cookieJar: WebSourceCookieJar,
    private val identityRegistry: WebSourceIdentityRegistry,
    private val client: ScopedHttpClient,
    private val backgroundScope: CoroutineScope,
    private val solvers: List<CaptchaSolver> = emptyList(),
    private val searchRoutes: List<SearchRoute> = emptyList(),
    private val maxSessions: Int = 3,
    private val idleTtl: Duration = 5.minutes,
    private val stickyWindow: Duration = 60.seconds,
    private val solveFailCooldown: Duration = 60.seconds,
    private val browserLoadTimeout: Duration = 12.seconds,
    private val getTimeMillis: () -> Long = { currentTimeMillis() },
    private val ioContext: CoroutineContext = Dispatchers.IO_,
) {
    val isInteractiveSupported: Boolean get() = browserFactory.isSupported

    // region 会话注册表 (全部状态由 [lock] 保护)

    private class BrowserSession(
        val browser: CaptchaBrowser,
    ) {
        var lastUsedAtMillis: Long = 0
        var refCount: Int = 0
    }

    private class ActiveSolve(
        val interactive: Boolean,
    ) {
        val deferred = CompletableDeferred<SolveOutcome>()
        var job: Job? = null
    }

    private class HostState {
        var session: BrowserSession? = null
        var lastHttpBlockedAtMillis: Long = 0
        var lastSolveFailedAtMillis: Long = 0
        var lastSolveSucceededAtMillis: Long = 0
        var activeSolve: ActiveSolve? = null
    }

    private val lock = Mutex()
    private val hostStates = mutableMapOf<String, HostState>()
    private val browserCreateSemaphore = Semaphore(MAX_CONCURRENT_BROWSER_CREATIONS)

    private fun hostStateLocked(host: String): HostState = hostStates.getOrPut(host) { HostState() }

    // endregion

    // region interactive UI 队列 (多槽位: 并发 solve 排队呈现, 不互相顶掉)

    private val interactiveUiQueue = mutableListOf<InteractiveSolveUi>()
    private val _interactiveUi = MutableStateFlow<InteractiveSolveUi?>(null)

    /**
     * 当前应呈现的交互解决对话框. app 根部唯一 dialog host 消费.
     */
    val interactiveUi: StateFlow<InteractiveSolveUi?> get() = _interactiveUi

    private suspend fun publishUi(ui: InteractiveSolveUi) = lock.withLock {
        interactiveUiQueue.add(ui)
        _interactiveUi.value = interactiveUiQueue.first()
    }

    private suspend fun removeUi(ui: InteractiveSolveUi) = lock.withLock {
        interactiveUiQueue.remove(ui)
        _interactiveUi.value = interactiveUiQueue.firstOrNull()
    }

    // endregion

    init {
        backgroundScope.launch {
            while (true) {
                delay(SESSION_SWEEP_INTERVAL)
                sweepIdleSessions()
            }
        }
    }

    // region fetchPage

    /**
     * 引擎的唯一页面入口: 直连优先, 按需走浏览器.
     *
     * 内容层面的结果 (含被挡) 以 [PageVerdict] 返回; 真正的网络错误以异常抛出.
     */
    suspend fun <T> fetchPage(url: String, expectation: PageExpectation<T>): PageVerdict<T> {
        val host = normalizedSessionHost(url)

        if (host != null) {
            for (route in searchRoutes) {
                if (route.matches(host)) {
                    route.fetch(url, expectation, client)?.let { return it }
                }
            }
        }

        // 浏览器粘滞: 60s 内 HTTP 刚被挡过且有暖会话, 不再先失败一次
        if (host != null && shouldStickToBrowser(host)) {
            loadInBrowser(host, url, expectation)?.let { verdict ->
                if (verdict is PageVerdict.Blocked && verdict.reason is BlockReason.Captcha) {
                    invalidate(host)
                }
                return verdict
            }
        }

        val page = httpFetch(url)
        val verdict = evaluator.evaluate(page, expectation)
        if (verdict !is PageVerdict.Blocked || verdict.reason !is BlockReason.Captcha || host == null) {
            return verdict
        }

        // 直连被验证码挡住
        val now = getTimeMillis()
        val recentlySolved = lock.withLock {
            val state = hostStateLocked(host)
            state.lastHttpBlockedAtMillis = now
            state.lastSolveSucceededAtMillis > 0 &&
                    now - state.lastSolveSucceededAtMillis < stickyWindow.inWholeMilliseconds
        }

        val browserVerdict = loadInBrowser(host, url, expectation)
        if (browserVerdict != null) {
            if (browserVerdict is PageVerdict.Blocked && browserVerdict.reason is BlockReason.Captcha) {
                // 暖会话也被挡: 会话已失效, 自动丢弃, 下次 solve 从干净状态开始
                invalidate(host)
            }
            return browserVerdict
        }

        if (recentlySolved) {
            // 刚 solve 成功却又被挡, 且无暖会话可验证: cookie 已陈旧, 自动失效
            cookieJar.clearForHost(host)
        }
        return verdict
    }

    private suspend fun shouldStickToBrowser(host: String): Boolean = lock.withLock {
        val state = hostStates[host] ?: return@withLock false
        state.session != null &&
                state.lastHttpBlockedAtMillis > 0 &&
                getTimeMillis() - state.lastHttpBlockedAtMillis < stickyWindow.inWholeMilliseconds
    }

    /**
     * 用暖会话加载页面. 无暖会话时返回 `null` (不创建浏览器).
     */
    private suspend fun <T> loadInBrowser(
        host: String,
        url: String,
        expectation: PageExpectation<T>,
    ): PageVerdict<T>? {
        val session = lock.withLock {
            hostStates[host]?.session?.also {
                it.refCount++
                it.lastUsedAtMillis = getTimeMillis()
            }
        } ?: return null

        try {
            val browser = session.browser
            browser.navigate(url)
            var last: PageVerdict<T>? = null
            val decisive = withTimeoutOrNull(browserLoadTimeout) {
                browserPages(browser, host).first { page ->
                    val v = evaluator.evaluate(page, expectation)
                    last = v
                    // Ok 或非验证码的 Blocked 是决定性判决; 验证码/空白可能只是挑战进行中, 等到超时
                    v is PageVerdict.Ok || (v is PageVerdict.Blocked && v.reason !is BlockReason.Captcha)
                }
            }
            val verdict = last ?: return null
            if (verdict is PageVerdict.Ok) {
                decisive?.let { syncBrowserIdentity(host, browser, it.finalUrl, url) }
            }
            return verdict
        } finally {
            lock.withLock {
                session.refCount--
                session.lastUsedAtMillis = getTimeMillis()
            }
        }
    }

    /**
     * 主 frame 加载事件 + 2s 慢速快照兜底 (应付纯前端路由的站点), 过滤与 [host] 无关的页面.
     */
    private fun browserPages(browser: CaptchaBrowser, host: String) = merge(
        browser.pageLoads,
        flow {
            while (true) {
                delay(SNAPSHOT_POLL_INTERVAL)
                browser.currentPage()?.let { emit(it) }
            }
        },
    ).let { upstream ->
        flow {
            upstream.collect { page ->
                if (isRelevantPage(page, host)) emit(page)
            }
        }
    }

    private fun isRelevantPage(page: LoadedPage, host: String): Boolean {
        if (page.finalUrl.isBlank() ||
            page.finalUrl == "about:blank" ||
            page.finalUrl.startsWith("chrome-error://")
        ) {
            return false
        }
        val pageHost = normalizedSessionHost(page.finalUrl) ?: return false
        return pageHost == host || pageHost.endsWith(".$host") || host.endsWith(".$pageHost")
    }

    private suspend fun httpFetch(url: String): LoadedPage = withContext(ioContext) {
        try {
            client.use {
                prepareGet(url) {
                    accept(ContentType.Text.Html)
                }.execute { response ->
                    response.toLoadedPage()
                }
            }
        } catch (e: ClientRequestException) {
            // expectSuccess 会把 4xx 变成异常; 被挡页面是内容而非错误, 转回 LoadedPage 交给判决
            LoadedPage(
                finalUrl = e.response.request.url.toString(),
                html = runCatching { e.response.bodyAsText() }.getOrDefault(""),
                status = e.response.status.value,
                retryAfter = parseRetryAfter(e.response.headers[HttpHeaders.RetryAfter]),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }

    private suspend fun HttpResponse.toLoadedPage(): LoadedPage {
        var html = bodyAsText()
        if (html.startsWith("\"")) {
            // 非常奇怪, 有的站点会返回一个 JSON 字符串
            html = runCatching {
                Json.parseToJsonElement(html).jsonPrimitive.content
            }.getOrNull() ?: html
        }
        return LoadedPage(
            finalUrl = request.url.toString(),
            html = html,
            status = status.value,
            retryAfter = parseRetryAfter(headers[HttpHeaders.RetryAfter]),
        )
    }

    private fun parseRetryAfter(header: String?): Duration? {
        return header?.trim()?.toLongOrNull()?.takeIf { it > 0 }?.seconds
    }

    // endregion

    // region solve

    /**
     * 解决验证码.
     *
     * - [interactive] = `true`: 必定呈现对话框. 入口不查任何缓存 —— 用户点 "处理验证码"
     *   本身就是 "当前状态不行" 的证明. 同 host 已有进行中的 solve 则 join (single-flight).
     * - [interactive] = `false`: 遍历 [solvers], v1 列表为空 → 立即失败, 不创建浏览器.
     */
    suspend fun solve(request: SolveRequest, interactive: Boolean): SolveOutcome {
        val host = normalizedSessionHost(request.pageUrl)
            ?: return SolveOutcome.Failed(null)
        if (interactive && !isInteractiveSupported) {
            return SolveOutcome.Unsupported
        }

        // single-flight per host
        val (active, isNew) = lock.withLock {
            val state = hostStateLocked(host)
            val existing = state.activeSolve
            if (existing != null) {
                existing to false
            } else {
                ActiveSolve(interactive).also { state.activeSolve = it } to true
            }
        }
        if (!isNew) {
            return active.deferred.await()
        }

        val job = backgroundScope.launch {
            val outcome = try {
                runSolve(host, request, active.interactive)
            } catch (_: CancellationException) {
                SolveOutcome.Cancelled
            } catch (e: Throwable) {
                logger.error(e) { "WebSessionManager: solve failed for $host" }
                SolveOutcome.Failed(null)
            }
            val now = getTimeMillis()
            lock.withLock {
                val state = hostStateLocked(host)
                when (outcome) {
                    SolveOutcome.Solved -> {
                        state.lastSolveSucceededAtMillis = now
                        state.lastSolveFailedAtMillis = 0
                    }

                    is SolveOutcome.Failed -> state.lastSolveFailedAtMillis = now
                    else -> {}
                }
                if (state.activeSolve === active) {
                    state.activeSolve = null
                }
            }
            active.deferred.complete(outcome)
        }
        active.job = job
        return active.deferred.await()
    }

    private suspend fun runSolve(host: String, request: SolveRequest, interactive: Boolean): SolveOutcome {
        if (!interactive) {
            return runAutoSolve(host, request)
        }
        return runInteractiveSolve(host, request)
    }

    private suspend fun runAutoSolve(host: String, request: SolveRequest): SolveOutcome {
        val applicable = solvers.filter { solver ->
            (request.kind.let { BlockReason.Captcha(it) }).let { solver.canAttempt(it, host) }
        }
        if (applicable.isEmpty()) {
            // v1: 无策略, 立即失败, 不创建浏览器
            return SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        }

        // per-host 失败冷却, 防浏览器风暴
        val inCooldown = lock.withLock {
            val state = hostStateLocked(host)
            state.lastSolveFailedAtMillis > 0 &&
                    getTimeMillis() - state.lastSolveFailedAtMillis < solveFailCooldown.inWholeMilliseconds
        }
        if (inCooldown) {
            return SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        }

        var lastFailure: SolveOutcome = SolveOutcome.Failed(BlockReason.Captcha(request.kind))
        for (solver in applicable) {
            var acquired: BrowserSession? = null
            val ctx = SolveContext(
                request = request,
                http = client,
                acquireBrowser = {
                    (acquired ?: acquireSession(host).also { acquired = it }).browser
                },
                evaluate = { page -> evaluator.evaluate(page, request.expectation) },
            )
            try {
                when (val outcome = solver.attempt(ctx)) {
                    SolveOutcome.Solved -> {
                        acquired?.let { syncBrowserIdentity(host, it.browser, request.pageUrl, request.pageUrl) }
                        return SolveOutcome.Solved
                    }

                    else -> lastFailure = outcome
                }
            } finally {
                acquired?.let { releaseSession(it) }
            }
        }
        return lastFailure
    }

    private suspend fun runInteractiveSolve(host: String, request: SolveRequest): SolveOutcome {
        val session = acquireSession(host)
        try {
            val browser = session.browser
            val userDecision = CompletableDeferred<SolveOutcome>()
            val ui = InteractiveSolveUi(
                request = request,
                browser = browser,
                onConfirm = {
                    backgroundScope.launch {
                        // 手动确认: 以当前页面快照 evaluate 的结果为准记录成败, 但都关闭对话框
                        val verdict = browser.currentPage()
                            ?.takeIf { isRelevantPage(it, host) }
                            ?.let { evaluator.evaluate(it, request.expectation) }
                        userDecision.complete(
                            when (verdict) {
                                is PageVerdict.Ok -> SolveOutcome.Solved
                                is PageVerdict.Blocked -> SolveOutcome.Failed(verdict.reason)
                                else -> SolveOutcome.Failed(null)
                            },
                        )
                    }
                },
                onDismiss = {
                    userDecision.complete(SolveOutcome.Cancelled)
                },
                onRefresh = {
                    backgroundScope.launch {
                        val current = browser.currentPage()?.finalUrl
                            ?.takeIf { it.isNotBlank() && it != "about:blank" && !it.startsWith("chrome-error://") }
                        browser.navigate(current ?: request.pageUrl)
                    }
                },
            )
            publishUi(ui)
            try {
                return coroutineScope {
                    val watcher = launch {
                        browserPages(browser, host).collect { page ->
                            if (evaluator.evaluate(page, request.expectation) is PageVerdict.Ok) {
                                userDecision.complete(SolveOutcome.Solved)
                            }
                        }
                    }
                    browser.navigate(request.pageUrl)
                    val outcome = userDecision.await()
                    watcher.cancel()
                    if (outcome == SolveOutcome.Solved) {
                        val finalUrl = browser.currentPage()?.finalUrl ?: request.pageUrl
                        syncBrowserIdentity(host, browser, finalUrl, request.pageUrl)
                    }
                    outcome
                }
            } finally {
                removeUi(ui)
            }
        } finally {
            releaseSession(session)
        }
    }

    /**
     * 只取消进行中的 auto-solve, 不清暖会话、不清 cookie.
     */
    fun cancelAutoSolves() {
        backgroundScope.launch {
            val jobs = lock.withLock {
                hostStates.values.mapNotNull { state ->
                    state.activeSolve?.takeIf { !it.interactive }?.job
                }
            }
            jobs.forEach { it.cancel() }
        }
    }

    /**
     * 丢弃 [host] 的暖会话与相关 cookie, 下次 solve 从干净状态开始.
     */
    suspend fun invalidate(host: String) {
        val normalized = normalizedSessionHost("https://$host") ?: return
        val session = lock.withLock {
            hostStates[normalized]?.let { state ->
                state.lastSolveSucceededAtMillis = 0
                state.session?.also { state.session = null }
            }
        }
        cookieJar.clearForHost(normalized)
        session?.let { closeSession(it) }
    }

    /**
     * 将浏览器的 cookie 与 UA 同步到 HTTP 侧, 保证身份一致.
     */
    private suspend fun syncBrowserIdentity(
        host: String,
        browser: CaptchaBrowser,
        finalUrl: String,
        requestedUrl: String,
    ) {
        val urls = listOfNotNull(
            finalUrl,
            requestedUrl,
            normalizedStorageOrigin(finalUrl),
            normalizedStorageOrigin(requestedUrl),
        ).distinct()
        val cookies = runCatching { browser.collectCookies(urls) }
            .onFailure { logger.error(it) { "WebSessionManager: failed to collect cookies for $host" } }
            .getOrDefault(emptyList())
        if (cookies.isNotEmpty()) {
            cookieJar.addBrowserCookies(finalUrl, cookies)
        }
        identityRegistry.setUserAgent(
            host,
            withContext(Dispatchers.Main) {
                browser.userAgent
            },
        )
        logger.info { "WebSessionManager: synced ${cookies.size} cookies and UA for $host" }
    }

    // endregion

    // region 视频资源嗅探

    /**
     * 在暖会话中提取视频资源. 无暖会话时返回 `null`, 调用方回落到平台默认提取器.
     */
    suspend fun extractVideoResource(
        pageUrl: String,
        timeoutMillis: Long,
        resourceMatcher: (String) -> WebViewVideoExtractor.Instruction,
    ): WebResource? {
        val host = normalizedSessionHost(pageUrl) ?: return null
        val session = lock.withLock {
            hostStates[host]?.session?.also {
                it.refCount++
                it.lastUsedAtMillis = getTimeMillis()
            }
        } ?: return null

        val browser = session.browser
        val deferred = CompletableDeferred<WebResource>()
        val loadedNestedUrls = mutableSetOf(pageUrl)
        val nestedLock = kotlinx.atomicfu.locks.SynchronizedObject()
        try {
            browser.setResourceInterceptor { url ->
                when (resourceMatcher(url)) {
                    WebViewVideoExtractor.Instruction.Continue -> InterceptDecision.Continue
                    WebViewVideoExtractor.Instruction.FoundResource -> {
                        deferred.complete(WebResource(url))
                        InterceptDecision.Block
                    }

                    WebViewVideoExtractor.Instruction.LoadPage -> {
                        val shouldLoad = kotlinx.atomicfu.locks.synchronized(nestedLock) {
                            loadedNestedUrls.add(url)
                        }
                        if (shouldLoad && deferred.isActive) {
                            backgroundScope.launch { browser.navigate(url) }
                        }
                        InterceptDecision.Continue
                    }
                }
            }
            browser.navigate(pageUrl)
            return withTimeoutOrNull(timeoutMillis.milliseconds) { deferred.await() }
        } finally {
            browser.setResourceInterceptor(null)
            lock.withLock {
                session.refCount--
                session.lastUsedAtMillis = getTimeMillis()
            }
        }
    }

    // endregion

    // region 会话生命周期

    private suspend fun acquireSession(host: String): BrowserSession {
        lock.withLock {
            hostStates[host]?.session?.let {
                it.refCount++
                it.lastUsedAtMillis = getTimeMillis()
                return it
            }
        }
        return browserCreateSemaphore.withPermit {
            // double-check: 等待信号量期间可能已有人创建
            lock.withLock {
                hostStates[host]?.session?.let {
                    it.refCount++
                    it.lastUsedAtMillis = getTimeMillis()
                    return it
                }
            }
            val browser = withContext(ioContext) { browserFactory.create() }
            val session = BrowserSession(browser).apply {
                refCount = 1
                lastUsedAtMillis = getTimeMillis()
            }
            var result: BrowserSession = session
            var duplicate: BrowserSession? = null
            var evicted: List<BrowserSession> = emptyList()
            lock.withLock {
                val state = hostStateLocked(host)
                val existing = state.session
                if (existing != null) {
                    // 竞态: 已有人注册了会话, 丢弃我们刚创建的
                    existing.refCount++
                    existing.lastUsedAtMillis = getTimeMillis()
                    result = existing
                    duplicate = session
                } else {
                    state.session = session
                    evicted = evictLruLocked()
                }
            }
            duplicate?.let { closeSession(it) }
            evicted.forEach { closeSession(it) }
            result
        }
    }

    private suspend fun releaseSession(session: BrowserSession) {
        lock.withLock {
            session.refCount--
            session.lastUsedAtMillis = getTimeMillis()
        }
    }

    /**
     * LRU 淘汰: 超出 [maxSessions] 时移除最久未用且未被引用的会话. 必须在 [lock] 内调用.
     */
    private fun evictLruLocked(): List<BrowserSession> {
        val withSessions = hostStates.entries.filter { it.value.session != null }
        if (withSessions.size <= maxSessions) return emptyList()
        val excess = withSessions.size - maxSessions
        return withSessions
            .filter { it.value.session!!.refCount <= 0 && it.value.activeSolve == null }
            .sortedBy { it.value.session!!.lastUsedAtMillis }
            .take(excess)
            .map { entry ->
                entry.value.session!!.also { entry.value.session = null }
            }
    }

    private suspend fun sweepIdleSessions() {
        val now = getTimeMillis()
        val toClose = lock.withLock {
            hostStates.entries.mapNotNull { (_, state) ->
                val session = state.session ?: return@mapNotNull null
                if (session.refCount <= 0 &&
                    state.activeSolve == null &&
                    now - session.lastUsedAtMillis > idleTtl.inWholeMilliseconds
                ) {
                    state.session = null
                    session
                } else null
            }
        }
        toClose.forEach { closeSession(it) }
    }

    private suspend fun closeSession(session: BrowserSession) {
        withContext(ioContext) {
            runCatching { session.browser.close() }
                .onFailure { logger.error(it) { "WebSessionManager: failed to close browser" } }
        }
    }

    // endregion

    private companion object {
        private val logger = logger<WebSessionManager>()

        private const val MAX_CONCURRENT_BROWSER_CREATIONS = 2
        private val SESSION_SWEEP_INTERVAL = 30.seconds
        private val SNAPSHOT_POLL_INTERVAL = 2.seconds
    }
}
