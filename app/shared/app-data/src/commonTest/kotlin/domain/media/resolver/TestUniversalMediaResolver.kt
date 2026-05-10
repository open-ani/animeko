/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.resolver

import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.domain.media.player.data.MediaDataProvider
import me.him188.ani.datasources.api.Media
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.UriMediaData

/**
 * Resolves any media into a [UriMediaData].
 */
object TestUniversalMediaResolver : MediaResolver {
    override fun supports(media: Media): Boolean = true

    override suspend fun resolve(media: Media, episode: EpisodeMetadata): MediaDataProvider<*> =
        TestMediaDataProvider()
}

class TestMediaDataProvider(
    private val uri: String = "https://example.com",
    private val headers: Map<String, String> = emptyMap(),
    override val extraFiles: MediaExtraFiles = MediaExtraFiles.EMPTY,
) : MediaDataProvider<UriMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): UriMediaData {
        return UriMediaData(uri, headers, extraFiles)
    }
}
