/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import me.him188.ani.utils.io.SystemPath

/**
 * 自动更新
 */
@Stable
sealed interface AppUpdateState {
    /**
     * 没有开启自动检查更新, 需要点击检查
     */
    @Immutable
    data object ClickToCheck : AppUpdateState

    /**
     * 已经是最新版本
     */
    @Immutable
    data object AlreadyUpToDate : AppUpdateState

    sealed interface HasNewVersion : AppUpdateState {
        val version: NewVersion
    }

    /**
     * 有新版本, 而且没有开启自动下载, 所以要展示一个 "更新" 图标
     */
    @Immutable
    data class HasUpdate(override val version: NewVersion) : AppUpdateState, HasNewVersion

    /**
     * 正在下载更新
     */
    @Stable
    data class Downloading(
        override val version: NewVersion,
        private val fileDownloaderStats: FileDownloaderStats,
    ) : HasNewVersion {
        val progress: Float get() = fileDownloaderStats.progress
    }

    /**
     * 正在下载更新
     */
    @Stable
    data class DownloadFailed(
        override val version: NewVersion,
        val throwable: Throwable,
    ) : HasNewVersion

    /**
     * 已经下载完成, 点击安装
     */
    @Immutable
    data class Downloaded(
        override val version: NewVersion,
        val file: SystemPath,
    ) : HasNewVersion

    /** 正在安装更新, 应保持应用运行直到安装器返回结果. */
    @Immutable
    data class Installing(override val version: NewVersion) : HasNewVersion

    companion object
}
