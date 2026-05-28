/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.torrent.offline

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

interface OfflineDownloadEngine {
    val id: String
    val displayName: String
    val isSupported: StateFlow<Boolean>
    suspend fun resolve(
        uri: String,
        pickVideoFile: (candidateFilenames: List<String>) -> String? = { null },
    ): ResolvedMedia
}

data class ResolvedMedia(
    val streamUrl: String,
    val expiresAt: Instant? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val providerFileId: String? = null,
)

class OfflineDownloadRejectedException(message: String, cause: Throwable? = null) : Exception(message, cause)
class OfflineDownloadAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
