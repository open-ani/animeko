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
import java.nio.file.Path
import kotlin.io.path.Path

data class AppInfo(
    val qualifier: String,
    val organization: String,
    val name: String,
)

interface AppFolderResolver {
    fun resolve(appInfo: AppInfo): AppDataDirectories

    companion object {
        /**
         * 测试任务用: 设置了此环境变量时, 数据和缓存都放在它指向的目录下, 与机器上正常安装的应用隔离.
         */
        const val TEST_DATA_DIR_ENV = "ANIMEKO_DESKTOP_TEST_DATA_DIR"

        val INSTANCE: AppFolderResolver by lazy {
            System.getenv(TEST_DATA_DIR_ENV)?.takeIf { it.isNotBlank() }?.let { dir ->
                return@lazy FixedAppFolderResolver(Path(dir))
            }
            when (currentPlatformDesktop()) {
                is Platform.Linux -> UnixAppFolderResolver
                is Platform.MacOS -> UnixAppFolderResolver
                is Platform.Windows -> WindowsAppFolderResolver
            }
        }
    }
}

/**
 * 数据放在 [root]`/data`, 缓存放在 [root]`/cache`.
 */
class FixedAppFolderResolver(private val root: Path) : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories =
        AppDataDirectories(root.resolve("data"), root.resolve("cache"))
}

object WindowsAppFolderResolver : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories {
        return runCatching {
            WindowsJnaAppFolderResolver.resolve(appInfo)
        }
//            .recoverCatching {
//            it.printStackTrace()
//            if (System.getenv("ANI_DISALLOW_PROJECT_DIRECTORIES_FALLBACK") == "true") throw it
//            ProjectDirectoriesAppFolderResolver.resolve(appInfo)
//        }
            .getOrThrow()
    }
}

object UnixAppFolderResolver : AppFolderResolver {
    override fun resolve(appInfo: AppInfo): AppDataDirectories {
        return ProjectDirectoriesAppFolderResolver.resolve(appInfo)
    }
}
