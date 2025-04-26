/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import me.him188.ani.app.data.models.subject.SubjectSeriesInfo
import me.him188.ani.app.data.repository.media.EpisodePreferencesRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.fetch.createFetchFetchSessionFlow
import me.him188.ani.app.domain.media.selector.DefaultMediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorContextFlowProducer
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.source.MediaFetchRequest
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.collections.tupleOf
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

/**
 * A use case that constructs [MediaFetchSelectBundle]s according to [MediaFetchRequest] or [SubjectEpisodeInfoBundle].
 *
 * It simply calls factories and does not perform I/O.
 *
 * @see MediaFetchSelectBundle
 */
fun interface CreateMediaFetchSelectBundleFlowUseCase : UseCase {

    /**
     * Creates a [MediaFetchSelectBundle] for the given [SubjectEpisodeInfoBundle].
     *
     * This function does not throw.
     */
    operator fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>,
    ): Flow<MediaFetchSelectBundle?>
}

class CreateMediaFetchSelectBundleFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : CreateMediaFetchSelectBundleFlowUseCase, KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()
    private val episodePreferencesRepository: EpisodePreferencesRepository by inject()
    private val settingsRepository: SettingsRepository by inject()

    override fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>
    ): Flow<MediaFetchSelectBundle?> {
        val bundleDistinct = subjectEpisodeInfoBundleFlow
            .distinctUntilChangedBy { bundle ->
                if (bundle == null) {
                    null
                } else {
                    // 这里需要指定所有需要的参数, 当这些参数变更时重新创建搜索
                    tupleOf(
                        bundle.subjectInfo.subjectId,
                        bundle.episodeInfo.episodeId,

                        bundle.subjectCollectionInfo.subjectInfo.nameCn,
                        bundle.subjectCollectionInfo.subjectInfo.name,
                        bundle.subjectCollectionInfo.subjectInfo.allNames,

                        bundle.episodeInfo.sort,
                        bundle.episodeInfo.ep,

                        bundle.episodeInfo.name,
                        bundle.episodeInfo.nameCn,

                        bundle.seriesInfo,
                        bundle.subjectCompleted,
                    )
                }
            }

        // 刚开始是 `null`
        val fetchRequestFlow: Flow<MediaFetchSession?> = bundleDistinct
            // 为什么要 `filterNotNull`:
            // 如果本地有缓存, 我们会优先读取缓存, bundle 会不是 null. 如果缓存有过期, 此时会同时发起网络查询. 
            // 如果加载发生网络错误, 然后用户点击 "重试", bundle 会变为 `null`. 
            // 然而即使进行了网络查询, 新的数据很有可能跟旧的数据是一样的, 就没有必要重新查询. 
            // 所以我们总是等待一个 not null SubjectEpisodeInfoBundle 比较.
            .filterNotNull()
            .map { bundle ->
                MediaFetchRequest.create(
                    bundle.subjectCollectionInfo.subjectInfo,
                    bundle.episodeCollectionInfo.episodeInfo,
                )
            }
            .distinctUntilChanged() // very important to avoid re-query
            .flatMapLatest { req ->
                logger.info { "MediaFetchRequest changed. Creating MediaFetchSession for reqeust: $req" }
                mediaSourceManager.createFetchFetchSessionFlow(flowOf(req))
            }
            .onStart<MediaFetchSession?> { emit(null) }

        return combine(bundleDistinct, fetchRequestFlow) { bundle, fetchSession ->
            tupleOf(bundle, fetchSession)
        }.mapLatest { (bundle, fetchSession) ->
            bundle ?: return@mapLatest null
            fetchSession ?: return@mapLatest null

            val selector = DefaultMediaSelector(
                MediaSelectorContextFlowProducer(
                    // TODO: 2025/4/22 Collect all these information from the ani server
                    flowOf(bundle.subjectCompleted ?: false), // accesses network
                    mediaSourceManager.allInstances.map { list ->
                        list.map { it.mediaSourceId }
                    },
                    flowOf(bundle.seriesInfo ?: SubjectSeriesInfo.Fallback),
                    flowOf(bundle.subjectInfo),
                    flowOf(bundle.episodeInfo),
                    mediaSourceManager.mediaSourceTiersFlow(), // only access local settings
                ).flow,
                fetchSession.cumulativeResults,
                savedUserPreference = episodePreferencesRepository.mediaPreferenceFlow(bundle.subjectId), // only access local settings
                savedDefaultPreference = settingsRepository.defaultMediaPreference.flow,
                mediaSelectorSettings = settingsRepository.mediaSelectorSettings.flow,
                flowCoroutineContext = flowContext,
            )

            MediaFetchSelectBundle(
                fetchSession,
                selector,
            )
        }.flowOn(flowContext)
    }

    private companion object {
        private val logger = logger<CreateMediaFetchSelectBundleFlowUseCaseImpl>()
    }
}