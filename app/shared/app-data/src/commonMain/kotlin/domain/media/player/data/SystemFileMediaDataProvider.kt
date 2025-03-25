/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.player.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.io.files.Path
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import org.openani.mediamp.ExperimentalMediampApi
import org.openani.mediamp.source.MediaExtraFiles
import org.openani.mediamp.source.SeekableInputMediaData
import org.openani.mediamp.source.SystemFileMediaData

class SystemFileMediaDataProvider internal constructor(
    val path: Path,
    override val extraFiles: MediaExtraFiles,
    private val fileType: ResourceLocation.LocalFile.FileType?,
) : MediaDataProvider<AniSystemFileMediaData> {
    override suspend fun open(scopeForCleanup: CoroutineScope): AniSystemFileMediaData =
        AniSystemFileMediaData(
            SystemFileMediaData(
                path, extraFiles,
                options = getOptions() ?: emptyList(),
            ),
        )

    private fun getOptions(): List<String>? = when (currentPlatform()) {
        is Platform.Desktop -> {
            when (fileType) {
                null -> null
                ResourceLocation.LocalFile.FileType.MPTS -> {
                    // VLC needs this to play .ts files correctly. Duration won't be parsed on macOS.
                    listOf(
                        ":demux=avformat",
                        ":avformat-options=probesize=524288000,analyzeduration=10000000",
                    )
                }
            }
        }

        Platform.Ios -> null
        is Platform.Android -> null
    }

    override fun toString(): String = "SystemFileMediaDataProvider(path=$path)"
}

@OptIn(ExperimentalMediampApi::class)
class AniSystemFileMediaData(
    val delegate: SystemFileMediaData,
) : SeekableInputMediaData by delegate, FileMediaData {
    override val filename: String get() = delegate.file.name
}
