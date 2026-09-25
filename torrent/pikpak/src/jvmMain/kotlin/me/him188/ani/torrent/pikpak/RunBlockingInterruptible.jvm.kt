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
import java.io.InterruptedIOException

internal actual fun <T> runBlockingInterruptible(block: suspend () -> T): T =
    try {
        runBlocking { block() }
    } catch (e: InterruptedException) {
        // runBlocking swallows the flag when it throws; the interrupt belongs to whoever set it.
        Thread.currentThread().interrupt()
        throw InterruptedIOException("read was interrupted").apply { initCause(e) }
    }

internal actual fun Throwable.isReadInterruption(): Boolean = this is InterruptedIOException
