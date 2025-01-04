/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.MediaSelectorEventSavePreferenceUseCase
import me.him188.ani.app.domain.player.AutoSwitchMediaOnPlayerErrorUseCase
import me.him188.ani.app.domain.usecase.GlobalKoin
import org.koin.core.Koin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.openani.mediamp.MediampPlayer

/**
 * A state class that combines fetch, select, and play for an episode.
 *
 * It also handles:
 * - [MediaSelectorAutoSelectUseCase]
 */
class EpisodeFetchPlayState(
    val subjectId: Int,
    initialEpisodeId: Int,
    player: MediampPlayer,
    private val backgroundScope: CoroutineScope,
    private val koin: Koin = GlobalKoin,
) : KoinComponent {
    private val mediaSelectorAutoSelectUseCase: MediaSelectorAutoSelectUseCase by inject()
    private val mediaSelectorEventSavePreferenceUseCase: MediaSelectorEventSavePreferenceUseCase by inject()
    private val createMediaFetchSelectBundleFlowUseCase: CreateMediaFetchSelectBundleFlowUseCase by inject()
    private val autoSwitchMediaOnPlayerErrorUseCase: AutoSwitchMediaOnPlayerErrorUseCase by inject()

    private val _episodeIdFlow: MutableStateFlow<Int> = MutableStateFlow(initialEpisodeId)
    val episodeIdFlow: StateFlow<Int> = _episodeIdFlow.asStateFlow()


    private val infoLoader = SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)

    /**
     * Combined subject- and episode-related details.
     *
     * Flow re-emits (almost immediately) when [episode switches][setEpisodeId].
     *
     * When an error occurs, the flow emits `null`, and the error can be observed from [infoLoadErrorState].
     */
    val infoBundleFlow =
        infoLoader.infoBundleFlow.shareIn(backgroundScope, SharingStarted.WhileSubscribed(), replay = 1)

    /**
     * A flow of the error that occurred during the loading of [infoBundleFlow].
     */
    val infoLoadErrorState: StateFlow<LoadError?> = infoLoader.infoLoadErrorState


    /**
     * A flow of the bundle of [MediaFetchSession] and [MediaSelector].
     *
     * Flow re-emits (almost immediately) when [infoBundleFlow] emits.
     *
     * This flow does not produce errors.
     */
    val fetchSelectFlow = createMediaFetchSelectBundleFlowUseCase(infoBundleFlow)
        .shareIn(backgroundScope, SharingStarted.WhileSubscribed(), 1)


    /**
     * A cold flow that emits `true` when media sources are loading.
     *
     * - When all media sources have completed, this flow emits `false`.
     * - If there is an error when loading episode data, this flow emits `false`.
     */
    val mediaSourceLoadingFlow = fetchSelectFlow
        .transformLatest { bundle ->
            if (bundle == null) {
                emit(false)
                return@transformLatest
            }
            emitAll(
                bundle.mediaFetchSession.hasCompleted.map {
                    !it.allCompleted()
                },
            )
        }

    val playerSession = PlayerSession(
        player,
        koin,
    )

    fun setEpisodeId(episodeId: Int) {
        _episodeIdFlow.value = episodeId
    }

    private var backgroundTasksStarted = false

    // Although this function does not perform job that is required to be on the main thread, 
    // but it involves non-stopping background tasks that must only be launched when the user is viewing the page.
    // If the app is in the background, these tasks should not be launched.
    @MainThread
    fun startBackgroundTasks() {
        if (backgroundTasksStarted) return
        backgroundTasksStarted = true

        backgroundScope.launch(CoroutineName("MediaSelectorAutoSelect")) {
            fetchSelectFlow.collectLatest { bundle ->
                if (bundle == null) return@collectLatest
                mediaSelectorAutoSelectUseCase(bundle.mediaFetchSession, bundle.mediaSelector)
            }
        }

        backgroundScope.launch(CoroutineName("MediaSelectorEventSavePreference")) {
            fetchSelectFlow.collectLatest { bundle ->
                if (bundle == null) return@collectLatest
                mediaSelectorEventSavePreferenceUseCase(bundle.mediaSelector, subjectId)
            }
        }

        backgroundScope.launch(CoroutineName("AutoSwitchMediaOnPlayerError")) {
            autoSwitchMediaOnPlayerErrorUseCase(
                fetchSelectFlow.filterNotNull(),
                playerSession.videoLoadingState,
                playerSession.player.playbackState,
            )
        }

        backgroundScope.launch(CoroutineName("LoadMedia")) {
            fetchSelectFlow.collectLatest { fetchSelect ->
                if (fetchSelect == null) return@collectLatest

                fetchSelect.mediaSelector.selected.collectLatest { media ->
                    playerSession.loadMedia(
                        media,
                        infoBundleFlow
                            .filterNotNull()
                            .map { it.episodeInfo.toEpisodeMetadata() }
                            .first(),
                    )
                }
            }
        }
    }

    override fun getKoin(): Koin = koin
}
