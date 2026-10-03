/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

import kotlinx.coroutines.runBlocking

// Native threads carry no interrupt flag, so there is nothing to translate here.
internal actual fun <T> runBlockingInterruptible(block: suspend () -> T): T = runBlocking { block() }

internal actual fun Throwable.isReadInterruption(): Boolean = false
