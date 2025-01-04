/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import me.him188.ani.app.domain.media.fetch.MediaSourceManager
import me.him188.ani.app.domain.media.fetch.create
import me.him188.ani.app.domain.media.fetch.createFetchFetchSessionFlow
import me.him188.ani.app.domain.media.selector.MediaSelectorFactory
import me.him188.ani.app.domain.usecase.UseCase
import me.him188.ani.datasources.api.source.MediaFetchRequest
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
     * Creates a [MediaFetchSelectBundle] for the given [MediaFetchRequest].
     *
     * This function does not throw.
     */
    operator fun invoke(
        fetchRequest: MediaFetchRequest,
    ): Flow<MediaFetchSelectBundle>

    /**
     * Creates a [MediaFetchSelectBundle] for the given [SubjectEpisodeInfoBundle].
     *
     * This function does not throw.
     */
    operator fun invoke(
        subjectEpisodeInfoBundleFlow: Flow<SubjectEpisodeInfoBundle?>,
    ): Flow<MediaFetchSelectBundle?> = subjectEpisodeInfoBundleFlow.transformLatest { bundle ->
        if (bundle == null) {
            emit(null)
            return@transformLatest
        }

        emitAll(
            invoke(
                MediaFetchRequest.create(
                    bundle.subjectCollectionInfo.subjectInfo,
                    bundle.episodeCollectionInfo.episodeInfo,
                ),
            ),
        )
    }
}

class CreateMediaFetchSelectBundleFlowUseCaseImpl(
    private val flowContext: CoroutineContext = Dispatchers.Default,
) : CreateMediaFetchSelectBundleFlowUseCase, KoinComponent {
    private val mediaSourceManager: MediaSourceManager by inject()

    override fun invoke(
        fetchRequest: MediaFetchRequest,
    ): Flow<MediaFetchSelectBundle> = mediaSourceManager.createFetchFetchSessionFlow(
        flowOf(fetchRequest),
    ).map { fetchSession ->
        val selector = MediaSelectorFactory.withKoin(getKoin())
            .create(fetchRequest.subjectId.toInt(), fetchSession.cumulativeResults)

        MediaFetchSelectBundle(
            fetchSession,
            selector,
        )
    }.flowOn(flowContext)
}