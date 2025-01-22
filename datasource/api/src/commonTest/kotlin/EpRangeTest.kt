/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api

import me.him188.ani.datasources.api.topic.EpisodeRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class EpRangeTest {
    @Test
    fun testEmpty() {
        val empty = EpisodeRange.empty()
        assertTrue(empty.knownSorts.toList().isEmpty())
    }

    @Test
    fun testSingle() {
        val single = EpisodeRange.single("1")
        assertEquals(1, single.knownSorts.toList().size)
    }

    @Test
    fun testRange() {
        val range = EpisodeRange.range("1", "3")
        assertEquals(3, range.knownSorts.toList().size)
        assertEquals("[01, 02, 03]", range.knownSorts.toList().toString())
    }

    @Test
    fun `partial range end`() {
        val range = EpisodeRange.range("1", "2.5")
        assertEquals(3, range.knownSorts.toList().size)
        assertEquals("[01, 02, 2.5]", range.knownSorts.toList().toString())
    }

    @Test
    fun `partial range start`() {
        val range = EpisodeRange.range("1.5", "3")
        assertEquals(3, range.knownSorts.toList().size)
        assertEquals("[1.5, 02, 03]", range.knownSorts.toList().toString())
    }

    @Test
    fun `partial range both`() {
        val range = EpisodeRange.range("1.5", "2.5")
        assertEquals(3, range.knownSorts.toList().size)
        assertEquals("[1.5, 02, 2.5]", range.knownSorts.toList().toString())
    }

    @Test
    fun `normal and special`() {
        val range = EpisodeRange.range(EpisodeSort("SP"), EpisodeSort("2.5"))
        assertEquals(2, range.knownSorts.toList().size)
        assertEquals("[SP, 2.5]", range.knownSorts.toList().toString())
    }

    @Test
    fun `special and special`() {
        val range = EpisodeRange.range(EpisodeSort("SP"), EpisodeSort("OP"))
        assertEquals(2, range.knownSorts.toList().size)
        assertEquals("[SP, OP]", range.knownSorts.toList().toString())
    }

    @Test
    fun `combine 3 seasons`() {
        val combined = EpisodeRange.combined(
            EpisodeRange.combined(EpisodeRange.season(1), EpisodeRange.season(2)),
            EpisodeRange.season(3),
        )
        assertEquals(
            EpisodeRange.Combined(
                EpisodeRange.Combined(
                    EpisodeRange.Season(1),
                    EpisodeRange.Season(2),
                ),
                EpisodeRange.Season(3),
            ),
            combined,
        )
        assertEquals("S1+S2+S3", combined.toString())
    }

    @Test
    fun `Combined equals seasons`() {
        assertEquals(
            EpisodeRange.Combined(
                EpisodeRange.Combined(
                    EpisodeRange.Season(1),
                    EpisodeRange.Season(2),
                ),
                EpisodeRange.Season(3),
            ),
            EpisodeRange.combined(
                EpisodeRange.combined(EpisodeRange.season(1), EpisodeRange.season(2)),
                EpisodeRange.season(3),
            ),
        )
    }

    @Test
    fun `Combined equals seasons tree`() {
        assertEquals(
            EpisodeRange.Combined(
                EpisodeRange.Season(1),
                EpisodeRange.Combined(
                    EpisodeRange.Season(2),
                    EpisodeRange.Season(3),
                ),
            ),
            EpisodeRange.combined(
                EpisodeRange.combined(EpisodeRange.season(1), EpisodeRange.season(2)),
                EpisodeRange.season(3),
            ),
        )
    }

    @Test
    fun `Combined equals seasons tree nested`() {
        assertEquals(
            EpisodeRange.Combined(
                EpisodeRange.Season(1),
                EpisodeRange.Combined(
                    EpisodeRange.Combined(
                        EpisodeRange.Season(2),
                        EpisodeRange.Season(3),
                    ),
                    EpisodeRange.Season(4),
                ),
            ),
            EpisodeRange.combined(
                EpisodeRange.combined(EpisodeRange.season(1), EpisodeRange.season(2)),
                EpisodeRange.combined(EpisodeRange.season(3), EpisodeRange.season(4)),
            ),
        )
    }

    @Test
    fun `Combined tree mismatch`() {
        assertNotEquals(
            EpisodeRange.Combined(
                EpisodeRange.Season(1),
                EpisodeRange.Combined(
                    EpisodeRange.Combined(
                        EpisodeRange.Season(2),
                        EpisodeRange.Season(3),
                    ),
                    EpisodeRange.Season(4),
                ),
            ),
            EpisodeRange.combined(
                EpisodeRange.combined(EpisodeRange.season(1), EpisodeRange.season(2)),
                EpisodeRange.combined(listOf(EpisodeRange.season(3))),
            ),
        )
    }

    @Test
    fun `Combined not equals empty`() {
        assertNotEquals(
            EpisodeRange.empty(),
            EpisodeRange.combined(
                EpisodeRange.combined(EpisodeRange.season(1), EpisodeRange.season(2)),
                EpisodeRange.season(3),
            ),
        )
    }

    @Test
    fun `combine single season`() {
        assertEquals(
            EpisodeRange.season(3),
            EpisodeRange.combined(listOf(EpisodeRange.season(3))),
        )
    }

    @Test
    fun `combine single empty`() {
        assertEquals(
            EpisodeRange.empty(),
            EpisodeRange.combined(listOf(EpisodeRange.empty())),
        )
    }

    @Test
    fun `combine two empty`() {
        assertEquals(
            EpisodeRange.empty(),
            EpisodeRange.combined(listOf(EpisodeRange.empty(), EpisodeRange.empty())),
        )
    }

    @Test
    fun `combine empty iterable`() {
        assertEquals(
            EpisodeRange.empty(),
            EpisodeRange.combined(listOf()),
        )
    }

    @Test
    fun `combine same episode`() {
        assertEquals(
            EpisodeRange.single("1"),
            EpisodeRange.combined(
                EpisodeRange.single("1"),
                EpisodeRange.single("1"),
            ),
        )
    }

    @Test
    fun `combine same season`() {
        assertEquals(
            EpisodeRange.season(1),
            EpisodeRange.combined(
                EpisodeRange.season(1),
                EpisodeRange.season(1),
            ),
        )
    }
}