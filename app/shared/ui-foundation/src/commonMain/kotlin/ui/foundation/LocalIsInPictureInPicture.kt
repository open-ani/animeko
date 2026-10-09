/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 应用当前是否处于系统画中画小窗.
 *
 * Android 的系统小窗展示整个 Activity 窗口, 挂在应用根节点上的浮层 (例如一起看气泡) 会随窗口一起被缩进小窗,
 * 需要据此决定是否绘制; iOS 的小窗只采集视频 layer, 应用 UI 不会进入小窗, 恒为 false.
 *
 * 由应用根 (`AniAppContent`) 用平台实现 `rememberIsInPictureInPicture` 提供的真实值;
 * 播放页面自己的小窗状态不走这里, 见 `PictureInPictureController`.
 */
val LocalIsInPictureInPicture: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }
