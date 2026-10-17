/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.imageviewer

import androidx.compose.runtime.Composable

/**
 * 把查看器里的图片复制到系统剪贴板.
 */
fun interface ImageClipboard {
    /** 失败时抛出异常. */
    suspend fun copy(file: ImageViewerExportedFile)
}

/**
 * 当前平台的图片剪贴板. 返回 `null` 表示不支持 (查看器隐藏复制按钮).
 */
@Composable
expect fun rememberImageClipboard(): ImageClipboard?
