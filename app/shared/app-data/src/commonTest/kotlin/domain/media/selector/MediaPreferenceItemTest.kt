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
import me.him188.ani.app.data.models.preference.MediaPreference
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_DMHY
import me.him188.ani.app.domain.media.selector.testFramework.MediaSelectorTestSuite.Companion.SOURCE_MIKAN
import me.him188.ani.app.domain.media.selector.testFramework.runSimpleMediaSelectorTestSuite
import me.him188.ani.test.TestContainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@TestContainer
class MediaPreferenceItemTest {
    @Test
    fun `ITEM-02 会话 override 屏蔽数据库后续更新`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "字幕组A")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty

        assertEquals("字幕组A", selector.alliance.finalSelected.first())

        selector.alliance.prefer("X")
        assertEquals("X", selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Y")
        assertEquals("X", selector.alliance.finalSelected.first())
    }

    @Test
    fun `ITEM-02 removePreference 后数据库更新与全局默认值同时被屏蔽`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "字幕组A")
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(alliance = "默认组")

        selector.alliance.removePreference()
        assertNull(selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Y")
        assertEquals("默认组", selector.alliance.defaultSelected.first())
        assertNull(selector.alliance.finalSelected.first())
    }

    @Test
    fun `ITEM-02 无 override 时数据库更新跟随生效`() = runSimpleMediaSelectorTestSuite {
        preferenceApi.savedUserPreference.value = MediaPreference.Empty
        preferenceApi.savedDefaultPreference.value = MediaPreference.Empty.copy(alliance = "默认组")

        assertEquals("默认组", selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Y")
        assertEquals("Y", selector.alliance.finalSelected.first())

        preferenceApi.savedUserPreference.value = MediaPreference.Empty.copy(alliance = "Z")
        assertEquals("Z", selector.alliance.finalSelected.first())
    }

    @Test
    fun `ITEM-03 alliance 与 resolution 的 available 去重后按字典序升序`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(alliance = "b组", resolution = "720P"),
            media(alliance = "A组", resolution = "1080P"),
            media(alliance = "B组", resolution = "2160P"),
            media(alliance = "A组", resolution = "1080P"),
        )

        assertEquals(listOf("A组", "B组", "b组"), selector.alliance.available.first())
        assertEquals(listOf("1080P", "2160P", "720P"), selector.resolution.available.first())
    }

    @Test
    fun `ITEM-04 subtitleLanguageId 的 available 排序表无效仅集合稳定`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(subtitleLanguages = listOf("CHS", "JPN")),
            media(subtitleLanguages = listOf("CHT", "CHS")),
            media(subtitleLanguages = listOf("ENG")),
        )

        val available = selector.subtitleLanguageId.available.first()
        // PINNED: ITEM-04 排序权重表误用分辨率字样(8K/4320P/4K/1080P...), 字幕语言 ID 全部落入 else -> -1,
        // 排序实际无效, 顺序 = HashSet 迭代序(不稳定), 故只断言集合相等不断言顺序.
        // 本例只作 flatMap 去重后的集合完整性兜底; 顺序表本身由下面的
        // `ITEM-04 排序权重表命中的是分辨率字样` 用例确定性地钉住.
        assertEquals(4, available.size)
        assertEquals(setOf("CHS", "CHT", "JPN", "ENG"), available.toSet())
    }

    @Test
    fun `ITEM-04 排序权重表命中的是分辨率字样`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(subtitleLanguages = listOf("CHS")),
            media(subtitleLanguages = listOf("720P")),
        )

        // PINNED: ITEM-04 (已知 bug) 权重表按分辨率字样打分, 所以字幕语言位上写 "720P" 反而拿到权重 2,
        // 真正的语言 ID "CHS" 落入 else -> -1. sortedByDescending 下输出必然是 ["720P", "CHS"]:
        // 两个权重不并列, 结果与 HashSet 迭代序无关, 是确定的.
        // 修复意图: 换成语言优先级表后 "720P" 会落入 else 分支, 顺序改变, 本用例立即失败,
        // 届时列入故意变更清单.
        assertEquals(listOf("720P", "CHS"), selector.subtitleLanguageId.available.first())
    }

    @Test
    fun `ITEM-05 mediaSourceId 的 available 误取分辨率集合`() = runSimpleMediaSelectorTestSuite {
        mediaApi.addMedia(
            media(sourceId = SOURCE_DMHY, resolution = "1080P"),
            media(sourceId = SOURCE_MIKAN, resolution = "720P"),
            media(sourceId = SOURCE_MIKAN, resolution = "1080P"),
        )

        // PINNED: ITEM-05 getFromMediaList 误取 it.properties.resolution 而非 it.mediaSourceId,
        // "可用数据源列表"实际是去重字典序排序后的分辨率集合, 不含任何 mediaSourceId.
        // 修复为取 mediaSourceId 时列入故意变更清单.
        assertEquals(listOf("1080P", "720P"), selector.mediaSourceId.available.first())
    }
}
