/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media

import me.him188.ani.datasources.api.DefaultMedia
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.MediaExtraFiles
import me.him188.ani.datasources.api.MediaProperties
import me.him188.ani.datasources.api.Subtitle
import me.him188.ani.datasources.api.SubtitleKind
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.datasources.api.topic.FileSize.Companion.megaBytes
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseSimplified
import me.him188.ani.datasources.api.topic.SubtitleLanguage.ChineseTraditional
import me.him188.ani.utils.platform.annotations.TestOnly

@TestOnly
const val SOURCE_DMHY = "dmhy"

@TestOnly
const val SOURCE_ACG = "acg.rip"

@TestOnly
fun createTestDefaultMedia(
    mediaId: String,
    mediaSourceId: String, // e.g. "dmhy"
    originalUrl: String,
    download: ResourceLocation,
    originalTitle: String,
    publishedTime: Long,
    properties: MediaProperties,
    episodeRange: EpisodeRange?,
    extraFiles: MediaExtraFiles = MediaExtraFiles.Empty,
    location: MediaSourceLocation,
    kind: MediaSourceKind,
//    mediaSourceInstanceId: String = mediaSourceId,
): DefaultMedia = DefaultMedia(
    mediaId = mediaId,
    mediaSourceId = mediaSourceId,
//    mediaSourceInstanceId = mediaSourceInstanceId,
    originalUrl = originalUrl,
    download = download,
    originalTitle = originalTitle,
    publishedTime = publishedTime,
    properties = properties,
    episodeRange = episodeRange,
    extraFiles = extraFiles,
    location = location,
    kind = kind,
)

fun createTestMediaProperties(
    subjectName: String? = null,
    episodeName: String? = null,
    subtitleLanguageIds: List<String> = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
    resolution: String = "1080P",
    alliance: String = "桜都字幕组",
    size: FileSize = 122.megaBytes,
    subtitleKind: SubtitleKind? = SubtitleKind.CLOSED,
): MediaProperties = MediaProperties(
    subjectName = subjectName,
    episodeName = episodeName,
    subtitleLanguageIds = subtitleLanguageIds,
    resolution = resolution,
    alliance = alliance,
    size = size,
    subtitleKind = subtitleKind,
)

// Used by many test, don't change it. 
// If you want to change it, copy it instead.
@TestOnly
val TestMediaList = listOf(
    createTestDefaultMedia(
        mediaId = "$SOURCE_DMHY.1",
        mediaSourceId = SOURCE_DMHY,
        originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 1,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 122.megaBytes,
            subtitleKind = SubtitleKind.CLOSED,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
        extraFiles = MediaExtraFiles(
            listOf(
                Subtitle(
                    uri = "https://example.com/1",
                    mimeType = "text/x-ass",
                    language = "简体中文",
                ),
            ),
        ),
    ),
    // exactly same properties as the first one, except for the ids.
    createTestDefaultMedia(
        mediaId = "$SOURCE_ACG.1",
        mediaSourceId = SOURCE_ACG,
        originalTitle = "[桜都字幕组] 孤独摇滚 ABC ABC ABC ABC ABC ABC ABC ABC ABC ABC",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 2,
        episodeRange = EpisodeRange.single(EpisodeSort(1)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseSimplified, ChineseTraditional).map { it.id },
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 122.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    ),

    createTestDefaultMedia(
        mediaId = "$SOURCE_DMHY.2",
        mediaSourceId = SOURCE_DMHY,
        originalTitle = "夜晚的水母不会游泳",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 3,
        episodeRange = EpisodeRange.single(EpisodeSort(2)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseTraditional).map { it.id },
            resolution = "1080P",
            alliance = "北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组北宇治字幕组",
            size = 233.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    ),
    createTestDefaultMedia(
        mediaId = "$SOURCE_ACG.2",
        mediaSourceId = SOURCE_ACG,
        originalTitle = "葬送的芙莉莲",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 4,
        episodeRange = EpisodeRange.single(EpisodeSort(2)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(ChineseSimplified).map { it.id },
            resolution = "1080P",
            alliance = "桜都字幕组",
            size = 0.bytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    ),
    createTestDefaultMedia(
        mediaId = "$SOURCE_ACG.3",
        mediaSourceId = SOURCE_ACG,
        originalTitle = "某个生肉",
        download = ResourceLocation.MagnetLink("magnet:?xt=urn:btih:1"),
        originalUrl = "https://example.com/1",
        publishedTime = 5,
        episodeRange = EpisodeRange.single(EpisodeSort(3)),
        properties = createTestMediaProperties(
            subtitleLanguageIds = listOf(),
            resolution = "1080P",
            alliance = "Lilith-Raws",
            size = 702.megaBytes,
            subtitleKind = null,
        ),
        kind = MediaSourceKind.BitTorrent,
        location = MediaSourceLocation.Online,
    ),
)

@TestOnly
val TestMediaSourceInfo = MediaSourceInfo("Mikan")
