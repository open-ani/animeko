/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import me.him188.ani.app.domain.player.extension.PlayerLoadErrorHandler
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
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
                subtitleLanguages = listOf("CHS")
            )
            val secondMedia = media(
                mediaId = "ゆるキャン△-b",
                alliance = "B",
                subtitleLanguages = listOf("CHT")
            )
            mediaApi.addMedia(firstMedia)
            mediaApi.addMedia(secondMedia)
        }
    ) {
        coroutineScope {
            val handler = PlayerLoadErrorHandler(
                getWebSources = { listOf("source-a", "source-b") },
                getPreferKind = { null },
                getSourceTiers = { MediaSelectorSourceTiers(emptyMap()) } // fake tiers
            )

            val job = launch {
                selector.events.onSelect.collect { event ->
                    if (event.isManualSelect && event.previousMedia != null) {
                        handler.addToBlacklist(event.previousMedia)
                    }
                }
            }
            
            testScope.runCurrent()
            selector.select(mediaApi.mediaList.value[0])
            selector.select(mediaApi.mediaList.value[1])
            testScope.advanceUntilIdle()
            
            assertTrue("ゆるキャン△-a" in handler.blacklist)

            job.cancel()
        }
    }
}