/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.EpisodeSwitchTrigger
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.domain.player.analytics.PlayFlowRecorder
import org.koin.core.Koin
import org.openani.mediamp.MediampPlayer


/**
 * An extension of the player.
 *
 * Instances are created by [EpisodePlayerExtensionFactory].
 */
abstract class PlayerExtension(
    val name: String,
) {

    /**
     * Called when the extension is allowed to launchpad background tasks.
     */
    open fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
    }

    /**
     * Before [EpisodeFetchSelectPlayState.episodeIdFlow] switches.
     *
     * Old episode id can be obtained using [EpisodeFetchSelectPlayState.episodeIdFlow] in this method.
     */
    open suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {}

    /**
     * Before the media selected in [episodeSession] is loaded into the player, replacing the media it is playing.
     *
     * The player still holds the previous media of the same episode (or no media, for the first load of the episode),
     * so this is the last chance to read its state, e.g. to save the playback progress before switching sources.
     * The start position of the new media is read from the play history after all extensions return.
     */
    open suspend fun onBeforeLoadMedia(episodeSession: EpisodeSession) {}

    /**
     * Called typically when view model is being cleared (user exiting the page).
     */
    open suspend fun onClose() {}
}


interface ExtensionBackgroundTaskScope {
    /**
     * Launch a background task.
     *
     * @param subName A name for the job for debugging purposes.
     */
    fun launch(subName: String, block: suspend CoroutineScope.() -> Unit): Job
}


/**
 * Currently synonymous to [EpisodeFetchSelectPlayState].
 */
interface PlayerExtensionContext {
    val subjectId: Int

    /**
     * 本播放页的加载流程埋点记录器. 事件应带 `EpisodeSession.playFlowEpisode`, 不读 [PlayFlowRecorder.currentEpisode].
     */
    val playFlow: PlayFlowRecorder

    val player: MediampPlayer
    val videoLoadingStateFlow: Flow<VideoLoadingState>

    val sessionFlow: Flow<EpisodeSession>

    /**
     * A shared event channel for all extensions.
     */
    val broadcastEvent: SharedFlow<PlayerExtensionEvent>

    @UnsafeEpisodeSessionApi
    suspend fun getCurrentEpisodeId(): Int

    /**
     * @param trigger 切集的发起方, 记入埋点.
     */
    suspend fun switchEpisode(newEpisodeId: Int, trigger: EpisodeSwitchTrigger = EpisodeSwitchTrigger.MANUAL)

    suspend fun broadcast(event: PlayerExtensionEvent)
}

inline fun <reified T : PlayerExtensionEvent> PlayerExtensionContext.subscribeEvents(): Flow<T> {
    return broadcastEvent.filterIsInstance<T>()
}

interface PlayerExtensionEvent

fun interface EpisodePlayerExtensionFactory<T : PlayerExtension> {
    fun create(context: PlayerExtensionContext, koin: Koin): T
}
