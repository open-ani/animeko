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
 * iOS 暂未提供本地视频文件选择器.
 */
@Composable
actual fun rememberLocalVideoFilePicker(onPicked: (SystemPath) -> Unit): (() -> Unit)? = null
