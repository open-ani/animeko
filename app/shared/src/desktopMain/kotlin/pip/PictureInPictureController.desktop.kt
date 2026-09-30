/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.pip

import androidx.compose.runtime.Composable
import org.openani.mediamp.MediampPlayer

/**
 * 桌面端没有系统级画中画 (macOS/Windows 无统一 API), 使用 no-op 实现.
 * 桌面端的"小窗"需求由窗口置顶 (always-on-top) 承担.
 */
@Composable
actual fun rememberPictureInPictureController(player: MediampPlayer): PictureInPictureController =
    NoOpPictureInPictureController
