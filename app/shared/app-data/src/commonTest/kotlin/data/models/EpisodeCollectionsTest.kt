/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models

import me.him188.ani.app.domain.episode.EpisodeCollections
import me.him188.ani.datasources.api.PackedDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EpisodeCollectionsTest {

    //    @Test
//    @Disabled
    fun `subject completed after 365 days`() {
        assertTrue {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2024, 10, 11),
            )
        }
    }

    @Test
    fun `subject not completed within 14 days`() {
        assertFalse {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2024, 10, 9),
            )
        }
    }

    @Test
    fun `subject not completed before first start`() {
        assertFalse {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2022, 10, 11),
            )
        }
    }

    @Test
    fun `subject not completed before last start`() {
        assertFalse {
            EpisodeCollections.isSubjectCompleted(
                sequenceOf(
                    PackedDate(2023, 10, 4),
                    PackedDate(2023, 10, 11),
                ),
                now = PackedDate(2022, 10, 10),
            )
        }
    }
}