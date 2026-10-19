/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform.features

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import me.him188.ani.utils.coroutines.runCatchingCancellable
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import me.him188.ani.utils.macos.share.MacosShareSheetNative
import java.io.File

/**
 * macOS 的分享菜单: `NSSharingServicePicker`, 由 `utils/macos-share` 模块的 JNI 库实现 ([MacosShareSheetNative]).
 * 原生库在主线程上创建并显示菜单: 菜单从锚点的位置向上弹出, 没有锚点时贴着内容区; 投递到主线程即视为成功.
 * 原生库只在 macOS 主机上构建, 不可用时返回 `false`.
 */
internal object MacosShareSheet : SystemShareSheet {
    private val logger = logger<MacosShareSheet>()

    override suspend fun shareFile(windowHandle: Long, file: File, anchor: DpRect?): Boolean =
        runCatchingCancellable {
            MacosShareSheetNative.ensureLoaded()
            MacosShareSheetNative.showSharingServicePicker(
                window = windowHandle,
                path = file.absolutePath,
                hasAnchor = anchor != null,
                left = anchor?.left?.value?.toDouble() ?: 0.0,
                top = anchor?.top?.value?.toDouble() ?: 0.0,
                width = anchor?.width?.value?.toDouble() ?: 0.0,
                height = anchor?.height?.value?.toDouble() ?: 0.0,
            )
        }.onFailure { logger.warn(it) { "Failed to show the macOS sharing service picker for $file" } }
            .getOrDefault(false)
}
