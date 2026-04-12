/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.window

import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.utils.logging.logger
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.interfaces.DBusInterface
import org.freedesktop.dbus.types.UInt32
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class LinuxWindowUtils : AwtWindowUtils() {
    private val lock = ReentrantLock()

    @Volatile private var closed = false

    /**
     * Inhibitors ordered by priority. The first one that succeeds wins.
     */
    private val inhibitors: List<ScreenSaverInhibitor> = listOf(
        DbusScreenSaverInhibitor(),
        SystemdInhibitScreenSaverInhibitor(),
    )

    companion object {
        private val logger = logger<LinuxWindowUtils>()
        private val instances = mutableListOf<WeakReference<LinuxWindowUtils>>()

        private val hasSystemdInhibit by lazy { cmdExists("systemd-inhibit") }
        private val hasSleep by lazy { cmdExists("sleep") }

        private fun cmdExists(cmd: String): Boolean = runCatching {
            if (cmd.contains(File.separatorChar)) {
                if (cmd.contains("..")) return false
                val f = File(cmd)
                f.isFile && f.canExecute()
            } else {
                System.getenv("PATH")?.split(File.pathSeparator)
                    ?.any { dir -> File(dir, cmd).let { it.isFile && it.canExecute() } } ?: false
            }
        }.getOrDefault(false)

        init {
            Runtime.getRuntime().addShutdownHook(Thread {
                synchronized(instances) {
                    instances.removeAll { it.get() == null }
                    instances.mapNotNull { it.get() }
                }.forEach { inst ->
                    try {
                        if (inst.lock.tryLock()) {
                            try { if (!inst.closed) inst.inhibitors.forEach { it.cleanup() } }
                            finally { inst.lock.unlock() }
                        } else {
                            logger.warn("[ScreenSaver] Shutdown: lock unavailable; skipping cleanup")
                        }
                    } catch (e: Exception) {
                        logger.warn("[ScreenSaver] Shutdown error", e)
                    }
                }
            })
        }
    }

    init {
        synchronized(instances) {
            instances.removeAll { it.get() == null }
            instances.add(WeakReference(this))
        }
    }

    override suspend fun setUndecoratedFullscreen(
        window: PlatformWindow,
        windowState: WindowState,
        undecorated: Boolean
    ) {
        windowState.placement = if (undecorated) WindowPlacement.Fullscreen else WindowPlacement.Floating
    }

    override fun setPreventScreenSaver(prevent: Boolean) = lock.withLock {
        if (closed) return@withLock

        if (prevent) {
            var anySuccess = false
            for (inhibitor in inhibitors) {
                if (inhibitor.isActive) {
                    anySuccess = true
                } else if (inhibitor.tryInhibit()) {
                    logger.info("[ScreenSaver] Inhibited via ${inhibitor.name}")
                    anySuccess = true
                }
            }
            if (!anySuccess) {
                logger.warn("[ScreenSaver] All inhibit methods failed")
            }
        } else {
            inhibitors.forEach { it.cleanup() }
        }
    }

    fun close() = lock.withLock {
        if (closed) return@withLock
        closed = true
        synchronized(instances) {
            instances.removeAll { ref ->
                val instance = ref.get()
                instance == null || instance === this
            }
        }
        inhibitors.forEach { it.cleanup() }
    }

    // ---- Inhibitor abstraction ----

    /**
     * Strategy for inhibiting the screen saver.
     * Implementations must be safe to call from within [lock].
     */
    private interface ScreenSaverInhibitor {
        val name: String
        val isActive: Boolean
        fun tryInhibit(): Boolean
        fun cleanup()
    }

    // ---- D-Bus org.freedesktop.ScreenSaver implementation ----

    @Suppress("FunctionName")
    @org.freedesktop.dbus.annotations.DBusInterfaceName("org.freedesktop.ScreenSaver")
    private interface ScreenSaverInterface : DBusInterface {
        fun Inhibit(applicationName: String, reason: String): UInt32
        fun UnInhibit(cookie: UInt32)
    }

    private inner class DbusScreenSaverInhibitor : ScreenSaverInhibitor {
        override val name = "D-Bus org.freedesktop.ScreenSaver"

        @Volatile
        private var inhibitCookie: UInt32? = null

        @Volatile
        private var connection: DBusConnection? = null

        override val isActive: Boolean get() = inhibitCookie != null

        override fun tryInhibit(): Boolean {
            return try {
                val conn = withDbusClassLoader {
                    DBusConnectionBuilder.forSessionBus().build()
                }
                val screenSaver = conn.getRemoteObject(
                    "org.freedesktop.ScreenSaver",
                    "/org/freedesktop/ScreenSaver",
                    ScreenSaverInterface::class.java,
                )
                val cookie = screenSaver.Inhibit("Animeko", "Playing video")
                connection = conn
                inhibitCookie = cookie
                logger.info("[ScreenSaver] D-Bus inhibit cookie: $cookie")
                true
            } catch (e: Exception) {
                logger.debug("[ScreenSaver] D-Bus inhibit failed", e)
                false
            }
        }

        override fun cleanup() {
            val cookie = inhibitCookie
            val conn = connection
            if (cookie != null && conn != null) {
                logger.info("[ScreenSaver] Uninhibiting D-Bus with cookie: $cookie")
                try {
                    val screenSaver = conn.getRemoteObject(
                        "org.freedesktop.ScreenSaver",
                        "/org/freedesktop/ScreenSaver",
                        ScreenSaverInterface::class.java,
                    )
                    screenSaver.UnInhibit(cookie)
                } catch (e: Exception) {
                    logger.error("[ScreenSaver] Failed to uninhibit D-Bus screen saver", e)
                } finally {
                    try { conn.close() } catch (_: Exception) {}
                    connection = null
                    inhibitCookie = null
                }
            }
        }

        /**
         * ServiceLoader uses the thread context classloader to discover providers.
         * On the AWT EventDispatch thread, this may differ from the classloader that
         * loaded dbus-java, causing transport provider discovery to fail.
         * Temporarily switch to the classloader that loaded [DBusConnectionBuilder].
         */
        private inline fun <T> withDbusClassLoader(block: () -> T): T {
            val currentThread = Thread.currentThread()
            val originalClassLoader = currentThread.contextClassLoader
            currentThread.contextClassLoader = DBusConnectionBuilder::class.java.classLoader
            try {
                return block()
            } finally {
                currentThread.contextClassLoader = originalClassLoader
            }
        }
    }

    // ---- systemd-inhibit subprocess fallback ----

    private inner class SystemdInhibitScreenSaverInhibitor : ScreenSaverInhibitor {
        override val name = "systemd-inhibit"

        @Volatile
        private var process: Process? = null

        override val isActive: Boolean get() = process?.isAlive == true

        override fun tryInhibit(): Boolean {
            if (!hasSystemdInhibit || !hasSleep) return false

            // Kill any stale process
            process?.let { p ->
                try { p.outputStream.close() } catch (_: Exception) {}
                p.destroy()
                try {
                    if (!p.waitFor(200, TimeUnit.MILLISECONDS)) {
                        p.destroyForcibly()
                    }
                } catch (_: Exception) {}
            }
            process = null

            return runCatching {
                val p = ProcessBuilder(
                    "systemd-inhibit", "--what=sleep:idle", "--who=Animeko",
                    "--why=Playing video", "--mode=block", "sleep", "infinity",
                ).redirectErrorStream(true).start()

                // Drain output async
                Thread {
                    try {
                        try {
                            p.inputStream.copyTo(java.io.OutputStream.nullOutputStream())
                        } catch (_: Exception) {}
                    } finally {
                        try { p.inputStream.close() } catch (_: Exception) {}
                    }
                }.apply { isDaemon = true; name = "ScreenSaver-drain"; start() }

                // Check if exits immediately
                val exitedImmediately = try {
                    p.waitFor(500, TimeUnit.MILLISECONDS)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    logger.warn("[ScreenSaver] Interrupted while starting systemd-inhibit", ie)
                    try {
                        p.destroyForcibly()
                        p.waitFor(2, TimeUnit.SECONDS)
                    } catch (_: Exception) {}
                    return false
                }

                if (exitedImmediately) {
                    logger.warn("[ScreenSaver] systemd-inhibit exited: ${p.exitValue()}")
                    try {
                        p.destroyForcibly()
                        p.waitFor(2, TimeUnit.SECONDS)
                    } catch (_: Exception) {}
                    false
                } else {
                    process = p
                    true
                }
            }.onFailure { logger.debug("[ScreenSaver] systemd failed", it) }.getOrDefault(false)
        }

        override fun cleanup() {
            process?.let { p ->
                try {
                    try { p.outputStream.close() } catch (_: Exception) {}

                    p.destroy()
                    val exited = try {
                        p.waitFor(300, TimeUnit.MILLISECONDS)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        logger.warn("[ScreenSaver] Interrupted while waiting for systemd-inhibit to stop", ie)
                        false
                    }

                    if (!exited) {
                        p.destroyForcibly()
                        try {
                            if (!p.waitFor(3, TimeUnit.SECONDS)) {
                                logger.warn("[ScreenSaver] systemd-inhibit won't die")
                            }
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            logger.warn("[ScreenSaver] Interrupted while waiting for forcible stop", ie)
                        }
                    }
                } finally {
                    process = null
                }
            }
        }
    }
}
