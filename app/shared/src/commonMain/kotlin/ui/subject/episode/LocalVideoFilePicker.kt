/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode

import androidx.compose.runtime.Composable
import me.him188.ani.utils.io.SystemPath

/**
 * 「使用本地视频文件」的文件选择器. 返回的函数用于打开系统文件选择器; 当前平台没有可用的选择器时返回 `null`.
 *
 * 用户选择的视频文件通过 [onPicked] 回调.
 *
 * @param initialDirectory 本地视频库目录. 仅作为选择器的默认起始位置, 用户仍可切换到其他目录.
 * Android 上为 SAF tree URI, 桌面端为绝对路径.
 */
@Composable
expect fun rememberLocalVideoFilePicker(
    onPicked: (SystemPath) -> Unit,
    initialDirectory: String? = null,
): (() -> Unit)?
