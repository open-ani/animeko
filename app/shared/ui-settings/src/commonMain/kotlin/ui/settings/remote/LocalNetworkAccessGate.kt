/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.remote

import androidx.compose.runtime.Composable

/**
 * iOS prompts on the first connection; Android 17 prompts before starting the scanner or
 * connection.
 */
@Composable internal expect fun LocalNetworkAccessGate(onBack: () -> Unit, content: @Composable () -> Unit)
