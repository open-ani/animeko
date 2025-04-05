/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import com.jetbrains.cef.JCefAppConfig
import io.ktor.http.Url
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
import org.cef.callback.CefAuthCallback
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.misc.CefLog
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.seconds


object AniCefApp {
    private val logger = logger<AniCefApp>()

    @Volatile
    private var app: CefApp? = null

    @Volatile
    var currentAppLogFile: File? = null
        private set

    private val lock = Mutex()

    private var proxyServer: Url? = null
    private var proxyAuthUsername: String? = null
    private var proxyAuthPassword: String? = null

    private val proxiedRequestHandler = object : CefRequestHandlerAdapter() {
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
                // will cause 139 (segfault)
                // add("--disable-gpu")
                // add("--disable-software-rasterizer")

                // add("--no-sandbox") // will cause 139 (segfault)
            }
        }
        jcefConfig.appArgsAsList.add(
            "--autoplay-policy=no-user-gesture-required",
        )

        CefLog.init(jcefConfig.cefSettings)
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
     * You can call [getInstance] later to get it.
     */
    suspend fun initialize(
        logDir: File,
        cacheDir: File,
        proxyServer: String? = null,
        proxyAuthUsername: String? = null,
        proxyAuthPassword: String? = null,
    ) {
        val currentApp = app
        if (currentApp != null) return

        val cefApp = lock.withLock {
            val currentApp2 = app
            if (currentApp2 != null) return

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
                    runOnCefContext { newApp.dispose() }
                },
            )

            app = newApp
            newApp
        }

        withTimeoutOrNull(8.seconds) {
            logger.info { "Awaiting JCEF initialization." }
            suspendCancellableCoroutine<Unit> { cont ->
                cefApp.onInitialization { state ->
                    if (state == CefApp.CefAppState.INITIALIZED) {
                        logger.info { "JCEF is initialized." }
                        cont.resume(Unit)
                    }
                }
            }
        }.also { result ->
            if (result == null) {
                // 长时间没加载好 JCEF, 可能是 CEF 内部出错了, 直接抛出异常并附带最新的 CEF 日志.
                throw JCEFInitializationException(
                    "Failed to initialize JCEF, state: ${CefApp.getState()}, " +
                            "last cef logs: \n${getLatestCefLog().joinToString("\n")}",
                )
            }
        }
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
     * @return `null` if CefApp hasn't initialized yet.
     */
    fun createClient(): CefClient? {
        return app?.createClient()
            ?.apply { addRequestHandler(proxiedRequestHandler) }
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