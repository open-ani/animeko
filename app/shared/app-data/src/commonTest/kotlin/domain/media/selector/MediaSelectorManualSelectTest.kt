/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.player.extension.PlayerLoadErrorHandler
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorManualSelectTest {
    @Test
    fun `blacklist is updated on manual select`() = runSimpleMediaSelectorTestSuite(
        buildTest = {
            initSubject("ゆるキャン△")
            val firstMedia = media(
                mediaId = "ゆるキャン△-a",
                alliance = "A",
                subtitleLanguages = listOf("CHS"),
            )
            val secondMedia = media(
                mediaId = "ゆるキャン△-b",
                alliance = "B",
                subtitleLanguages = listOf("CHT"),
            )
            mediaApi.addMedia(firstMedia)
            mediaApi.addMedia(secondMedia)
        },
    ) {
        val first = mediaApi.mediaList.value[0]
        val second = mediaApi.mediaList.value[1]

        val handler = PlayerLoadErrorHandler(
            getPreferKind = { null },
            getSourceTiers = { MediaSelectorSourceTiers(emptyMap()) },
        )

        coroutineScope {
            val job = launch {
                handler.observeMediaSelectorBlacklist(
                    mediaSelectorFlow = flowOf(selector),
                )
            }

            testScope.runCurrent()
            selector.select(first)
            selector.select(second)
            testScope.advanceUntilIdle()

            assertTrue(
                actual = "ゆるキャン△-a" in handler.blacklist,
                message = "Previous media should be blacklisted",
            )

            job.cancel()
        }

    }
}