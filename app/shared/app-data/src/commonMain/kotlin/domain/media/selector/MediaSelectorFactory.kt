/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.repository.episode.EpisodeCollectionRepository
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.subject.SubjectRelationsRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory.Companion.withRepositories
import me.him188.ani.datasources.api.Media
import org.koin.core.Koin
import org.koin.mp.KoinPlatform
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.flow.combine
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.withRequestedNumbers
import me.him188.ani.datasources.api.source.MediaFetchRequest

/**
 * 提前给予 episode 和 subject 的 context, 用于构造 [MediaSelector].
 *
 * @see withRepositories
 */
interface MediaSelectorFactory {
    /**
     * @param fetchRequest 查询会话当前生效的请求 ([MediaFetchSession.latestRequest]); 提供时, 用户在编辑器里改的当前集集数
     * 覆盖 Bangumi 的集数 ([withRequestedNumbers]).
     */
    fun create(
        subjectId: Int,
        episodeId: Int,
        mediaList: Flow<List<Media>>,
        flowCoroutineContext: CoroutineContext = Dispatchers.Default,
        fetchRequest: Flow<MediaFetchRequest>? = null,
    ): MediaSelector // 如果要'挂载'自动保存配置, 可以为这个的返回值操作.

    companion object {
        fun withKoin(koin: Koin = KoinPlatform.getKoin()): MediaSelectorFactory = withRepositories(
            episodePreferencesRepository = koin.get(),
            settingsRepository = koin.get(),
            episodeCollectionRepository = koin.get(),
            mediaSourceManager = koin.get(),
            subjectRelationsRepository = koin.get(),
            subjectCollectionRepository = koin.get(),
        )

        fun withRepositories(
            episodePreferencesRepository: EpisodePreferencesRepository,
            settingsRepository: SettingsRepository,
            episodeCollectionRepository: EpisodeCollectionRepository,
            mediaSourceManager: MediaSourceManager,
            subjectRelationsRepository: SubjectRelationsRepository,
            subtitlePreferences: MediaSelectorSubtitlePreferences = MediaSelectorSubtitlePreferences.CurrentPlatform,
            subjectCollectionRepository: SubjectCollectionRepository,
        ): MediaSelectorFactory = object : MediaSelectorFactory {
            override fun create(
                subjectId: Int,
                episodeId: Int,
                mediaList: Flow<List<Media>>,
                flowCoroutineContext: CoroutineContext,
                fetchRequest: Flow<MediaFetchRequest>?,
            ): MediaSelector {
                return DefaultMediaSelector(
                    MediaSelectorContextFlowProducer(
                        episodeCollectionRepository.subjectCompletedFlow(subjectId),
                        mediaSourceManager.allInstances.map { list ->
                            list.map { it.mediaSourceId }
                        },
                        subjectRelationsRepository.subjectSeriesInfoFlow(subjectId),
                        subjectCollectionRepository.subjectCollectionFlow(subjectId).map { it.subjectInfo },
                        episodeCollectionRepository.episodeCollectionInfoFlow(subjectId, episodeId).map { it.episodeInfo }.let { episodeInfo ->
                            if (fetchRequest == null) episodeInfo else combine(episodeInfo, fetchRequest) { info, request -> info.withRequestedNumbers(request) }
                        },
                        mediaSourceManager.mediaSourceTiersFlow(),
                        flowOf(subtitlePreferences),
                    ).flow,
                    mediaList,
                    savedUserPreference = episodePreferencesRepository.mediaPreferenceFlow(subjectId),
                    savedDefaultPreference = settingsRepository.defaultMediaPreference.flow,
                    mediaSelectorSettings = settingsRepository.mediaSelectorSettings.flow,
                    flowCoroutineContext = flowCoroutineContext,
                )
            }
        }
    }
}
