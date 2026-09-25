/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.update

import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.inSystem
import me.him188.ani.utils.io.isDirectory
import me.him188.ani.utils.io.isRegularFile
import me.him188.ani.utils.io.list
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.io.resolveSibling
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.component.KoinComponent

/**
 * 管理更新相关文件的目录.
 *
 * [rootDir] 下每个功能使用各自独立的子目录, 互不嵌套:
 * - [saveDir]: 自动更新下载的安装包. 由自动更新独占, 其中的普通文件都视为安装包.
 * - [devBuildsDir]: 开发者设置「安装指定 commit」下载的产物.
 *
 * 两种安装包在安装成功后进程都会退出, 所以已安装的包统一在下次启动时由 [deleteInstalledFiles] 清理.
 */
class UpdateManager(
    /**
     * 更新文件的根目录, 例如 `cache/updates`. 在 Android 上必须位于 FileProvider 允许共享的路径内.
     */
    val rootDir: SystemPath,
) : KoinComponent {
    companion object {
        private val logger = logger<UpdateManager>()

        const val SAVE_DIR_NAME = "download"
        const val DEV_BUILDS_DIR_NAME = "dev-builds"

        private val COMMIT_SHA_REGEX = Regex("[0-9a-f]{7,40}")

        /**
         * 从 CI 构建的版本号 (如 `4.9.0-main-45f0e6fb`) 中取出 commit sha. 正式版和本地 dev 构建返回 `null`.
         */
        fun devBuildShaOf(versionName: String): String? =
            versionName.substringAfterLast('-', "").takeIf { COMMIT_SHA_REGEX.matches(it) }
    }

    /**
     * 自动更新下载安装包的目录. 由自动更新独占.
     */
    val saveDir: SystemPath = rootDir.resolve(SAVE_DIR_NAME)

    /**
     * 开发者设置「安装指定 commit」下载产物的目录. 安装包文件名包含 commit 的短 sha.
     */
    val devBuildsDir: SystemPath = rootDir.resolve(DEV_BUILDS_DIR_NAME)

    /**
     * 如果此版本与 [file] 版本相同, 则删除 [file]
     */
    fun deleteInstalled(file: SystemPath, currentVersion: String) {
        if (file.name.contains(currentVersion)) {
            file.delete()
        }
    }

    fun deleteInstaller(file: SystemPath) {
        file.delete()
        file.resolveSibling(file.name + ".sha256").delete()
    }

    /**
     * 删除 [saveDir] 中文件名不包含任何 [keepFilenames] 的旧安装包, 为下载新安装包腾出空间.
     *
     * 只处理普通文件, 不会碰目录. 单个文件删除失败只记录日志, 不影响随后的下载.
     */
    fun deleteStaleInstallers(keepFilenames: Collection<String>) {
        if (!saveDir.exists()) return
        for (entry in saveDir.list()) {
            val file = entry.inSystem
            if (file.name == ".DS_Store") continue
            if (!file.isRegularFile()) {
                logger.warn { "Unexpected non-file entry in update save dir, ignoring: $file" }
                continue
            }
            if (keepFilenames.any { file.name.contains(it) }) continue

            logger.info { "Deleting old installer: $file" }
            try {
                deleteInstaller(file)
            } catch (e: Exception) {
                logger.warn(e) { "Failed to delete old installer, ignoring: $file" }
            }
        }
    }

    /**
     * 启动时调用. 删除已经安装到当前版本的安装包:
     * - [saveDir] 中文件名包含 [currentVersion] 的自动更新安装包;
     * - 如果当前版本是 CI 构建 (版本号带 commit sha) 且 [devBuildsDir] 中有该 sha 的包, 则整个 [devBuildsDir].
     *   没有匹配的包时保留目录, 以免删掉 Linux 上等待手动安装的 AppImage.
     *
     * 同时清理 #3441 遗留在 [saveDir] 内部的 `dev-builds` 目录.
     */
    fun deleteInstalledFiles(currentVersion: String = currentAniBuildConfig.versionName) {
        deleteLegacyDevBuildsDir()
        deleteInstalledUpdate(currentVersion)
        deleteInstalledDevBuild(currentVersion)
    }

    private fun deleteInstalledUpdate(currentVersion: String) {
        if (!saveDir.exists()) return
        for (entry in saveDir.list()) {
            if (entry.name.contains(currentVersion)) {
                logger.info { "Deleting old installer file because it matches current version $currentVersion: $entry" }
                deleteInstaller(entry.inSystem)
            }
        }
    }

    private fun deleteInstalledDevBuild(currentVersion: String) {
        val sha = devBuildShaOf(currentVersion) ?: return
        if (!devBuildsDir.isDirectory()) return
        val installed = devBuildsDir.list().any { it.name.contains(sha) }
        if (!installed) return
        logger.info { "Deleting dev builds dir because it contains the package for current version $currentVersion: $devBuildsDir" }
        try {
            devBuildsDir.deleteRecursively()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete dev builds dir, ignoring: $devBuildsDir" }
        }
    }

    /**
     * #3441 曾把 dev builds 放在 `saveDir/dev-builds`, 自动更新清理旧安装包时会因为无法删除目录而失败.
     */
    private fun deleteLegacyDevBuildsDir() {
        val legacy = saveDir.resolve(DEV_BUILDS_DIR_NAME)
        if (!legacy.isDirectory()) return
        logger.info { "Deleting legacy dev builds dir inside update save dir: $legacy" }
        try {
            legacy.deleteRecursively()
        } catch (e: Exception) {
            logger.warn(e) { "Failed to delete legacy dev builds dir, ignoring: $legacy" }
        }
    }
}
