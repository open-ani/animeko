/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.pikpak

// The player reads on a thread it owns and cancels by interrupting it: ExoPlayer's Loader does
// exactly that on seek and on release. runBlocking answers an interrupt with InterruptedException,
// which media3 does not recognise and reports as "Unexpected exception loading stream"; an
// IOException is the cancellation it already handles.
internal expect fun <T> runBlockingInterruptible(block: suspend () -> T): T

// A read the player interrupted is it cancelling the load, not a failure worth retrying.
internal expect fun Throwable.isReadInterruption(): Boolean
