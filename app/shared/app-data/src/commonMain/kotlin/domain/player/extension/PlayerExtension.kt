/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.UnsafeEpisodeSessionApi
import me.him188.ani.app.domain.player.VideoLoadingState
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
    open fun onStart(backgroundTaskScope: ExtensionBackgroundTaskScope) {
    }

    /**
     * Before [EpisodeFetchSelectPlayState.episodeIdFlow] switches.
     *
     * Old episode id can be obtained using [EpisodeFetchSelectPlayState.episodeIdFlow] in this method.
     */
    open suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {}

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

    val player: MediampPlayer
    val videoLoadingStateFlow: Flow<VideoLoadingState>

    val sessionFlow: Flow<EpisodeSession>

    @UnsafeEpisodeSessionApi
    suspend fun getCurrentEpisodeId(): Int
    suspend fun switchEpisode(newEpisodeId: Int)
}

fun interface EpisodePlayerExtensionFactory<T : PlayerExtension> {
    fun create(context: PlayerExtensionContext, koin: Koin): T
}
