/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.toFile
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import java.awt.Desktop
import java.io.File

object FileOpener {
    fun openInFileBrowser(file: SystemPath) {
        return openInFileBrowser(file.toFile())
    }

    /**
     * 在 Windows 资源管理器或 macOS Finder 中打开文件所在目录, 并高亮该文件
     */
    fun openInFileBrowser(file: File) {
        if (highlightFile(file)) return

        if (file.isDirectory) {
            Desktop.getDesktop().open(file)
            return
        }
        Desktop.getDesktop().open(file.parentFile)
    }

    private fun highlightFile(file: File): Boolean {
        if (!file.exists()) {
            return false
        }

        return try {
            when (currentPlatform()) {
                is Platform.Windows -> {
                    // Windows
                    val command = listOf("explorer", "/select", "\"", file.absolutePath, "\"")
                    ProcessBuilder(command).start()
                    true
                }

                is Platform.MacOS -> {
                    // macOS
                    val command = listOf("open", "-R", file.absolutePath)
                    ProcessBuilder(command).start()
                    true
                }

                else -> false
            }
        } catch (e: Throwable) {
            false
        }
    }
}