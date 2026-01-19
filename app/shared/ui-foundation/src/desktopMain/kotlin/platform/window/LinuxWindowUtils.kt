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
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.TimeUnit

class LinuxWindowUtils : AwtWindowUtils() {
    private val lock = ReentrantLock()
    
    @Volatile private var dbusCookie: UInt? = null
    @Volatile private var systemdProcess: Process? = null
    @Volatile private var closed = false

    companion object {
        private val logger = logger<LinuxWindowUtils>()
        private val instances = mutableListOf<WeakReference<LinuxWindowUtils>>()
        
        init {
            Runtime.getRuntime().addShutdownHook(Thread {
                synchronized(instances) {
                    instances.removeAll { it.get() == null }
                    instances.mapNotNull { it.get() }
                }.forEach { inst ->
                    try {
                        if (inst.lock.tryLock(2, TimeUnit.SECONDS)) {
                            try { if (!inst.closed) inst.cleanupLocked() }
                            finally { inst.lock.unlock() }
                        } else {
                            logger.warn("[ScreenSaver] Shutdown: lock timeout")
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        logger.warn("[ScreenSaver] Shutdown interrupted", e)
                    } catch (e: Exception) {
                        logger.warn("[ScreenSaver] Shutdown error", e)
                    }
                }
            })
        }
    }

    init {
        synchronized(instances) { instances.add(WeakReference(this)) }
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
            val systemdOk = systemdProcess?.isAlive ?: false || trySystemdInhibitLocked()
            val dbusOk = dbusCookie != null || tryDbusInhibitLocked()
            
            when {
                systemdOk && dbusOk -> logger.info("[ScreenSaver] Inhibited (systemd + dbus)")
                systemdOk || dbusOk -> logger.info("[ScreenSaver] Partial (systemd=$systemdOk, dbus=$dbusOk)")
                else -> logger.warn("[ScreenSaver] All methods failed")
            }
        } else {
            cleanupLocked()
        }
    }

    private fun trySystemdInhibitLocked(): Boolean {
        if (!cmdExists("systemd-inhibit") || !cmdExists("tail")) return false
        
        systemdProcess?.destroy()
        systemdProcess = null
        
        return runCatching {
            val p = ProcessBuilder(
                "systemd-inhibit", "--what=sleep:idle", "--who=AniVideoPlayer",
                "--why=Playing video", "--mode=block", "tail", "-f", "/dev/null"
            ).redirectErrorStream(true).start()
            
            // Drain output async
            Thread {
                try {
                    try {
                        p.inputStream.copyTo(java.io.OutputStream.nullOutputStream())
                    } catch (_: Exception) {
                        // ignore copy errors
                    }
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
                systemdProcess = p
                true
            }
        }.onFailure { logger.debug("[ScreenSaver] systemd failed", it) }.getOrDefault(false)
    }

    private fun tryDbusInhibitLocked(): Boolean {
        if (!cmdExists("dbus-send")) return false
        
        return runCatching {
            val p = ProcessBuilder(
                "dbus-send", "--session", "--print-reply",
                "--dest=org.freedesktop.ScreenSaver", "/org/freedesktop/ScreenSaver",
                "org.freedesktop.ScreenSaver.Inhibit", "string:AniVideoPlayer", "string:Playing video"
            ).redirectErrorStream(true).start()
            
            val finished = try {
                p.waitFor(5, TimeUnit.SECONDS)
            } catch (ie: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("[ScreenSaver] Interrupted while waiting for dbus-send", ie)
                try { p.destroyForcibly(); p.waitFor(2, TimeUnit.SECONDS) } catch (_: Exception) {}
                return false
            }

            if (!finished) {
                try { p.destroyForcibly(); p.waitFor(2, TimeUnit.SECONDS) } catch (_: Exception) {}
                logger.warn("[ScreenSaver] dbus-send timed out")
                return false
            }
            
            val out = try { p.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
            val cookie = Regex("""\buint32\s+(\d+)""").find(out)?.groups?.get(1)?.value?.toUIntOrNull()
            
            if (cookie != null) {
                dbusCookie = cookie
                true
            } else {
                logger.debug("[ScreenSaver] No cookie in: $out")
                false
            }
        }.onFailure { logger.debug("[ScreenSaver] dbus failed", it) }.getOrDefault(false)
    }

    private fun cleanupLocked() {
        systemdProcess?.let { p ->
            try {
                p.destroy()
                val exited = try {
                    p.waitFor(300, TimeUnit.MILLISECONDS)
                } catch (ie: InterruptedException) {
                    Thread.currentThread().interrupt()
                    logger.warn("[ScreenSaver] Interrupted while waiting for systemd-inhibit to stop", ie)
                    false
                }

                if (!exited) {
                    try {
                        p.destroyForcibly()
                        val forcibleExited = try {
                            p.waitFor(3, TimeUnit.SECONDS)
                        } catch (ie: InterruptedException) {
                            Thread.currentThread().interrupt()
                            logger.warn("[ScreenSaver] Interrupted while waiting for forcible stop", ie)
                            false
                        }
                        if (!forcibleExited) {
                            logger.warn("[ScreenSaver] systemd-inhibit won't die")
                        }
                    } catch (e: Exception) {
                        logger.warn("[ScreenSaver] Error forcing systemd-inhibit stop", e)
                    }
                }
            } finally {
                systemdProcess = null
            }
        }
        
        dbusCookie?.let { cookie ->
            runCatching {
                ProcessBuilder(
                    "dbus-send", "--session", "--type=method_call", "--dest=org.freedesktop.ScreenSaver",
                    "/org/freedesktop/ScreenSaver", "org.freedesktop.ScreenSaver.UnInhibit", "uint32:$cookie"
                ).redirectErrorStream(true).start().waitFor(5, TimeUnit.SECONDS)
            }.onFailure { logger.debug("[ScreenSaver] dbus uninhibit failed", it) }
            dbusCookie = null
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
        cleanupLocked()
    }

    private fun cmdExists(cmd: String): Boolean = runCatching {
        if (cmd.contains(File.separatorChar)) {
            if (cmd.contains("..")) return false
            val f = File(cmd)
            // Simpler check: allow symlinks; just require file exists and is executable.
            f.exists() && f.canExecute()
        } else {
            System.getenv("PATH")?.split(File.pathSeparator)
                ?.any { File(it, cmd).canExecute() } ?: false
        }
    }.getOrDefault(false)
}