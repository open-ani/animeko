/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.subject

import kotlinx.serialization.json.Json
import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.SubjectRelationGraphPlatform
import me.him188.ani.client.models.AniSubjectRelationGraph
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubjectRelationGraphMappingTest {
    /**
     * 服务器对 "某科学的超电磁炮S OVA" 的真实响应
     */
    private val response = Json.decodeFromString(
        AniSubjectRelationGraph.serializer(),
        """
{
  "subjectId": 97197,
  "nodes": [
    {
      "id": 2585,
      "name": "とある科学の超電磁砲",
      "nameCn": "某科学的超电磁炮",
      "imageLarge": "",
      "airDate": "2009-10-02",
      "platform": 1,
      "episodeCount": 24,
      "role": "MAIN"
    },
    {
      "id": 51928,
      "name": "とある科学の超電磁砲S",
      "nameCn": "某科学的超电磁炮S",
      "imageLarge": "",
      "airDate": "2013-04-12",
      "platform": 1,
      "episodeCount": 24,
      "role": "MAIN"
    },
    {
      "id": 262940,
      "name": "とある科学の超電磁砲T",
      "nameCn": "某科学的超电磁炮T",
      "imageLarge": "",
      "airDate": "2020-01-10",
      "platform": 1,
      "episodeCount": 25,
      "role": "MAIN"
    },
    {
      "id": 537743,
      "name": "とある科学の超電磁砲 第4期",
      "nameCn": "某科学的超电磁炮 第四季",
      "imageLarge": "",
      "airDate": "",
      "platform": 1,
      "episodeCount": 0,
      "role": "MAIN"
    },
    {
      "id": 1014,
      "name": "とある魔術の禁書目録",
      "nameCn": "魔法禁书目录",
      "imageLarge": "",
      "airDate": "2008-10-04",
      "platform": 1,
      "episodeCount": 24,
      "role": "SIDE",
      "attachTo": 2585,
      "relation": 12
    },
    {
      "id": 98371,
      "name": "とある科学の超電磁砲 炎天下の撮影モデルも楽じゃありませんわね",
      "nameCn": "某科学的超电磁炮 OVA",
      "imageLarge": "",
      "airDate": "2010-10-29",
      "platform": 2,
      "episodeCount": 1,
      "role": "SIDE",
      "attachTo": 2585,
      "relation": 6
    },
    {
      "id": 97197,
      "name": "とある科学の超電磁砲S 大事なことはぜんぶ銭湯に教わった",
      "nameCn": "某科学的超电磁炮S OVA",
      "imageLarge": "",
      "airDate": "2014-03-27",
      "platform": 2,
      "episodeCount": 1,
      "role": "SIDE",
      "attachTo": 51928,
      "relation": 6
    }
  ],
  "edges": [
    {
      "from": 1014,
      "to": 2585,
      "relation": 11
    },
    {
      "from": 2585,
      "to": 51928,
      "relation": 3
    },
    {
      "from": 2585,
      "to": 98371,
      "relation": 6
    },
    {
      "from": 51928,
      "to": 97197,
      "relation": 6
    },
    {
      "from": 51928,
      "to": 262940,
      "relation": 3
    },
    {
      "from": 262940,
      "to": 537743,
      "relation": 3
    }
  ],
  "mainline": [
    2585,
    51928,
    262940,
    537743
  ],
  "truncated": false
}
        """.trimIndent(),
    )

    @Test
    fun `groups branches under their main node`() {
        val graph = response.toSubjectRelationGraph(emptyMap())
        assertEquals(97197, graph.subjectId)
        assertEquals(listOf(2585, 51928, 262940, 537743), graph.mainline.map { it.subject.subjectId })
        assertFalse(graph.mainline.any { it.isMinor })
        assertEquals(4, graph.mainCount)
        assertEquals(3, graph.branchCount)

        val first = graph.mainline[0]
        assertEquals(listOf(1014, 98371), first.branches.map { it.subject.subjectId })
        assertEquals(listOf(SubjectRelation.MAIN_STORY, SubjectRelation.SPECIAL), first.branches.map { it.relation })
        assertEquals(listOf(97197), graph.mainline[1].branches.map { it.subject.subjectId })
        assertEquals(emptyList(), graph.mainline[2].branches)
    }

    @Test
    fun `maps subject fields`() {
        val graph = response.toSubjectRelationGraph(mapOf(2585 to UnifiedCollectionType.DONE))
        val first = graph.mainline[0].subject
        assertEquals("某科学的超电磁炮", first.displayName)
        assertEquals(PackedDate(2009, 10, 2), first.airDate)
        assertEquals(SubjectRelationGraphPlatform.TV, first.platform)
        assertEquals(24, first.episodeCount)
        assertEquals(UnifiedCollectionType.DONE, first.collectionType)

        val ova = graph.mainline[1].branches.single().subject
        assertEquals(SubjectRelationGraphPlatform.OVA, ova.platform)
        assertEquals(UnifiedCollectionType.NOT_COLLECTED, ova.collectionType)

        // 尚未公布放送日期
        assertEquals(PackedDate.Invalid, graph.mainline[3].subject.airDate)
    }
}
