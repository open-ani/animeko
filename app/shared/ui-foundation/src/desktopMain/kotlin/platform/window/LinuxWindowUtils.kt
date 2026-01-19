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
    
    @Volatile private var systemdProcess: Process? = null
    @Volatile private var closed = false

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
            val systemdOk = systemdProcess?.isAlive == true || trySystemdInhibitLocked()
            
            if (systemdOk) {
                logger.info("[ScreenSaver] Inhibited via systemd-inhibit")
            } else {
                logger.warn("[ScreenSaver] systemd-inhibit failed")
            }
        } else {
            cleanupLocked()
        }
    }

    private fun trySystemdInhibitLocked(): Boolean {
        if (!hasSystemdInhibit || !hasSleep) return false
        
        systemdProcess?.let { p ->
            try { p.outputStream.close() } catch (_: Exception) {}
            p.destroy()
            try {
                if (!p.waitFor(200, TimeUnit.MILLISECONDS)) {
                    p.destroyForcibly()
                }
            } catch (_: Exception) {}
        }
        systemdProcess = null
        
        return runCatching {
            val p = ProcessBuilder(
                "systemd-inhibit", "--what=sleep:idle", "--who=AniVideoPlayer",
                "--why=Playing video", "--mode=block", "sleep", "infinity"
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

    private fun cleanupLocked() {
        systemdProcess?.let { p ->
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
                systemdProcess = null
            }
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
}