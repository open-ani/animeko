/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.episode

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.PlaybackState
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * A state class that combines fetch, select, and play for an episode.
 *
 * It also handles:
 * - [MediaSelectorAutoSelectUseCase]
 */
class EpisodeFetchSelectPlayState(
    val subjectId: Int,
    initialEpisodeId: Int,
    player: MediampPlayer,
    private val backgroundScope: CoroutineScope,
    extensions: List<EpisodePlayerExtensionFactory<*>>,
    private val koin: Koin = GlobalKoin,
    private val sharingStarted: SharingStarted = SharingStarted.WhileSubscribed(),
    val mainDispatcher: CoroutineContext = Dispatchers.Main.immediate,
) {
    private val _episodeSessionFlow = MutableStateFlow(
        newEpisodeSession(initialEpisodeId),
    )

    /**
     * A flow of [EpisodeSession].
     * TODO Document
     */
    val episodeSessionFlow: StateFlow<EpisodeSession> = _episodeSessionFlow.asStateFlow()

    val playerSession = PlayerSession(
        player,
        koin,
        mainDispatcher,
    )

    private val extensionManager by lazy {
        val intrinsicExtensions = listOf(
            EpisodePlayerExtensionFactory { _, _ -> LoadMediaOnSelectExtension() },
        )

        PlayerExtensionManager(
            intrinsicExtensions + extensions,
            this, koin,
        ) // leaking 'this', but should be fine
    }

    private val switchEpisodeLock = Mutex()

    /**
     * Switch to a new episode.
     *
     * This function flushes all background tasks and starts new ones.
     */
    suspend fun switchEpisode(episodeId: Int) {
        currentCoroutineContext()[InSwitchEpisode]?.let { element ->
            error(
                "Recursive switchEpisode call detected. " +
                        "You wanted to switch to $episodeId, while you are already switching to ${element.newEpisodeId}.",
            )
        }

        switchEpisodeLock.withLock {
            /**
             * Caution: switchEpisode maybe called from a session scope task that was launched from [PlayerExtension.onStart].
             *
             * At step 1 we close the scope. This will cancel all session scope tasks, including the current one running this line of code.
             *
             * So we launch a new coroutine to do the actual work.
             */
            backgroundScope.launch {
                withContext(InSwitchEpisode(episodeId)) {
                    // 1. 停止上一个 episode 生命周期内的所有后台任务.
                    logger.info { "SwitchEpisode($episodeId): Stopping previous scope" }
                    _episodeSessionFlow.value.sessionScope.coroutineContext.job.cancelAndJoin()

                    // 2. 暂停播放, '冻结'播放器状态. 此时还不能 stop, 因为要调用扩展.
                    logger.info { "SwitchEpisode($episodeId): Pausing player" }
                    withContext(mainDispatcher) {
                        if (player.playbackState.value == PlaybackState.PLAYING) {
                            player.pause()
                        }
                    }

                    // 3. 调用扩展, 使用旧播放器的状态.
                    logger.info { "SwitchEpisode($episodeId): Calling extension onBeforeSwitchEpisode" }
                    extensionManager.call {
                        it.onBeforeSwitchEpisode(episodeId)
                    }

                    // 4. 停止播放器, 清空播放器状态.
                    logger.info { "SwitchEpisode($episodeId): Stopping player" }
                    withContext(mainDispatcher) {
                        player.stopPlayback()
                    }

                    // 5. 创建新的 fetchSelectSession
                    logger.info { "SwitchEpisode($episodeId): Propagate newEpisodeSession" }
                    val newSession = newEpisodeSession(episodeId)
                    _episodeSessionFlow.value = newSession

                    // 6. Suspend until background tasks are started.
                    logger.info { "SwitchEpisode($episodeId): Start background tasks" }
                    newSession.startSessionScopeTasks()

                    logger.info { "SwitchEpisode($episodeId): Complete" }
                }
            }.join()
        }
    }

    private fun newEpisodeSession(episodeId: Int) = EpisodeSession(
        subjectId,
        episodeId,
        koin,
        backgroundScope.coroutineContext,
        sharingStarted,
    )

    private val uiReady = CompletableDeferred<Unit>()

    fun onUIReady() {
        uiReady.complete(Unit)

        /**
         * Check if we need to startBackgroundTasks. This is needed, because initial value of [_episodeSessionFlow] does not call startBackgroundTasks.
         */
        episodeSessionFlow.value.let { session ->
            if (!session.sessionScopeTasksStarted.value) {
                backgroundScope.launch {
                    session.startSessionScopeTasks() // Will check again if backgroundTasksStarted so thread-safe.
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

    private suspend fun EpisodeSession.startSessionScopeTasks() {
        /**
         * Session-scope tasks are non-stopping, and is not aware of app lifecycle.
         * They must only be launched when the user is viewing the page.
         */
        uiReady.await()

        if (sessionScopeTasksStarted.getAndUpdate { true }) {
            return // already started
        }

        // We've set backgroundTasksStarted = true, so we must ensure tasks are launched (i.e. this coroutine not cancelled)
        withContext(NonCancellable) {
            // Start all extensions in session's scope.
            extensionManager.call { extension ->
                extension.onStart(ExtensionBackgroundTaskScopeImpl(extension, sessionScope))
            }
        }
    }

    private class ExtensionBackgroundTaskScopeImpl(
        private val extension: PlayerExtension,
        private val scope: CoroutineScope,
    ) : ExtensionBackgroundTaskScope {
        override fun launch(subName: String, block: suspend CoroutineScope.() -> Unit): Job {
            return scope.launch(
                CoroutineName(extension.name + "." + subName),
                start = CoroutineStart.UNDISPATCHED, // TODO
            ) {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw ExtensionException(
                        "Unhandled exception in background scope from task '$subName' launched by extension '$extension'",
                        e,
                    )
                }
            }
        }
    }

    /**
     * An intrinsic extension that is automatically and forcefully added to the extension manager.
     *
     * This extension calls [PlayerSession.loadMedia] when a new media is selected.
     */
    private inner class LoadMediaOnSelectExtension : PlayerExtension("LoadMediaOnSelect") {
        override fun onStart(backgroundTaskScope: ExtensionBackgroundTaskScope) {
            backgroundTaskScope.launch("LoadMediaOnSelect") {
                episodeSessionFlow.collectLatest { episodeSession ->
                    episodeSession.fetchSelectFlow.collectLatest fetchSelect@{ fetchSelect ->
                        if (fetchSelect == null) return@fetchSelect

                        // `filterNotNull()` is needed. Even when media is unselect, we should not stop the player.
                        fetchSelect.mediaSelector.selected.filterNotNull().collectLatest { media ->
                            playerSession.loadMedia(
                                media,
                                episodeSession.infoBundleFlow
                                    .filterNotNull()
                                    .first()
                                    .episodeInfo.toEpisodeMetadata(),
                            )
                        }
                    }
                }
            }
        }
    }

    private companion object {
        private val logger = logger<EpisodeFetchSelectPlayState>()
    }
}

/**
 * A flow of the error that occurred during the loading of [infoBundleFlow].
 */
@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.infoLoadErrorFlow: Flow<LoadError?> get() = episodeSessionFlow.flatMapLatest { it.infoLoadErrorStateFlow }

/**
 * Combined subject- and episode-related details.
 *
 * Flow re-emits (almost immediately) when [episode switches][EpisodeFetchSelectPlayState.switchEpisode].
 *
 * When an error occurs, the flow emits `null`, and the error can be observed from [infoLoadErrorFlow].
 */
@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.infoBundleFlow get() = episodeSessionFlow.flatMapLatest { it.infoBundleFlow }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.mediaFetchSessionFlow: Flow<MediaFetchSession?>
    get() = episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }.map { it?.mediaFetchSession }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.mediaSelectorFlow: Flow<MediaSelector?>
    get() = episodeSessionFlow.flatMapLatest { it.fetchSelectFlow }.map { it?.mediaSelector }

@UnsafeEpisodeSessionApi
val EpisodeFetchSelectPlayState.episodeIdFlow get() = episodeSessionFlow.map { it.episodeId }

val EpisodeFetchSelectPlayState.player get() = playerSession.player

/**
 * Gets the episodeId at the current moment.
 */
@UnsafeEpisodeSessionApi
suspend fun EpisodeFetchSelectPlayState.getCurrentEpisodeId(): Int {
    return episodeIdFlow.first()
}


/**
 * Marks an API as unsafe to use when collecting from multiple flows marked with this annotation.
 *
 * - If you are collecting a [EpisodeFetchSelectPlayState.episodeSessionFlow] flow, calling this method is safe, and you can opt in [UnsafeEpisodeSessionApi].
 * - If you are collecting a flatmap-ed flow from [EpisodeFetchSelectPlayState.episodeSessionFlow], it's NOT safe to call this method.
 *
 * Example of WRONG use-case:
 * ```
 * episodeSession.flatMapLatest { it.fetchSelectFlow }.collectLatest { fetchSelect ->
 *     fetchSelect.mediaSelectorFlow.filterNotNull().flatMapLatest { it.selected }.collectLatest { media ->
 *         // Selected media has changed, let's save user's preferences!
 *         val episodeId = getCurrentEpisodeId() // WRONG.
 *         savePreference(episodeId, media.mediaProperties)
 *     }
 * }
 * ```
 *
 * In the above example, your code works fine if nobody is calling [EpisodeFetchSelectPlayState.switchEpisode].
 * However, if there do, your `getCurrentEpisodeId()` may receive a new episodeId. So you end up saving the preference for the new episode.
 *
 * A correct way to do this is to `collectLatest` from [EpisodeFetchSelectPlayState.episodeSessionFlow] directly.
 *
 * episodeSession.collectLatest { session ->
 *     session.mediaSelectorFlow.flatMapLatest { it.mediaSelector.selected }.collectLatest { media ->
 *         val episodeId = session.episodeId // Correct. We are using the episodeId from the session that you are observing mediaSelectorFlow from.
 *         savePreference(episodeId, media.mediaProperties)
 *     }
 * }
 */
@RequiresOptIn(
    message = "This flow API is unsafe for use. When you collect from multiple flows marked with this annotation, you may see inconsistent (old) data from one flow." +
            "You must not combine these unsafe flows. If you need to combine them, use flows from fetchSelectSession",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeEpisodeSessionApi


/**
 * A context element that indicates that the coroutine is in the process of switching episodes [EpisodeFetchSelectPlayState.switchEpisode].
 */
private class InSwitchEpisode(
    val newEpisodeId: Int,
) : AbstractCoroutineContextElement(InSwitchEpisode) {
    companion object Key : CoroutineContext.Key<InSwitchEpisode>
}
