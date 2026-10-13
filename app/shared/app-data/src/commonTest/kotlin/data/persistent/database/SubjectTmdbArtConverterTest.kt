/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.persistent.database

import me.him188.ani.app.data.models.subject.SubjectTmdbArt
import me.him188.ani.app.data.models.subject.TmdbImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [SubjectTmdbArt] 在 Room 列 (BLOB, 可空) 中原样往返.
 */
class SubjectTmdbArtConverterTest {
    private val converter = ProtoConverters.SubjectTmdbArtConverter

    private fun roundTrip(art: SubjectTmdbArt?): SubjectTmdbArt? =
        converter.fromByteArray(converter.fromSubjectTmdbArt(art))

    @Test
    fun `full art survives round trip`() {
        val art = SubjectTmdbArt(
            backdrops = listOf(TmdbImage("b1-m", "b1-l"), TmdbImage("b2-m", "b2-l")),
            posters = mapOf("ja" to TmdbImage("p-m", "p-l"), "xx" to TmdbImage("px-m", "px-l")),
            logos = mapOf("zh" to TmdbImage("l-m", "l-l", vector = "l.svg")),
        )
        assertEquals(art, roundTrip(art))
        assertEquals("b1-m", roundTrip(art)?.primaryBackdrop?.medium)
    }

    @Test
    fun `empty art survives round trip`() {
        assertEquals(SubjectTmdbArt(), roundTrip(SubjectTmdbArt()))
        assertNull(roundTrip(SubjectTmdbArt())?.primaryBackdrop)
    }

    @Test
    fun `null stays null`() {
        assertNull(converter.fromSubjectTmdbArt(null))
        assertNull(roundTrip(null))
    }
}
