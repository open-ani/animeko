/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

import kotlin.test.Test
import kotlin.test.assertEquals

class RelatedPersonInfoTest {
    private fun info(index: Int, personId: Int, position: PersonPosition) = RelatedPersonInfo(
        index = index,
        personInfo = PersonInfo(
            id = personId,
            name = "person$personId",
            type = PersonType.Individual,
            careers = emptyList(),
            imageLarge = "",
            imageMedium = "",
            summary = "",
            locked = null,
        ),
        position = position,
    )

    @Test
    fun `重要职位靠前 其余按index排序`() {
        // 导演兼分镜、演出、原画: 导演行排在前面, 其余职位按服务端顺序排在不重要的尾部
        val sorted = listOf(
            info(index = 67, personId = 100, position = PersonPosition.KeyAnimation),
            info(index = 16, personId = 100, position = PersonPosition.EpisodeDirector),
            info(index = 8, personId = 100, position = PersonPosition.Storyboard),
            info(index = 1, personId = 100, position = PersonPosition.Director),
            info(index = 0, personId = 200, position = PersonPosition.OriginalWork),
        ).sortedWith(RelatedPersonInfo.ImportanceOrder)

        assertEquals(
            listOf(
                PersonPosition.OriginalWork,
                PersonPosition.Director,
                PersonPosition.Storyboard,
                PersonPosition.EpisodeDirector,
                PersonPosition.KeyAnimation,
            ),
            sorted.map { it.position },
        )
    }
}
