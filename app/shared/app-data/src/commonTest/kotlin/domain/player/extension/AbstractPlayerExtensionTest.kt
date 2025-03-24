/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import me.him188.ani.app.domain.episode.EpisodeFetchSelectPlayState
import me.him188.ani.app.domain.episode.EpisodePlayerTestSuite
import org.openani.mediamp.PlaybackState
import org.openani.mediamp.metadata.MediaProperties
import org.openani.mediamp.test.TestMediampPlayer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

abstract class AbstractPlayerExtensionTest {
    protected val subjectId = 1
    protected val initialEpisodeId = 2


    fun EpisodePlayerTestSuite.createState(
        extensions: List<EpisodePlayerExtensionFactory<*>> = listOf(),
    ): EpisodeFetchSelectPlayState {
        return EpisodeFetchSelectPlayState(
            subjectId,
            initialEpisodeId,
            player,
            backgroundScope,
            extensions = extensions,
            koin,
            mainDispatcher = EmptyCoroutineContext, // no switch
        )
    }

    fun EpisodePlayerTestSuite.setMediaDuration(durationMillis: Long) {
        player.mediaProperties.value = player.mediaProperties.value?.copy(durationMillis = durationMillis)
            ?: MediaProperties.Empty.copy(durationMillis = durationMillis)
    }


    @BeforeTest
    fun installDispatcher() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterTest
    fun resetDispatcher() {
        Dispatchers.resetMain()
    }
}

fun TestMediampPlayer.loadMedia(
    durationMs: Long,
) {
    mediaProperties.value = MediaProperties(
        durationMillis = durationMs,
    )
    playbackState.value = PlaybackState.READY
}
