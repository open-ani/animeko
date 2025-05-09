/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinError
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser.SW_RESTORE
import com.sun.jna.platform.win32.WinUser.SW_SHOW
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop
import kotlin.system.exitProcess

sealed interface SingleInstanceChecker {
    fun ensureSingleInstance()

    companion object {
        val instance by lazy {
            when (currentPlatformDesktop()) {
                is Platform.Windows -> WindowsSingleInstanceChecker
                else -> NoOpSingleInstanceChecker
            }
        }
    }
}

data object NoOpSingleInstanceChecker : SingleInstanceChecker {
    override fun ensureSingleInstance() {
    }
}

private const val WINDOW_NAME = "Ani"

data object WindowsSingleInstanceChecker : SingleInstanceChecker {
    private val logger = logger<WindowsSingleInstanceChecker>()

    private var hMutex: WinNT.HANDLE? = null
    private const val MUTEX_NAME = "AniAppSingleInstanceMutex"

    override fun ensureSingleInstance() {
        try {
            val kernel32 = Kernel32.INSTANCE

            // Create a named mutex
            hMutex = kernel32.CreateMutex(null, true, MUTEX_NAME)

            val lastError = kernel32.GetLastError()
            if (hMutex == null || hMutex == WinBase.INVALID_HANDLE_VALUE) {
                error("Failed to create mutex. Error: $lastError")
            } else if (lastError == WinError.ERROR_ALREADY_EXISTS) {
                // Mutex already exists, another instance is running
                logger.warn("Another instance is already running.")
                kernel32.CloseHandle(hMutex)
                kotlin.runCatching { showWindowByName(WINDOW_NAME) }
                    .onFailure {
                        logger.error(it) { "Failed to bring window to front" }
                    }
                exitProcess(1)
            }

            // Add shutdown hook to release the mutex
            Runtime.getRuntime().addShutdownHook(
                Thread {
                    kernel32.ReleaseMutex(hMutex)
                    kernel32.CloseHandle(hMutex)
                },
            )

            logger.info("Application is running with mutex to prevent double instances: $MUTEX_NAME")
        } catch (e: Throwable) {
            logger.error(e) { "Failed to check single instance, ignoring and continuing" }
        }
    }

    private fun showWindowByName(windowTitle: String) {
        val user32 = User32.INSTANCE

        // Find the window by its title
        val hWnd: WinDef.HWND? = user32.FindWindow(null, windowTitle)

        if (hWnd == null) {
            logger.warn("Window with title '$windowTitle' not found.")
            return
        }

        user32.ShowWindow(hWnd, SW_RESTORE)
        user32.ShowWindow(hWnd, SW_SHOW)
        // Try to bring the window to the foreground
        user32.SetForegroundWindow(hWnd)
    }
}


//object SingleInstanceChecker {
//    private val logger = logger<SingleInstanceChecker>()
//
//    @JvmStatic
//    fun ensureSingleInstance(lockFile: File) {
//        try {
//            // Create a new lock file and acquire lock
//            if (lockFile.exists()) {
//                val pid = lockFile.readText().trim()
//                if (isProcessRunning(pid)) {
//                    logger.info("Another instance of the application is already running.")
//                    exitProcess(1)
//                } else {
//                    logger.warn("Found stale lock file (PID=$pid). Removing it...")
//                    lockFile.delete()
//                }
//            }
//
//            // Write the current process PID to the lock file
//            val processID = getProcessID()
//            raf.writeUTF(processID)
//
//            // Ensure lock is released when the app shuts down
//            Runtime.getRuntime().addShutdownHook(
//                Thread {
//                    kotlin.runCatching {
//                        lockFile.delete()
//                    }.onFailure {
//                        logger.error(it) { "Failed to release lock" }
//                    }
//                },
//            )
//
//            logger.info { "Application is running with PID: ${getProcessID()}" }
//        } catch (e: Throwable) {
//            logger.error(e) { "Failed to check single instance, ignoring and continuing" }
//        }
//    }
//
//    // Method to get the current process ID (PID)
//    private fun getProcessID(): String {
//        return ManagementFactory.getRuntimeMXBean().name.split("@")[0]
//    }
//
//    // Method to check if the process with the given PID is running
//    private fun isProcessRunning(pid: String): Boolean {
//        return try {
//            val process = ProcessBuilder("tasklist", "/FI", "PID eq $pid").start()
//            val reader = process.inputStream.bufferedReader()
//            val output = reader.readLines()
//            output.any { it.contains(pid) }
//        } catch (e: Exception) {
//            false
//        }
//    }
//}
