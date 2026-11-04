/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.test.runTest
import me.him188.ani.app.domain.media.TestMediaList
import me.him188.ani.app.domain.media.selector.PlaybackSourceMemory
import me.him188.ani.app.domain.mediasource.jsonapi.SampleSite
import me.him188.ani.app.domain.mediasource.jsonapi.createTestJsonApiMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorSearchConfig
import me.him188.ani.app.domain.mediasource.web.createTestSelectorMediaSource
import me.him188.ani.datasources.api.WebMediaRoute
import me.him188.ani.datasources.api.source.MediaSource
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.datasources.api.source.TestHttpMediaSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackConfigurationIdTest {
    @Test
    fun `configuration lookup invalidates old routes for both web source implementations`() = runTest {
        val engine = MockEngine { error("Configuration lookup must not request a page") }
        val original: List<MediaSource> = listOf(
            createTestSelectorMediaSource(SelectorSearchConfig(searchUrl = "https://site/search"), engine),
            createTestJsonApiMediaSource(SampleSite.config, engine),
        )
        val replacements: List<MediaSource> = listOf(
            createTestSelectorMediaSource(SelectorSearchConfig(searchUrl = "https://site/find"), engine),
            createTestJsonApiMediaSource(SampleSite.config.copy(requestInterval = SampleSite.config.requestInterval * 2), engine),
        )
        for ((source, replacement) in original.zip(replacements)) {
            val configuration = assertNotNull(source.playbackConfigurationIdOrNull)
            val media = TestMediaList[0].copy(
                mediaSourceId = source.mediaSourceId,
                kind = MediaSourceKind.WEB,
                properties = TestMediaList[0].properties.copy(
                    webRoute = WebMediaRoute("https://site/subject/1", "line", configuration),
                ),
            )
            val memory = PlaybackSourceMemory()
            memory.recordFailure(memory.beginAttempt(1, media), PlaybackSourceMemory.FailureReason.PLAYBACK)
            memory.retainConfigurations(mapOf(source.mediaSourceId to source.playbackConfigurationIdOrNull))
            assertEquals(1, memory.snapshot().failures.size)
            memory.retainConfigurations(mapOf(source.mediaSourceId to replacement.playbackConfigurationIdOrNull))
            assertTrue(memory.snapshot().failures.isEmpty())
        }
        assertNull(TestHttpMediaSource("other").playbackConfigurationIdOrNull)
    }
}
