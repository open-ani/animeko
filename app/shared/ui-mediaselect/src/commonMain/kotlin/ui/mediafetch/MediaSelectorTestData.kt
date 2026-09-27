/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.flowOf
import me.him188.ani.app.data.repository.media.ManualBrowseMemory
import me.him188.ani.app.domain.media.createTestDefaultMedia
import me.him188.ani.app.domain.media.createTestMediaProperties
import me.him188.ani.app.domain.mediasource.instance.createTestMediaSourceInstance
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.mediasource.web.captcha.createTestWebSessionManager
import me.him188.ani.app.ui.foundation.rememberBackgroundScope
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseState
import me.him188.ani.app.ui.mediaselect.manual.ManualBrowseTarget
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.paging.SizedSource
import me.him188.ani.datasources.api.paging.emptySizedSource
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseEpisode
import me.him188.ani.datasources.api.source.BrowseSubject
import me.him188.ani.datasources.api.source.ConnectionStatus
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.datasources.api.source.MediaMatch
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.MediaSourceLocation
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.datasources.api.topic.FileSize
import me.him188.ani.datasources.api.topic.ResourceLocation
import me.him188.ani.utils.platform.annotations.TestOnly

@TestOnly
val TestMediaFetchRequest
    get() = MediaFetchRequest(
        subjectId = "12345",
        episodeId = "67890",
        subjectNameCN = "关于我转生变成史莱姆这档事 第三季",
        subjectNames = listOf(
            "転生したらスライムだった件 第3期",
            "关于我转生变成史莱姆这档事 第三季",
            "Tensei Shitara Slime Datta Ken Season 3",
        ),
        episodeSort = EpisodeSort("49"),
        episodeName = "恶魔与阴谋",
        episodeEp = EpisodeSort("01"),
    )

@TestOnly
val TestBrowseSubjects: List<BrowseSubject>
    get() = listOf(
        BrowseSubject(name = "命运石之门", url = "https://example.com/subject/1"),
        BrowseSubject(name = "命运石之门 0", url = "https://example.com/subject/2"),
        BrowseSubject(name = "命运石之门 负荷领域的既视感", url = "https://example.com/subject/3"),
        BrowseSubject(name = "命运石之门 OVA", url = "https://example.com/subject/4"),
    )

/**
 * 3 条线路; 线路 1 为 01..24 + OVA + SP.
 */
@TestOnly
val TestBrowseChannels: List<BrowseChannel>
    get() = listOf(
        BrowseChannel(
            name = "线路1",
            episodes = (1..24).map { index ->
                BrowseEpisode(
                    name = index.toString().padStart(2, '0'),
                    url = "https://example.com/play/1/$index",
                    episodeSort = EpisodeSort(index),
                )
            } + listOf(
                BrowseEpisode(name = "OVA", url = "https://example.com/play/1/ova", episodeSort = null),
                BrowseEpisode(name = "SP", url = "https://example.com/play/1/sp", episodeSort = EpisodeSort("SP")),
            ),
        ),
        BrowseChannel(
            name = "线路2",
            episodes = (1..24).map { index ->
                BrowseEpisode(
                    name = "$index",
                    url = "https://example.com/play/2/$index",
                    episodeSort = EpisodeSort(index),
                )
            },
        ),
        BrowseChannel(
            name = "线路3",
            episodes = (1..12).map { index ->
                BrowseEpisode(
                    name = "第 $index 话",
                    url = "https://example.com/play/3/$index",
                    episodeSort = EpisodeSort(index),
                )
            },
        ),
    )

/**
 * 内存假源: supportsBrowsing = true, kind = WEB; 搜索返回固定 4 条; 条目有 3 条线路, 线路 1 为 01..24 + OVA + SP;
 * createMedia 返回 DefaultMedia(kind WEB, episodeRange = episodeSort?.let(EpisodeRange::single)).
 */
@TestOnly
class TestBrowsableMediaSource(
    override val mediaSourceId: String = "test-browse",
    val subjects: List<BrowseSubject> = TestBrowseSubjects,
    val channels: (BrowseSubject) -> List<BrowseChannel> = { TestBrowseChannels },
    val searchDelegate: suspend (String) -> List<BrowseSubject> = { subjects },
) : MediaSource {
    override val kind: MediaSourceKind get() = MediaSourceKind.WEB
    override val info: MediaSourceInfo = MediaSourceInfo(displayName = mediaSourceId)
    override val supportsBrowsing: Boolean get() = true

    override suspend fun checkConnection(): ConnectionStatus = ConnectionStatus.SUCCESS

    override suspend fun fetch(query: MediaFetchRequest): SizedSource<MediaMatch> = emptySizedSource()

    override suspend fun searchSubjects(keyword: String): List<BrowseSubject> = searchDelegate(keyword)

    override suspend fun browseSubject(subject: BrowseSubject): List<BrowseChannel> = channels(subject)

    override fun createMedia(
        subject: BrowseSubject,
        channelName: String?,
        episode: BrowseEpisode,
        episodeSort: EpisodeSort?,
    ): Media = createTestDefaultMedia(
        mediaId = "$mediaSourceId.${episode.url}",
        mediaSourceId = mediaSourceId,
        originalUrl = episode.url,
        download = ResourceLocation.WebVideo(episode.url),
        originalTitle = "${subject.name} ${episode.name}",
        publishedTime = 0,
        properties = createTestMediaProperties(
            subjectName = subject.name,
            episodeName = episode.name,
            subtitleLanguageIds = listOf("CHS"),
            resolution = "1080P",
            alliance = channelName ?: "",
            size = FileSize.Unspecified,
            subtitleKind = null,
        ),
        episodeRange = episodeSort?.let(EpisodeRange::single),
        location = MediaSourceLocation.Online,
        kind = MediaSourceKind.WEB,
    )

    override fun close() {}
}

/**
 * browsableSources = flowOf(listOf(createTestMediaSourceInstance(source))), preferredSourceId = flowOf(null).
 */
@TestOnly
fun createTestManualBrowseState(
    backgroundScope: CoroutineScope,
    source: MediaSource = TestBrowsableMediaSource(),
    target: ManualBrowseTarget? = ManualBrowseTarget(1, "命运石之门", EpisodeSort(25), "25"),
    onPlay: suspend (Media, ManualBrowseMemory?) -> Unit = { _, _ -> },
    webSessionManager: WebSessionManager = createTestWebSessionManager(backgroundScope),
): ManualBrowseState = ManualBrowseState(
    browsableSources = flowOf(listOf(createTestMediaSourceInstance(source))),
    webSessionManager = webSessionManager,
    target = flowOf(target),
    preferredSourceId = flowOf(null),
    onPlay = onPlay,
    backgroundScope = backgroundScope,
)

@Composable
@TestOnly
fun rememberTestManualBrowseState(): ManualBrowseState {
    val backgroundScope = rememberBackgroundScope()
    return remember(backgroundScope) { createTestManualBrowseState(backgroundScope.backgroundScope) }
}
