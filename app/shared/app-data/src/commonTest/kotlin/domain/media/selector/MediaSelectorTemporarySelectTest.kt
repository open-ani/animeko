/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import kotlinx.io.files.Path
import me.him188.ani.app.domain.media.LocalFileMedia
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.test.TestContainer
import me.him188.ani.utils.io.inSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorTemporarySelectTest {
    private fun localFile(name: String = "episode-01.mkv") =
        LocalFileMedia.create(Path("/videos/$name").inSystem)

    private suspend fun MediaSelector.currentPreferences() = listOf(
        alliance.finalSelected.first(),
        resolution.finalSelected.first(),
        subtitleLanguageId.finalSelected.first(),
        mediaSourceId.finalSelected.first(),
    )

    @Test
    fun `selectTemporarily selects a media outside the candidates without touching preferences`() =
        runSimpleMediaSelectorTestSuite {
            mediaApi.addMedia(media(alliance = "A", resolution = "1080P", subtitleLanguages = listOf("CHS")))
            val local = localFile()
            val preferencesBefore = selector.currentPreferences()

            val collected = selector.collectEvents {
                assertTrue(selector.selectTemporarily(local))
            }

            assertSame(local, selector.selected.value)
            // 本地文件是 WEB 类型, 但既不广播 onChangePreference 也不广播 onPreferWebSource
            collected.assertOrder(OnBeforeSelect::class, OnSelect::class)
            assertNull(collected.onSelect.single().event.previousMedia)
            assertEquals(preferencesBefore, selector.currentPreferences())
        }

    @Test
    fun `selectTemporarily replaces the current selection and is not overridden by auto select`() =
        runSimpleMediaSelectorTestSuite {
            val online = media(alliance = "A", subtitleLanguages = listOf("CHS"))
            mediaApi.addMedia(online)
            assertTrue(selector.select(online))
            val local = localFile()

            val collected = selector.collectEvents {
                assertTrue(selector.selectTemporarily(local))
            }

            assertSame(online, collected.onSelect.single().event.previousMedia)
            assertNull(selector.trySelectDefault())
            assertNull(selector.trySelectCached())
            assertSame(local, selector.selected.value)
        }

    @Test
    fun `selecting the same local file again does nothing`() = runSimpleMediaSelectorTestSuite {
        assertTrue(selector.selectTemporarily(localFile()))

        val collected = selector.collectEvents {
            assertFalse(selector.selectTemporarily(localFile()))
        }

        collected.expectNoEvents()
    }

    @Test
    fun `a regular select after selectTemporarily switches back and updates preferences as usual`() =
        runSimpleMediaSelectorTestSuite {
            val online = media(alliance = "A", resolution = "1080P", subtitleLanguages = listOf("CHS"))
            mediaApi.addMedia(online)
            val local = localFile()
            assertTrue(selector.selectTemporarily(local))

            val collected = selector.collectEvents {
                assertTrue(selector.select(online))
            }

            collected.assertOrder(OnBeforeSelect::class, OnChangePreference::class, OnSelect::class)
            assertSame(local, collected.onSelect.single().event.previousMedia)
            assertSame(online, selector.selected.value)
            assertEquals("A", selector.alliance.finalSelected.first())
        }
}
