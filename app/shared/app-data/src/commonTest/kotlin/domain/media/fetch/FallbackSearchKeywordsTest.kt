/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.fetch

import kotlin.test.Test
import kotlin.test.assertEquals

class FallbackSearchKeywordsTest {
    @Test
    fun `season alias comes before series base names`() {
        assertEquals(
            listOf("出包王女 第二季", "出包王女", "To LOVEる -とらぶる-"),
            computeFallbackSearchKeywords(
                subjectNames = listOf(
                    "更多 出包王女",
                    "もっとTo LOVEる -とらぶる-",
                    "Motto To Love-Ru: Trouble",
                    "出包王女 第二季",
                ),
                seriesSubjectNames = setOf("To LOVEる -とらぶる-", "出包王女"),
            ),
        )
    }

    @Test
    fun `longer base name is more specific and comes first`() {
        assertEquals(
            listOf("出包王女Darkness 第二季", "出包王女Darkness", "出包王女"),
            computeFallbackSearchKeywords(
                subjectNames = listOf("出包王女Darkness 第二季"),
                seriesSubjectNames = setOf("出包王女", "更多 出包王女", "出包王女Darkness"),
            ),
        )
    }

    @Test
    fun `renamed sequel has no base name`() {
        assertEquals(
            emptyList(),
            computeFallbackSearchKeywords(
                subjectNames = listOf("伪物语"),
                seriesSubjectNames = setOf("化物语", "猫物语 黑"),
            ),
        )
    }

    @Test
    fun `first season has nothing to fall back to`() {
        assertEquals(
            emptyList(),
            computeFallbackSearchKeywords(
                subjectNames = listOf("出包王女", "To LOVEる -とらぶる-"),
                seriesSubjectNames = setOf("更多 出包王女", "出包王女Darkness"),
            ),
        )
    }

    @Test
    fun `series name differing only by special characters is not a base name`() {
        assertEquals(
            emptyList(),
            computeFallbackSearchKeywords(
                subjectNames = listOf("轻音少女"),
                seriesSubjectNames = setOf("轻音少女~"),
            ),
        )
    }

    @Test
    fun `single character series name is not a base name`() {
        assertEquals(
            emptyList(),
            computeFallbackSearchKeywords(
                subjectNames = listOf("K RETURN OF KINGS"),
                seriesSubjectNames = setOf("K"),
            ),
        )
    }

    @Test
    fun `season markers in various forms`() {
        assertEquals(
            listOf("某科学的超电磁炮 第2期", "某科学的超电磁炮 第三季"),
            computeFallbackSearchKeywords(
                subjectNames = listOf("某科学的超电磁炮T", "某科学的超电磁炮 第2期", "某科学的超电磁炮 第三季", "A Certain Scientific Railgun T"),
                seriesSubjectNames = emptySet(),
            ),
        )
    }
}
