/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop.storage

import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatformDesktop

data class AppInfo(
    val qualifier: String,
    val organization: String,
    val name: String,
)

interface AppFolderResolver {
    fun resolve(appInfo: AppInfo): AppDataDirectories

    companion object {
        val INSTANCE: AppFolderResolver by lazy {
            when (currentPlatformDesktop()) {
                is Platform.Linux -> UnixAppFolderResolver
                is Platform.MacOS -> UnixAppFolderResolver
                is Platform.Windows -> WindowsAppFolderResolver
            }
        }
    }
}

object WindowsAppFolderResolver : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories {
        return runCatching {
            WindowsJnaAppFolderResolver.resolve(appInfo)
        }.recoverCatching {
            it.printStackTrace()
            if (System.getenv("ANI_DISALLOW_PROJECT_DIRECTORIES_FALLBACK") == "true") throw it
            ProjectDirectoriesAppFolderResolver.resolve(appInfo)
        }.getOrThrow()
    }
}

object UnixAppFolderResolver : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories {
        return ProjectDirectoriesAppFolderResolver.resolve(appInfo)
    }
}
