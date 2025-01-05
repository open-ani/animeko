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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.resolver.toEpisodeMetadata
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.player.ExtensionException
import me.him188.ani.app.domain.player.PlayerExtensionManager
import me.him188.ani.app.domain.player.extension.EpisodePlayerExtensionFactory
import me.him188.ani.app.domain.player.extension.ExtensionBackgroundTaskScope
import me.him188.ani.app.domain.player.extension.PlayerExtension
import me.him188.ani.app.domain.usecase.GlobalKoin
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

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
    extensions: List<EpisodePlayerExtensionFactory<*>>,
    private val koin: Koin = GlobalKoin,
    sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
    val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) {
    private val createMediaFetchSelectBundleFlowUseCase: CreateMediaFetchSelectBundleFlowUseCase by koin.inject()

    private val _episodeIdFlow: MutableStateFlow<Int> = MutableStateFlow(initialEpisodeId)
    val episodeIdFlow: StateFlow<Int> = _episodeIdFlow.asStateFlow()


    private val infoLoader = SubjectEpisodeInfoBundleLoader(subjectId, episodeIdFlow, koin)

    /**
     * Combined subject- and episode-related details.
     *
     * Flow re-emits (almost immediately) when [episode switches][switchEpisode].
     *
     * When an error occurs, the flow emits `null`, and the error can be observed from [infoLoadErrorFlow].
     */
    val infoBundleFlow =
        infoLoader.infoBundleFlow.shareIn(backgroundScope, sharingStarted, replay = 1)
    // TODO: test infoBundleFlow observes value

    /**
     * A flow of the error that occurred during the loading of [infoBundleFlow].
     */
    val infoLoadErrorFlow: StateFlow<LoadError?> = infoLoader.infoLoadErrorState
    // TODO: test error is reflected


    /**
     * A flow of the bundle of [MediaFetchSession] and [MediaSelector].
     *
     * Flow re-emits (almost immediately) when [infoBundleFlow] emits.
     *
     * This flow does not produce errors.
     */
    val fetchSelectFlow = createMediaFetchSelectBundleFlowUseCase(infoBundleFlow)
        .shareIn(backgroundScope, sharingStarted, replay = 1)
    // TODO: 2025/1/4 test fetchSelectFlow changes only when infoBundleFlow's value equality changes 


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
    // TODO: test this

    val playerSession = PlayerSession(
        player,
        koin,
        mainDispatcher
    )

    private val extensionManager by lazy {
        PlayerExtensionManager(extensions, this, koin) // leaking 'this', but should be fine
    }

    private val switchEpisodeLock = Mutex()

    suspend fun switchEpisode(episodeId: Int) {
        currentCoroutineContext()[InSwitchEpisode]?.let { element ->
            error(
                "Recursive switchEpisode call detected. " +
                        "You wanted to switch to $episodeId, while you are already switching to ${element.newEpisodeId}."
            )
        }

        withContext(InSwitchEpisode(episodeId)) {
            switchEpisodeLock.withLock {
                withContext(mainDispatcher) {
                    player.stop()
                }

                extensionManager.call {
                    it.onBeforeSwitchEpisode(episodeId)
                }
                _episodeIdFlow.value = episodeId
            }
        }
    }

    private var backgroundTasksStarted = false

    // Although this function does not perform job that is required to be on the main thread, 
    // but it involves non-stopping background tasks that must only be launched when the user is viewing the page.
    // If the app is in the background, these tasks should not be launched.
    @MainThread
    fun startBackgroundTasks() {
        if (backgroundTasksStarted) return
        backgroundTasksStarted = true


        // This is a very basic feature, not extension, but we launch it here for simplicity.
        ExtensionBackgroundTaskScopeImpl(object : PlayerExtension("LoadMediaOnSelect") {})
            .launch("LoadMediaOnSelect") {
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

        extensionManager.call { extension ->
            extension.onStart(ExtensionBackgroundTaskScopeImpl(extension))
        }
    }

    private inner class ExtensionBackgroundTaskScopeImpl(
        private val extension: PlayerExtension,
    ) : ExtensionBackgroundTaskScope {
        override fun launch(subName: String, block: suspend CoroutineScope.() -> Unit): Job {
            return backgroundScope.launch(
                CoroutineName(extension.name + "." + subName),
                start = CoroutineStart.UNDISPATCHED // TODO
            ) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ExtensionException(
                        "Unhandled exception in background scope from task '$subName' launched by extension '$extension'",
                        e
                    )
                }
            }
        }
    }

    /**
     * Called when view model is cleared
     */
    suspend fun onClose() {
        extensionManager.call { it.onClose() }
    }
}

val EpisodeFetchPlayState.player get() = playerSession.player

private class InSwitchEpisode(
    val newEpisodeId: Int,
) : AbstractCoroutineContextElement(InSwitchEpisode) {
    companion object Key : CoroutineContext.Key<InSwitchEpisode>
}
