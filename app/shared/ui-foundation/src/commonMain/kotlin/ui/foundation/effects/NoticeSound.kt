/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.effects

import androidx.compose.runtime.Composable
import me.him188.ani.app.data.models.preference.NoticeSoundKind

/**
 * 返回一个"响一声系统提示音"的函数, 参数决定响哪一声 ([NoticeSoundKind.None] 则不响).
 *
 * 音色由调用方传入而不是由本函数读设置: 设置界面要能在用户选中某一项时**立刻试听那一项**,
 * 而那一刻新值还没写回设置.
 *
 * 用途很窄, 目前只给后台会话的提示用 (见 `RetainedPlaybackNotice`): 那些提示的前提就是
 * 用户不在看着屏幕 —— 退出播放页去翻别的、或者干脆没在看电视, 等着后台把数据源加载好.
 * 光有一条会自己消失的 toast, 人不看屏幕就等于没提示.
 *
 * **不要给普通 toast 用**: 错误提示在全应用到处都有, 每条都响会很吵.
 *
 * 平台不支持 (桌面 / iOS) 时返回空实现. 具体音色与静音策略见各平台 actual.
 */
@Composable
expect fun rememberNoticeSoundPlayer(): (NoticeSoundKind) -> Unit
