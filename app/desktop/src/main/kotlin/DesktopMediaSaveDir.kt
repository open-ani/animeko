/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.preference.MediaCacheSettings
import me.him188.ani.app.data.repository.user.Settings
import java.io.File

/**
 * 保留用户配置的缓存目录, 包括暂时未挂载或不可访问的网络盘.
 * 文件操作失败由下载引擎处理, 启动时不根据权限探测结果切换缓存目录.
 */
internal suspend fun resolveDesktopMediaSaveDir(
    settings: Settings<MediaCacheSettings>,
    defaultSaveDir: String,
): String {
    val configuredSaveDir = settings.flow.first().saveDir
    // 绝对路径和系统分隔符与下载引擎保持一致, 路径转换不访问可能离线的网络盘.
    val saveDir = File(configuredSaveDir ?: defaultSaveDir).absolutePath
    if (configuredSaveDir == null) {
        settings.update { copy(saveDir = saveDir) }
    }
    return saveDir
}
