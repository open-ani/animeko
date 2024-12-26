/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import me.him188.ani.app.torrent.anitorrent.AnitorrentLibraryLoader
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.logger
import kotlin.system.exitProcess

object TestTasks {
    private val logger = logger<TestTasks>()
    fun handleTestTask(taskName: String, args: List<String>): Nothing {
        when (taskName) {
            "anitorrent-load-test" -> {
                AnitorrentLibraryLoader.loadLibraries()
                exitProcess(0)
            }

            else -> {
                logger.error { "Unknown test task: $taskName" }
                exitProcess(1)
            }
        }
    }

}