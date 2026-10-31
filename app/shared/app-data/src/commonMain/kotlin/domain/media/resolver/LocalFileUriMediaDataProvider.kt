/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData

/**
 * 通过 URI 播放本地视频. Android 上用于 SAF 返回的 `content://` 文档 URI,
 * 由 ExoPlayer 的 ContentResolver 数据源直接读取, 不需要先复制文件.
 */
class LocalFileUriMediaDataProvider(
    val uri: String,
    val originalTitle: String,
    private val headers: Map<String, String> = emptyMap(),
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
) : MediaDataProvider<UriMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData = UriMediaData(uri, headers, extraFiles)
    override fun toString(): String = "LocalFileUriVideoSource(uri='$uri')"
}
