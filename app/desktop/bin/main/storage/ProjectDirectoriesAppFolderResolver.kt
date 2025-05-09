/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop.storage

import dev.dirs.ProjectDirectories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import me.him188.ani.utils.coroutines.runInterruptible
import java.io.File

object ProjectDirectoriesAppFolderResolver : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories = runBlocking {
        kotlinx.coroutines.withTimeout(5000) {
            runInterruptible(Dispatchers.IO) {
                val projectDirectories = ProjectDirectories.from(
                    appInfo.qualifier,
                    appInfo.organization,
                    appInfo.name,
                )

                AppDataDirectories(
                    File(projectDirectories.dataDir).toPath(),
                    File(projectDirectories.cacheDir).toPath(),
                )
            }
        }
    }
}