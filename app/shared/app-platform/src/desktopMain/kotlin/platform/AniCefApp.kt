/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import com.jetbrains.cef.JCefAppConfig
import io.ktor.http.Url
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.utils.io.readLastNLines
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.currentPlatformDesktop
import me.him188.ani.utils.platform.currentTimeMillis
import me.him188.ani.utils.platform.isLinux
import me.him188.ani.utils.platform.isMacOS
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefAuthCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.misc.BoolRef
import org.cef.misc.CefLog
import org.cef.network.CefRequest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


object AniCefApp {
    private val logger = logger<AniCefApp>()

    /**
     * 已创建的 [CefApp]. 创建后 CEF 会在自己的线程上异步初始化, 实例存在不代表已经就绪, 见 [initialized].
     */
    @Volatile
    private var app: CefApp? = null

    /**
     * [app] 是否已确认进入 [CefApp.CefAppState.INITIALIZED].
     */
    @Volatile
    private var initialized = false

    /**
     * 启动时等待初始化的上限. 机器严重卡顿 (刚唤醒、内存换页) 时初始化会被拖慢, 这个值要能覆盖那种情况.
     */
    private val STARTUP_INITIALIZATION_TIMEOUT: Duration = 30.seconds

    @Volatile
    var currentAppLogFile: File? = null
        private set

    private val lock = Mutex()
    private const val DEFAULT_DATA_SOURCE_BROWSER_LIMIT = 8
    private val dataSourceBrowserGate = DataSourceBrowserGate(DEFAULT_DATA_SOURCE_BROWSER_LIMIT)
    private val disposedApps = Collections.newSetFromMap(IdentityHashMap<CefApp, Boolean>())

    private var proxyServer: Url? = null
    private var proxyAuthUsername: String? = null
    private var proxyAuthPassword: String? = null

    /**
     * client 唯一的 request handler. JCEF 的 `addRequestHandler` 只在 client 还没有 request handler 时生效,
     * 所以代理认证和调用方的资源请求处理必须合在同一个 handler 里.
     */
    private class AniCefRequestHandler(
        private val resourceRequestHandler: CefResourceRequestHandler?,
    ) : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: CefRequest?,
            isNavigation: Boolean,
            isDownload: Boolean,
            requestInitiator: String?,
            disableDefaultHandling: BoolRef?,
        ): CefResourceRequestHandler? = resourceRequestHandler

        override fun getAuthCredentials(
            browser: CefBrowser?,
            originUrl: String?,
            isProxy: Boolean,
            host: String?,
            port: Int,
            realm: String?,
            scheme: String?,
            callback: CefAuthCallback?,
        ): Boolean {
            if (!isProxy) return false
            if (host != proxyServer?.host) return false
            if (port != proxyServer?.port) return false
            if (scheme != proxyServer?.protocol?.name) return false

            if (callback == null) return false
            callback.Continue(proxyAuthUsername, proxyAuthPassword)
            return true
        }
    }

    /**
     * Create a new [CefApp].
     *
     * Note that you must terminate the last instance before creating new one.
     * Otherwise it will return the existing instance.
     */
    // not thread-safe
    private fun createCefApp(
        logDir: File,
        cacheDir: File,
        proxyServer: String? = null,
    ): CefApp {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd")

        val jcefConfig = JCefAppConfig.getInstance()

        jcefConfig.cefSettings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_DEFAULT
        jcefConfig.cefSettings.log_file = logDir
            .resolve("cef-${dateFormat.format(Date(currentTimeMillis()))}.log")
            .also { currentAppLogFile = it }
            .absolutePath
        jcefConfig.cefSettings.windowless_rendering_enabled = true
        jcefConfig.cefSettings.cache_path = cacheDir.absolutePath

        jcefConfig.appArgsAsList.apply {
            add("--mute-audio")
            add("--force-dark-mode")

            proxyServer?.let { add("--proxy-server=${it}") }
        }

        val platform = currentPlatform()
        if (platform.isMacOS()) {
            // Fix framework paths when packaged
            jcefConfig.appArgsAsList.apply {
                removeAll { it.startsWith("--framework-dir-path") }
                removeAll { it.startsWith("--browser-subprocess-path") }
                removeAll { it.startsWith("--main-bundle-path") }
                findMacOsFrameworkPath()?.let {
                    // See `JCefAppConfig.getInstance` sources for why we use these paths.
                    logger.info { "Found CEF framework at $it" }
                    add("--framework-dir-path=${it.resolve("Chromium Embedded Framework.framework")}")
                    add("--browser-subprocess-path=${it.resolve("jcef Helper.app/Contents/MacOS/jcef Helper")}")
                    add("--main-bundle-path=${it.resolve("jcef Helper.app")}")
                } ?: logger.error { "CEF framework not found" }
            }
        }
        if (platform.isLinux()) {
            jcefConfig.appArgsAsList.apply {
                // Embedded browsers do not save passwords; avoid depending on desktop keyrings such as KWallet.
                add("--password-store=basic")
                // Chromium 137's hardware ANGLE path crashes the JCEF GPU process on Linux.
                // TODO: Remove after upgrading to Chromium 141+ and verifying Intel, AMD, and NVIDIA.
                add("--use-gl=angle")
                add("--use-angle=swiftshader-webgl")

                // Chromium's default display backend crashes the CEF browser process (SIGTRAP)
                // on Wayland sessions before the GPU process is usable, so JCEF never reaches
                // the INITIALIZED state. Force the X11 (XWayland) backend, which is stable.
                add("--ozone-platform=x11")

                // will cause 139 (segfault)
                // add("--disable-gpu")
                // add("--disable-software-rasterizer")

                // add("--no-sandbox") // will cause 139 (segfault)
            }
        }
        jcefConfig.appArgsAsList.add(
            "--autoplay-policy=no-user-gesture-required",
        )

        CefLog.init(jcefConfig.cefSettings.log_file, jcefConfig.cefSettings.log_severity)
        CefApp.startup(jcefConfig.appArgs)
        return CefApp.getInstance(jcefConfig.appArgs, jcefConfig.cefSettings)
    }

    /**
     * Returns `$JAVA_HOME/../Frameworks`
     */
    private fun findMacOsFrameworkPath(): File? {
        /*
        Absolute path: /Users/him188/Projects/ani/app/desktop/build/compose/binaries/main-release/app/Ani.app/Contents
        user.dir/Users/him188/Projects/ani/app/desktop/build/compose/binaries/main-release/app/Ani.app/Contents
        Java home: /Users/him188/Projects/ani/app/desktop/build/compose/binaries/main-release/app/Ani.app/Contents/runtime/Contents/Home
         */
        logger.info { "Absolute path: " + File(".").normalize().absolutePath }
        logger.info { "user.dir: " + File(System.getProperty("user.dir")).normalize().absolutePath }
        logger.info { "Java home: " + System.getProperty("java.home") }

        val javaHome = File(System.getProperty("java.home"))
        sequence {
            yield(javaHome.resolve("../Frameworks/Chromium Embedded Framework.framework"))
            yield(javaHome.resolve("Frameworks/Chromium Embedded Framework.framework"))
            yield(javaHome.resolve("lib/Frameworks/Chromium Embedded Framework.framework"))
        }.forEach {
            if (it.exists()) {
                return it.parentFile.normalize()
            }
        }

        return null
    }

    /**
     * Initialize singleton instance of [CefApp].
     *
     * 超时只抛出 [JCEFInitializationException] 通知启动流程, 不会销毁实例:
     * 同一个 JVM 里 CEF 一旦 dispose 就无法再创建 (`CefApp.getInstance` 会抛 "CefApp was terminated"),
     * 而初始化在 CEF 自己的线程上仍会继续. 之后调用 [awaitInitialized] 可以接着等同一个实例就绪.
     */
    suspend fun initialize(
        logDir: File,
        cacheDir: File,
        proxyServer: String? = null,
        proxyAuthUsername: String? = null,
        proxyAuthPassword: String? = null,
    ) {
        if (initialized) return

        val cefApp = lock.withLock {
            app?.let { return@withLock it }

            val finalCacheDir = if (checkLockFile(cacheDir)) {
                cacheDir
            } else {
                val newTempCacheDir = cacheDir.nameWithoutExtension + "-${currentTimeMillis()}"
                logger.warn { "Failed to resolve JCEF lock file, switch to temporary dir $newTempCacheDir for this instance." }
                cacheDir.parentFile.resolve(newTempCacheDir)
            }

            val newApp = createCefApp(logDir, finalCacheDir, proxyServer)
            this.proxyServer = proxyServer?.let(::Url)
            this.proxyAuthUsername = proxyAuthUsername
            this.proxyAuthPassword = proxyAuthPassword

            Runtime.getRuntime().addShutdownHook(
                thread(start = false) {
                    disposeAppBlocking(newApp)
                },
            )

            app = newApp
            newApp
        }

        if (!awaitInitialized(cefApp, STARTUP_INITIALIZATION_TIMEOUT)) {
            throw JCEFInitializationException(
                "Failed to initialize JCEF within $STARTUP_INITIALIZATION_TIMEOUT, state: ${CefApp.getState()}. " +
                        "Will keep waiting for the same instance when a browser is requested. " +
                        "Last cef logs: \n${getLatestCefLog().joinToString("\n")}",
            )
        }
    }

    /**
     * 等待 JCEF 完成初始化, 最多等 [timeout].
     *
     * 返回 `false` 表示仍未就绪: 从未调用过 [initialize], 实例已终止, 或者这次又超时了.
     * 启动时超时过的实例可以在这里接着等, 见 [initialize].
     */
    suspend fun awaitInitialized(timeout: Duration): Boolean {
        if (initialized) return true
        val cefApp = app ?: return false
        return awaitInitialized(cefApp, timeout)
    }

    private suspend fun awaitInitialized(cefApp: CefApp, timeout: Duration): Boolean {
        if (initialized) return true
        if (cefApp.isTerminated) {
            logger.warn { "JCEF instance is terminated, cannot wait for initialization." }
            return false
        }
        logger.info { "Awaiting JCEF initialization, timeout: $timeout." }
        val result = withTimeoutOrNull(timeout) {
            suspendCancellableCoroutine<Unit> { cont ->
                // 已初始化的实例会立即回调, 所以重复等待是安全的.
                cefApp.onInitialization { state ->
                    if (state == CefApp.CefAppState.INITIALIZED && cont.isActive) {
                        cont.resume(Unit)
                    }
                }
            }
        }
        if (result == null) {
            logger.warn { "JCEF did not initialize within $timeout, state: ${CefApp.getState()}." }
            return false
        }
        initialized = true
        logger.info { "JCEF is initialized." }
        return true
    }

    /**
     * 解决上一次启动时可能遗留的 lockfile 问题
     *
     * 返回 `true` 表示没有问题, 可以使用当前的 [cacheDir] 启动 JCEF.
     *
     * @return `false` if lock file cannot be deleted.
     */
    private fun checkLockFile(cacheDir: File): Boolean {
        if (!cacheDir.exists()) return true

        return when (currentPlatformDesktop()) {
            // windows 只删除 lockfile
            is Platform.Windows -> {
                val lockFile = cacheDir.resolve("lockfile")
                if (!lockFile.exists() || !lockFile.isFile) return true

                try {
                    Files.delete(lockFile.toPath())
                    true
                } catch (e: IOException) {
                    // 删不掉说明之前 Ani 退出后 jcef helper 还在后台运行.
                    logger.error(e) { "Lock file exists and cannot be deleted while initializing JCEF." }
                    false
                }
            }

            is Platform.MacOS,
            is Platform.Linux -> {
                return cacheDir.deleteRecursively()
            }
        }
    }

    /**
     * Create a new CEF client.
     *
     * You should dispose it if drop.
     *
     * 调用前应先用 [awaitInitialized] 确认就绪, 否则创建的浏览器会一直等到初始化完成才开始加载.
     *
     * @param resourceRequestHandler 处理这个 client 下所有浏览器的资源请求 (拦截、改请求头).
     * 不要再对返回的 client 调用 `addRequestHandler`: client 已有处理代理认证的 request handler, 后加的会被 JCEF 静默忽略.
     * @return `null` if [initialize] has never been called.
     */
    fun createClient(resourceRequestHandler: CefResourceRequestHandler? = null): CefClient? {
        return app?.createClient()
            ?.apply { addRequestHandler(AniCefRequestHandler(resourceRequestHandler)) }
    }

    fun configureDataSourceBrowserLimit(limit: Int) {
        dataSourceBrowserGate.configureLimit(limit)
    }

    suspend fun acquireDataSourceBrowserPermit(): BrowserLifecyclePermit {
        return dataSourceBrowserGate.acquire()
    }

    class BrowserLifecyclePermit internal constructor(
        private val releasePermit: () -> Unit,
    ) {
        private val released = AtomicBoolean(false)

        fun release() {
            if (released.compareAndSet(false, true)) {
                releasePermit()
            }
        }
    }

    suspend fun closeBrowserAndDisposeClient(
        browser: CefBrowser?,
        client: CefClient?,
    ) {
        suspendCoroutineOnCefContext {
            closeBrowserAndDisposeClientNow(browser, client)
        }
    }

    fun closeBrowserAndDisposeClientBlocking(
        browser: CefBrowser?,
        client: CefClient?,
    ) {
        blockOnCefContext {
            closeBrowserAndDisposeClientNow(browser, client)
        }
    }

    fun disposeBlocking() {
        val currentApp = app ?: return
        app = null
        disposeAppBlocking(currentApp)
    }

    private fun disposeAppBlocking(target: CefApp) {
        val shouldDispose = synchronized(disposedApps) {
            disposedApps.add(target)
        }
        if (!shouldDispose) return

        runCatching {
            if (SwingUtilities.isEventDispatchThread()) {
                target.dispose()
            } else {
                // 此方法会在 JVM shutdown hook 中调用. 若这次 shutdown 是 EDT 调用 System.exit 触发的,
                // EDT 正阻塞等待 hook 结束, invokeAndWait 无限等待会互相死锁, 进程永远无法退出,
                // 因此只能有界等待.
                val latch = CountDownLatch(1)
                SwingUtilities.invokeLater {
                    try {
                        target.dispose()
                    } finally {
                        latch.countDown()
                    }
                }
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    logger.warn { "Timed out waiting for JCEF disposal on EDT, proceeding without it." }
                }
            }
        }.onFailure {
            logger.warn(it) { "Failed to dispose JCEF." }
        }
    }

    private fun closeBrowserAndDisposeClientNow(
        browser: CefBrowser?,
        client: CefClient?,
    ) {
        runCatching {
            browser?.close(true)
        }.onFailure {
            logger.warn(it) { "Failed to close CEF browser." }
        }
        runCatching {
            client?.dispose()
        }.onFailure {
            logger.warn(it) { "Failed to dispose CEF client." }
        }
    }

    /**
     * You should always call cef methods in Cef context.
     */
    fun runOnCefContext(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }

    /**
     * You should always call cef methods in Cef context.
     */
    fun blockOnCefContext(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeAndWait(block)
        }
    }

    /**
     * Run in Cef context and get result.
     */
    suspend fun <T> suspendCoroutineOnCefContext(block: () -> T): T {
        return suspendCancellableCoroutine {
            runOnCefContext {
                it.resumeWith(runCatching(block))
            }
        }
    }

    fun getLatestCefLog(nLine: Int = 30): List<String> {
        val file = currentAppLogFile ?: return emptyList()
        return file.readLastNLines(nLine)
    }
}

private class JCEFInitializationException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

private class DataSourceBrowserGate(initialLimit: Int) {
    private val lock = ReentrantLock()
    private val waiters = ArrayDeque<CompletableDeferred<Unit>>()
    private var limit = initialLimit.coerceAtLeast(1)
    private var acquired = 0

    suspend fun acquire(): AniCefApp.BrowserLifecyclePermit {
        while (true) {
            val waiter = lockAndGetWaiter()
            if (waiter == null) {
                return AniCefApp.BrowserLifecyclePermit(::release)
            }

            try {
                waiter.await()
                return AniCefApp.BrowserLifecyclePermit(::release)
            } catch (e: Throwable) {
                val wakeups = cancelWaiter(waiter)
                wakeups.forEach { it.complete(Unit) }
                throw e
            }
        }
    }

    fun configureLimit(newLimit: Int) {
        val wakeups = lockAndConfigureLimit(newLimit.coerceAtLeast(1))
        wakeups.forEach { it.complete(Unit) }
    }

    private fun release() {
        val wakeups = lockAndRelease()
        wakeups.forEach { it.complete(Unit) }
    }

    private fun lockAndGetWaiter(): CompletableDeferred<Unit>? {
        lock.lock()
        try {
            if (acquired < limit) {
                acquired++
                return null
            }
            return CompletableDeferred<Unit>().also(waiters::addLast)
        } finally {
            lock.unlock()
        }
    }

    private fun cancelWaiter(waiter: CompletableDeferred<Unit>): List<CompletableDeferred<Unit>> {
        lock.lock()
        try {
            if (waiters.remove(waiter)) {
                return emptyList()
            }
            acquired--
            return collectWakeupsLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun lockAndConfigureLimit(newLimit: Int): List<CompletableDeferred<Unit>> {
        lock.lock()
        try {
            limit = newLimit
            return collectWakeupsLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun lockAndRelease(): List<CompletableDeferred<Unit>> {
        lock.lock()
        try {
            check(acquired > 0) { "Browser lifecycle permit released without being acquired" }
            acquired--
            return collectWakeupsLocked()
        } finally {
            lock.unlock()
        }
    }

    private fun collectWakeupsLocked(): List<CompletableDeferred<Unit>> {
        val wakeups = mutableListOf<CompletableDeferred<Unit>>()
        while (acquired < limit && waiters.isNotEmpty()) {
            acquired++
            wakeups += waiters.removeFirst()
        }
        return wakeups
    }
}
