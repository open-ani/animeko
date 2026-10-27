/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.flow.first
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.data.models.subject.SubjectInfo
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnChangePreference
import me.him188.ani.app.domain.media.selector.testFramework.RecordedMediaSelectorEvent.OnPreferWebSource
import me.him188.ani.app.domain.media.selector.testFramework.collectEvents
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame

/**
 * 手动点选当前资源会记录偏好，但不会切换资源或触发播放重新加载。
 * 覆盖自动选源后首次指定偏好、失败回退后替换偏好，以及不记录偏好的临时选择。
 */
@TestContainer
class MediaSelectorCurrentPreferenceTest {
    @Test
    fun `manual selection of current automatic media remembers source without reloading`() =
        runSimpleMediaSelectorTestSuite {
            preferenceApi.savedUserPreference.value = MediaPreference.Empty
            preferenceApi.savedDefaultPreference.value = MediaPreference.Empty
            preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
                subjectInfo = SubjectInfo.Empty.copy(subjectId = 123),
            )
            val target = media(
                sourceId = "web-c",
                kind = MediaSourceKind.WEB,
                alliance = "channel-2",
                resolution = "720P",
                subtitleLanguages = listOf("CHS"),
            )
            selector.selectAutomatically(target, expectedSelection = null)

            // 自动选择不记录手动偏好；点选当前 WEB 资源只广播偏好事件，不广播选择事件。
            val events = selector.collectEvents {
                assertFalse(selector.select(target, SelectTrigger.MANUAL_AUTO_PAGE))
            }

            assertSame(target, selector.selected.value)
            events.assertOrder(OnChangePreference::class, OnPreferWebSource::class)
            assertEquals(PreferWebSourceEvent(123, "web-c"), events.onPreferWebSource.single().event)
            assertEquals("web-c", selector.mediaSourceId.finalSelected.first())
            assertEquals("channel-2", selector.alliance.finalSelected.first())
            assertEquals("720P", selector.resolution.finalSelected.first())
            assertEquals("CHS", selector.subtitleLanguageId.finalSelected.first())
        }

    @Test
    fun `manual selection of current fallback media replaces the previous source preference`() =
        runSimpleMediaSelectorTestSuite {
            preferenceApi.mediaSelectorContext.value = preferenceApi.mediaSelectorContext.value.copy(
                subjectInfo = SubjectInfo.Empty.copy(subjectId = 123),
            )
            val old = media(sourceId = "web-a", kind = MediaSourceKind.WEB)
            val current = media(sourceId = "web-c", kind = MediaSourceKind.WEB)
            selector.select(old)
            selector.selectAutomatically(
                current,
                expectedSelection = old,
                trigger = SelectTrigger.AUTO_ERROR_SWITCH,
            )

            // 自动回退保留原有手动偏好；用户确认当前资源时，偏好应指向当前源，播放保持不变。
            val events = selector.collectEvents {
                assertFalse(selector.select(current, SelectTrigger.MANUAL_AUTO_PAGE))
            }

            events.assertOrder(OnChangePreference::class, OnPreferWebSource::class)
            assertEquals("web-c", events.onChangePreference.single().preference.mediaSourceId)
            assertEquals(PreferWebSourceEvent(123, "web-c"), events.onPreferWebSource.single().event)
            assertSame(current, selector.selected.value)
        }

    @Test
    fun `repeating a temporary selection does not remember the current source`() =
        runSimpleMediaSelectorTestSuite {
            val target = media(sourceId = "web-c", kind = MediaSourceKind.WEB)
            selector.selectAutomatically(target, expectedSelection = null)
            // 临时选择不代表用户要记住偏好，重复选择仍不写偏好，也不广播任何事件。
            selector.collectEvents {
                assertFalse(selector.selectTemporarily(target))
            }.expectNoEvents()
        }
}
