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
import me.him188.ani.client.models.AniCollectionType
import me.him188.ani.client.models.AniSubjectRelationGraph
import me.him188.ani.client.models.AniSubjectRelationGraphNodeRole
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubjectRelationGraphMappingTest {
    private fun decode(json: String) = Json.decodeFromString(AniSubjectRelationGraph.serializer(), json)

    /**
     * 服务器对 "某科学的超电磁炮S OVA" 的真实响应 (省略了 edges 和图片)
     */
    private val railgun = decode(
        """
{"subjectId": 97197, "mainline": [2585, 51928, 262940, 537743], "truncated": false, "edges": [], "nodes": [
    {"id": 2585, "name": "とある科学の超電磁砲", "nameCn": "某科学的超电磁炮", "imageLarge": "", "airDate": "2009-10-02", "platform": 1, "episodeCount": 24, "compilation": false, "role": "MAIN"},
    {"id": 51928, "name": "とある科学の超電磁砲S", "nameCn": "某科学的超电磁炮S", "imageLarge": "", "airDate": "2013-04-12", "platform": 1, "episodeCount": 24, "compilation": false, "role": "MAIN"},
    {"id": 262940, "name": "とある科学の超電磁砲T", "nameCn": "某科学的超电磁炮T", "imageLarge": "", "airDate": "2020-01-10", "platform": 1, "episodeCount": 25, "compilation": false, "role": "MAIN"},
    {"id": 537743, "name": "とある科学の超電磁砲 第4期", "nameCn": "某科学的超电磁炮 第四季", "imageLarge": "", "airDate": "", "platform": 1, "episodeCount": 0, "compilation": false, "role": "MAIN"},
    {"id": 1014, "name": "とある魔術の禁書目録", "nameCn": "魔法禁书目录", "imageLarge": "", "airDate": "2008-10-04", "platform": 1, "episodeCount": 24, "compilation": false, "role": "SIDE", "attachTo": 2585, "relation": 12},
    {"id": 98371, "name": "とある科学の超電磁砲 炎天下の撮影モデルも楽じゃありませんわね", "nameCn": "某科学的超电磁炮 OVA", "imageLarge": "", "airDate": "2010-10-29", "platform": 2, "episodeCount": 1, "compilation": false, "role": "SIDE", "attachTo": 2585, "relation": 6},
    {"id": 97197, "name": "とある科学の超電磁砲S 大事なことはぜんぶ銭湯に教わった", "nameCn": "某科学的超电磁炮S OVA", "imageLarge": "", "airDate": "2014-03-27", "platform": 2, "episodeCount": 1, "compilation": false, "role": "SIDE", "attachTo": 51928, "relation": 6}
]}
        """.trimIndent(),
    )

    /**
     * 服务器对 "鬼灭之刃 游郭篇" 的响应 (省略了 edges 和图片). 前传/续集链上的总集篇已由服务器挂到前面最近的正片下
     */
    private val kimetsu = decode(
        """
{"subjectId": 328195, "mainline": [245665, 291494, 350764, 328195, 369768, 441939, 501958, 501960, 501961], "truncated": false, "edges": [], "nodes": [
    {"id": 245665, "name": "鬼滅の刃", "nameCn": "鬼灭之刃", "imageLarge": "", "airDate": "2019-04-06", "platform": 1, "episodeCount": 26, "compilation": false, "role": "MAIN"},
    {"id": 291494, "name": "劇場版 鬼滅の刃 無限列車編", "nameCn": "剧场版 鬼灭之刃 无限列车篇", "imageLarge": "", "airDate": "2020-10-16", "platform": 3, "episodeCount": 1, "compilation": false, "role": "MAIN_MINOR"},
    {"id": 350764, "name": "鬼滅の刃 無限列車編", "nameCn": "鬼灭之刃 无限列车篇", "imageLarge": "", "airDate": "2021-10-10", "platform": 1, "episodeCount": 7, "compilation": false, "role": "MAIN_MINOR"},
    {"id": 328195, "name": "鬼滅の刃 遊郭編", "nameCn": "鬼灭之刃 游郭篇", "imageLarge": "", "airDate": "2021-12-05", "platform": 1, "episodeCount": 11, "compilation": false, "role": "MAIN"},
    {"id": 369768, "name": "鬼滅の刃 刀鍛冶の里編", "nameCn": "鬼灭之刃 刀匠村篇", "imageLarge": "", "airDate": "2023-04-09", "platform": 1, "episodeCount": 11, "compilation": false, "role": "MAIN"},
    {"id": 441939, "name": "鬼滅の刃 柱稽古編", "nameCn": "鬼灭之刃 柱训练篇", "imageLarge": "", "airDate": "2024-05-12", "platform": 1, "episodeCount": 8, "compilation": false, "role": "MAIN"},
    {"id": 501958, "name": "劇場版 鬼滅の刃 無限城編 第一章 猗窩座再来", "nameCn": "剧场版 鬼灭之刃 无限城篇 第一章 猗窝座再袭", "imageLarge": "", "airDate": "2025-07-18", "platform": 3, "episodeCount": 1, "compilation": false, "role": "MAIN_MINOR"},
    {"id": 501960, "name": "劇場版 鬼滅の刃 無限城編 第二部", "nameCn": "剧场版 鬼灭之刃 无限城篇 第二部", "imageLarge": "", "airDate": "", "platform": 3, "episodeCount": 1, "compilation": false, "role": "MAIN_MINOR"},
    {"id": 501961, "name": "劇場版 鬼滅の刃 無限城編 第三部", "nameCn": "剧场版 鬼灭之刃 无限城篇 第三部", "imageLarge": "", "airDate": "", "platform": 3, "episodeCount": 1, "compilation": false, "role": "MAIN_MINOR"},
    {"id": 294137, "name": "鬼滅の刃 兄妹の絆", "nameCn": "鬼灭之刃 兄妹的羁绊", "imageLarge": "", "airDate": "2019-03-29", "platform": 3, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 245665, "relation": 4},
    {"id": 317002, "name": "鬼滅の刃 那田蜘蛛山編", "nameCn": "鬼灭之刃 那田蜘蛛山篇", "imageLarge": "", "airDate": "2020-10-17", "platform": 1, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 245665, "relation": 4},
    {"id": 322102, "name": "鬼滅の刃 柱合会議・蝶屋敷編", "nameCn": "鬼灭之刃 柱合会议・蝶屋敷篇", "imageLarge": "", "airDate": "2020-12-20", "platform": 1, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 245665, "relation": 4},
    {"id": 349032, "name": "鬼滅の刃 浅草編", "nameCn": "鬼灭之刃 浅草篇", "imageLarge": "", "airDate": "2021-09-12", "platform": 1, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 245665, "relation": 4},
    {"id": 349033, "name": "鬼滅の刃 鼓屋敷編", "nameCn": "鬼灭之刃 鼓屋敷篇", "imageLarge": "", "airDate": "2021-09-18", "platform": 1, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 245665, "relation": 4},
    {"id": 410499, "name": "鬼滅の刃 上弦集結、そして刀鍛冶の里へ", "nameCn": "鬼灭之刃 上弦集结、前往锻刀村", "imageLarge": "", "airDate": "2023-02-03", "platform": 3, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 328195, "relation": 4},
    {"id": 422759, "name": "鬼滅の刃 遊郭編 特別編集版", "nameCn": "鬼灭之刃 游郭篇 特別编集版", "imageLarge": "", "airDate": "2023-04-01", "platform": 1, "episodeCount": 2, "compilation": true, "role": "SIDE", "attachTo": 328195, "relation": 4},
    {"id": 469668, "name": "鬼滅の刃 絆の奇跡、そして柱稽古へ", "nameCn": "鬼灭之刃 绊之奇迹，然后与柱训练", "imageLarge": "", "airDate": "2024-02-02", "platform": 3, "episodeCount": 1, "compilation": true, "role": "SIDE", "attachTo": 369768, "relation": 4},
    {"id": 484412, "name": "鬼滅の刃 刀鍛冶の里編 特別編集版", "nameCn": "鬼灭之刃 刀匠村篇 特別编集版", "imageLarge": "", "airDate": "2024-05-04", "platform": 1, "episodeCount": 2, "compilation": true, "role": "SIDE", "attachTo": 369768, "relation": 4},
    {"id": 569579, "name": "鬼滅の刃 柱稽古編 特別編集版", "nameCn": "鬼灭之刃 柱训练篇 特別编集版", "imageLarge": "", "airDate": "2025-07-16", "platform": 1, "episodeCount": 2, "compilation": true, "role": "SIDE", "attachTo": 441939, "relation": 4}
]}
        """.trimIndent(),
    )

    /**
     * 服务器对 "进击的巨人 最终季 Part.2" 的响应的主线部分 (省略了分支, edges 和图片). 完结篇前后篇是 1 话的 TV 特别篇
     */
    private val aot = decode(
        """
{"subjectId": 331752, "mainline": [55770, 118335, 217300, 263750, 285666, 331752, 376739, 415779], "truncated": false, "edges": [], "nodes": [
    {"id": 55770, "name": "進撃の巨人", "nameCn": "进击的巨人", "imageLarge": "", "airDate": "2013-04-06", "platform": 1, "episodeCount": 25, "compilation": false, "role": "MAIN"},
    {"id": 118335, "name": "進撃の巨人 Season 2", "nameCn": "进击的巨人 第二季", "imageLarge": "", "airDate": "2017-04-01", "platform": 1, "episodeCount": 12, "compilation": false, "role": "MAIN"},
    {"id": 217300, "name": "進撃の巨人 Season 3", "nameCn": "进击的巨人 第三季", "imageLarge": "", "airDate": "2018-07-22", "platform": 1, "episodeCount": 12, "compilation": false, "role": "MAIN"},
    {"id": 263750, "name": "進撃の巨人 Season 3 Part.2", "nameCn": "进击的巨人 第三季 Part.2", "imageLarge": "", "airDate": "2019-04-28", "platform": 1, "episodeCount": 10, "compilation": false, "role": "MAIN"},
    {"id": 285666, "name": "進撃の巨人 The Final Season", "nameCn": "进击的巨人 最终季", "imageLarge": "", "airDate": "2020-12-06", "platform": 1, "episodeCount": 16, "compilation": false, "role": "MAIN"},
    {"id": 331752, "name": "進撃の巨人 The Final Season Part.2", "nameCn": "进击的巨人 最终季 Part.2", "imageLarge": "", "airDate": "2022-01-09", "platform": 1, "episodeCount": 12, "compilation": false, "role": "MAIN"},
    {"id": 376739, "name": "進撃の巨人 The Final Season 完結編 前編", "nameCn": "进击的巨人 最终季 完结篇 前篇", "imageLarge": "", "airDate": "2023-03-03", "platform": 1, "episodeCount": 1, "compilation": false, "role": "MAIN_MINOR"},
    {"id": 415779, "name": "進撃の巨人 The Final Season 完結編 後編", "nameCn": "进击的巨人 最终季 完结篇 后篇", "imageLarge": "", "airDate": "2023-11-04", "platform": 1, "episodeCount": 1, "compilation": false, "role": "MAIN_MINOR"}
]}
        """.trimIndent(),
    )

    @Test
    fun `groups branches under their main node`() {
        val graph = railgun.toSubjectRelationGraph(emptyMap())
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
        val graph = railgun.toSubjectRelationGraph(mapOf(2585 to UnifiedCollectionType.DONE))
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

    @Test
    fun `collection types from server are used for subjects without local overrides`() {
        val graph = railgun.copy(
            nodes = railgun.nodes.map {
                when (it.id) {
                    2585L -> it.copy(collectionType = AniCollectionType.DONE)
                    51928L -> it.copy(collectionType = AniCollectionType.DONE)
                    98371L -> it.copy(collectionType = AniCollectionType.WISH)
                    else -> it
                }
            },
        ).toSubjectRelationGraph(
            mapOf(
                51928 to UnifiedCollectionType.DOING,
                98371 to UnifiedCollectionType.NOT_COLLECTED, // 请求之后在本地取消了收藏
            ),
        )
        assertEquals(
            listOf(
                UnifiedCollectionType.DONE,
                UnifiedCollectionType.DOING,
                UnifiedCollectionType.NOT_COLLECTED,
                UnifiedCollectionType.NOT_COLLECTED,
            ),
            graph.mainline.map { it.subject.collectionType },
        )
        assertEquals(
            listOf(UnifiedCollectionType.NOT_COLLECTED, UnifiedCollectionType.NOT_COLLECTED),
            graph.mainline[0].branches.map { it.subject.collectionType },
        )
    }

    @Test
    fun `mainline nodes other than seasons are minor`() {
        val graph = kimetsu.toSubjectRelationGraph(emptyMap())
        assertEquals(
            listOf(
                245665 to false, // 鬼灭之刃
                291494 to true, // 剧场版 无限列车篇
                350764 to true, // 无限列车篇 TV 版, 7 话
                328195 to false, // 游郭篇
                369768 to false, // 刀匠村篇
                441939 to false, // 柱训练篇
                501958 to true, // 剧场版 无限城篇 第一章
                501960 to true,
                501961 to true,
            ),
            graph.mainline.map { it.subject.subjectId to it.isMinor },
        )
    }

    @Test
    fun `branches keep the server's grouping and order`() {
        val graph = kimetsu.toSubjectRelationGraph(emptyMap())
        assertEquals(
            listOf(294137, 317002, 322102, 349032, 349033),
            graph.mainline[0].branches.map { it.subject.subjectId },
        )
        assertEquals(emptyList(), graph.mainline[1].branches)
        assertEquals(
            listOf(410499 to SubjectRelation.COMPILATION, 422759 to SubjectRelation.COMPILATION),
            graph.mainline[3].branches.map { it.subject.subjectId to it.relation },
        )
        assertEquals(19, graph.mainCount + graph.branchCount)
    }

    @Test
    fun `tv specials on the sequel chain are minor mainline nodes`() {
        val graph = aot.toSubjectRelationGraph(emptyMap())
        assertEquals(aot.mainline.map { it.toInt() }, graph.mainline.map { it.subject.subjectId })
        // 完结篇前后篇不计入 "第几部"
        assertEquals(listOf(376739, 415779), graph.mainline.filter { it.isMinor }.map { it.subject.subjectId })
        assertEquals(0, graph.branchCount)
    }

    @Test
    fun `series without seasons keeps every subject on the mainline`() {
        val movies = kimetsu.copy(
            mainline = listOf(501958, 501960, 501961),
            nodes = kimetsu.nodes.filter { it.id in listOf(501958L, 501960L, 501961L) }
                .map { it.copy(role = AniSubjectRelationGraphNodeRole.MAIN) },
        ).toSubjectRelationGraph(emptyMap())
        assertEquals(listOf(501958, 501960, 501961), movies.mainline.map { it.subject.subjectId })
        assertFalse(movies.mainline.any { it.isMinor })
    }
}
