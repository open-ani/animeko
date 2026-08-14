/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import me.him188.ani.app.data.models.preference.MpvOption
import org.openani.mediamp.MediampPlayer

/**
 * 将用户自定义的 mpv 选项 ([me.him188.ani.app.data.models.preference.PlayerKernelConfig.mpvOptions]) 应用到 [player].
 *
 * 只有使用 mpv 内核的平台 (桌面端) 会真正应用, 其他平台以及非 mpv 的播放器实现都是空操作.
 *
 * 选项在 mpv 实例初始化之后设置, 因此只能在启动时设置的选项 (例如 `vo`) 会被 mpv 拒绝, 此时仅记录日志.
 * 可能阻塞调用线程 (mpv 实例是懒创建的), 应在后台线程调用.
 */
internal expect fun applyMpvOptions(player: MediampPlayer, options: List<MpvOption>)
