/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnBeforeSelect
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnPreferWebSource
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnSelect
import me.him188.ani.app.domain.media.selector.testFramework.SimpleMediaSelectorTestSuite
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@TestContainer
class MediaSelectorEventOrderTest {
    /**
     * SEL-02 的四步副作用顺序:
     * (1) onBeforeSelect -> (2) `selected.value = candidate` -> (3) 写偏好 + onChangePreference (+WEB 时 onPreferWebSource)
     * -> (4) onSelect.
     *
     * 第 (2) 步靠每个事件的 `selectedAtEmit` 钉住: onBeforeSelect 必须看到旧值, 其余三个事件必须看到新值.
     * 只断言 assertOrder 是不够的 —— 把 `selected.value = candidate` 挪到广播之后, 事件顺序完全不变.
     */
    private suspend fun SimpleMediaSelectorTestSuite.checkSelectWebMediaSideEffectOrder() {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
        preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
            subjectInfo = SubjectInfo.Empty.copy(subjectId = 123),
        )
        val previous = media(alliance = "字幕组A", subtitleLanguages = listOf("CHS"))
        val target = media(alliance = "字幕组B", subtitleLanguages = listOf("CHS"), kind = MediaSourceKind.WEB)
        mediaApi.addMedia(previous, target)
        assertTrue(selector.select(previous))

        val collected = selector.collectEvents {
            assertTrue(selector.select(target))
        }

        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnPreferWebSource::class,
            OnSelect::class,
        )
        val expectedEvent = SelectEvent(
            media = target,
            subtitleLanguageId = null,
            previousMedia = previous,
        )
        assertEquals(expectedEvent, collected.onBeforeSelect.single().event)
        assertEquals(expectedEvent, collected.onSelect.single().event)
        assertEquals(
            PreferWebSourceEvent(subjectId = 123, mediaSourceId = target.mediaSourceId),
            collected.onPreferWebSource.single().event,
        )
        // PINNED: SEL-02 第 (2) 步 —— selected 在 onBeforeSelect 之后, 广播偏好之前就已经切到 candidate
        assertEquals(previous, collected.onBeforeSelect.single().selectedAtEmit)
        assertEquals(target, collected.onChangePreference.single().selectedAtEmit)
        assertEquals(target, collected.onPreferWebSource.single().selectedAtEmit)
        assertEquals(target, collected.onSelect.single().selectedAtEmit)
    }

    @Test
    fun `SEL-02 select WEB media 副作用顺序为 onBeforeSelect onChangePreference onPreferWebSource onSelect`() =
        runSimpleMediaSelectorTestSuite {
            checkSelectWebMediaSideEffectOrder()
        }

    /**
     * 生产默认 `enableCaching = true` (MediaSelector.kt 的构造器默认值), 本族其余用例都跑在 false 下.
     * 这里补一次 true 的变体: caching 只影响上游计算的时序, 不应改变 SEL-02 的副作用顺序与 selectedAtEmit.
     */
    @Test
    fun `SEL-02 cachingEnabled 时 select WEB media 副作用顺序不变`() =
        runSimpleMediaSelectorTestSuite(cachingEnabled = true) {
            checkSelectWebMediaSideEffectOrder()
        }

    @Test
    fun `SEL-02 select BT media 不发 onPreferWebSource`() = runSimpleMediaSelectorTestSuite {
        val previous = media(alliance = "字幕组A", subtitleLanguages = listOf("CHS"))
        val target = media(alliance = "字幕组B", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(previous, target)
        assertTrue(selector.select(previous))

        val collected = selector.collectEvents {
            assertTrue(selector.select(target))
        }

        collected.assertOrder(
            OnBeforeSelect::class,
            OnChangePreference::class,
            OnSelect::class,
        )
        val expectedEvent = SelectEvent(
            media = target,
            subtitleLanguageId = null,
            previousMedia = previous,
        )
        assertEquals(expectedEvent, collected.onBeforeSelect.single().event)
        assertEquals(expectedEvent, collected.onSelect.single().event)
        // PINNED: SEL-02 第 (2) 步 —— selected 在 onBeforeSelect 之后, 广播偏好之前就已经切到 candidate
        assertEquals(previous, collected.onBeforeSelect.single().selectedAtEmit)
        assertEquals(target, collected.onChangePreference.single().selectedAtEmit)
        assertEquals(target, collected.onSelect.single().selectedAtEmit)
    }

    @Test
    fun `EVT-01 重复 select 同一 media 返回 false 且零事件`() = runSimpleMediaSelectorTestSuite {
        val target = media(alliance = "字幕组", subtitleLanguages = listOf("CHS"))
        mediaApi.addMedia(target)

        selector.collectEvents {
            assertTrue(selector.select(target))
        }.run {
            assertEquals(1, onBeforeSelect.size)
            assertEquals(1, onSelect.size)
        }

        // PINNED: EVT-01 重复 select 不发任何事件 (包括 onBeforeSelect), 与 onBeforeSelect KDoc 矛盾
        selector.collectEvents {
            assertFalse(selector.select(target))
        }.expectNoEvents()
    }
}
