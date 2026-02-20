/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.torrent.parcel

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.him188.ani.utils.coroutines.CancellationException

/**
 * Exception which can be parceled and transact in async remote call.
 */
@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
@Parcelize
class RemoteContinuationException(
    val throwableName: String,
    override val message: String?,
    val serializedCause: String?,
) : Parcelable, Exception(
    "$throwableName: $message",
    serializedCause?.let { RemoteContinuationException("Exception", it, null) },
) {
    fun smartCast(): Exception {
        return if (throwableName.contains("CancellationException")) {
            CancellationException(message, cause)
        } else {
            this
        }
    }
}
