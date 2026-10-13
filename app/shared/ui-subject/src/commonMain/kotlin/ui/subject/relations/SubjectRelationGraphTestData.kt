/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.relations

import me.him188.ani.app.data.models.subject.SubjectRelation
import me.him188.ani.app.data.models.subject.SubjectRelationGraph
import me.him188.ani.app.data.models.subject.SubjectRelationGraphBranch
import me.him188.ani.app.data.models.subject.SubjectRelationGraphMainNode
import me.him188.ani.app.data.models.subject.SubjectRelationGraphPlatform
import me.him188.ani.app.data.models.subject.SubjectRelationGraphSubject
import me.him188.ani.datasources.api.PackedDate
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 来自 Bangumi 真实系列的关系图, 由服务器算法计算, 并按客户端的规则分组得到.
 */
@TestOnly
object TestSubjectRelationGraphs {
    /** Re:Zero, 从第二季进入. 每一季下有番外和衍生 */
    val ReZero = SubjectRelationGraph(
        subjectId = 278826,
        mainline = listOf(
            SubjectRelationGraphMainNode(
                subject(140001, "Re:ゼロから始める異世界生活", "Re：从零开始的异世界生活", PackedDate(2016, 4, 3), SubjectRelationGraphPlatform.TV, 25, UnifiedCollectionType.DONE),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(177998, "Re:ゼロから始める休憩時間", "Re：从零开始的休息时间", PackedDate(2016, 4, 8), SubjectRelationGraphPlatform.TV, 11, UnifiedCollectionType.DONE), SubjectRelation.DERIVED),
                    SubjectRelationGraphBranch(subject(185837, "Re:プチから始める異世界生活", "Re：从迷你开始的异世界生活", PackedDate(2016, 6, 24), SubjectRelationGraphPlatform.TV, 14, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                    SubjectRelationGraphBranch(subject(225462, "Re:ゼロから始める異世界生活 Memory Snow", "Re：从零开始的异世界生活 雪之回忆", PackedDate(2019, 6, 7), SubjectRelationGraphPlatform.OVA, 1, UnifiedCollectionType.DONE), SubjectRelation.SPECIAL),
                    SubjectRelationGraphBranch(subject(261805, "Re:ゼロから始める異世界生活 氷結の絆", "Re：从零开始的异世界生活 冰结之绊", PackedDate(2019, 11, 8), SubjectRelationGraphPlatform.OVA, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.SPECIAL),
                    SubjectRelationGraphBranch(subject(296195, "Re:ゼロから始める異世界生活 新編集版", "Re：从零开始的异世界生活 新编集版", PackedDate(2020, 1, 1), SubjectRelationGraphPlatform.TV, 25, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(278826, "Re:ゼロから始める異世界生活 2nd season", "Re：从零开始的异世界生活 第二季", PackedDate(2020, 7, 8), SubjectRelationGraphPlatform.TV, 13, UnifiedCollectionType.DOING),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(310194, "Re:ゼロから始める休憩時間 2nd Season", "Re：从零开始的休息时间2", PackedDate(2020, 7, 10), SubjectRelationGraphPlatform.WEB, 25, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(316247, "Re:ゼロから始める異世界生活 2nd season 後半クール", "Re：从零开始的异世界生活 第二季 后半部分", PackedDate(2021, 1, 6), SubjectRelationGraphPlatform.TV, 12, UnifiedCollectionType.WISH),
                isMinor = false,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(425998, "Re:ゼロから始める異世界生活 3rd season 襲擊編", "Re：从零开始的异世界生活 第三季 袭击篇", PackedDate(2024, 10, 2), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(516311, "Re:ゼロから始める休憩時間 3rd season", "Re：从零开始的休息时间3", PackedDate(2024, 10, 2), SubjectRelationGraphPlatform.WEB, 16, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(510728, "Re:ゼロから始める異世界生活 3rd season 反擊編", "Re：从零开始的异世界生活 第三季 反击篇", PackedDate(2025, 2, 5), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(547888, "Re:ゼロから始める異世界生活 4th season 喪失編", "Re：从零开始的异世界生活 第四季 丧失篇", PackedDate(2026, 4, 8), SubjectRelationGraphPlatform.TV, 11, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(638494, "Re:ゼロから始める休憩時間 4th season", "Re：从零开始的休息时间4", PackedDate(2026, 4, 8), SubjectRelationGraphPlatform.WEB, 0, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(633836, "Re:ゼロから始める異世界生活 4th season 奪還編", "Re：从零开始的异世界生活 第四季 夺还篇", PackedDate(2026, 8, 12), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = emptyList(),
            ),
        ),
        truncated = false,
    )

    /** Re:Zero, 从 OVA 进入: 当前条目在第一季的相关条目中 */
    val ReZeroFromOva = SubjectRelationGraph(
        subjectId = 225462,
        mainline = listOf(
            SubjectRelationGraphMainNode(
                subject(140001, "Re:ゼロから始める異世界生活", "Re：从零开始的异世界生活", PackedDate(2016, 4, 3), SubjectRelationGraphPlatform.TV, 25, UnifiedCollectionType.DONE),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(177998, "Re:ゼロから始める休憩時間", "Re：从零开始的休息时间", PackedDate(2016, 4, 8), SubjectRelationGraphPlatform.TV, 11, UnifiedCollectionType.DONE), SubjectRelation.DERIVED),
                    SubjectRelationGraphBranch(subject(185837, "Re:プチから始める異世界生活", "Re：从迷你开始的异世界生活", PackedDate(2016, 6, 24), SubjectRelationGraphPlatform.TV, 14, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                    SubjectRelationGraphBranch(subject(225462, "Re:ゼロから始める異世界生活 Memory Snow", "Re：从零开始的异世界生活 雪之回忆", PackedDate(2019, 6, 7), SubjectRelationGraphPlatform.OVA, 1, UnifiedCollectionType.DONE), SubjectRelation.SPECIAL),
                    SubjectRelationGraphBranch(subject(261805, "Re:ゼロから始める異世界生活 氷結の絆", "Re：从零开始的异世界生活 冰结之绊", PackedDate(2019, 11, 8), SubjectRelationGraphPlatform.OVA, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.SPECIAL),
                    SubjectRelationGraphBranch(subject(296195, "Re:ゼロから始める異世界生活 新編集版", "Re：从零开始的异世界生活 新编集版", PackedDate(2020, 1, 1), SubjectRelationGraphPlatform.TV, 25, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(278826, "Re:ゼロから始める異世界生活 2nd season", "Re：从零开始的异世界生活 第二季", PackedDate(2020, 7, 8), SubjectRelationGraphPlatform.TV, 13, UnifiedCollectionType.DOING),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(310194, "Re:ゼロから始める休憩時間 2nd Season", "Re：从零开始的休息时间2", PackedDate(2020, 7, 10), SubjectRelationGraphPlatform.WEB, 25, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(316247, "Re:ゼロから始める異世界生活 2nd season 後半クール", "Re：从零开始的异世界生活 第二季 后半部分", PackedDate(2021, 1, 6), SubjectRelationGraphPlatform.TV, 12, UnifiedCollectionType.WISH),
                isMinor = false,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(425998, "Re:ゼロから始める異世界生活 3rd season 襲擊編", "Re：从零开始的异世界生活 第三季 袭击篇", PackedDate(2024, 10, 2), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(516311, "Re:ゼロから始める休憩時間 3rd season", "Re：从零开始的休息时间3", PackedDate(2024, 10, 2), SubjectRelationGraphPlatform.WEB, 16, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(510728, "Re:ゼロから始める異世界生活 3rd season 反擊編", "Re：从零开始的异世界生活 第三季 反击篇", PackedDate(2025, 2, 5), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(547888, "Re:ゼロから始める異世界生活 4th season 喪失編", "Re：从零开始的异世界生活 第四季 丧失篇", PackedDate(2026, 4, 8), SubjectRelationGraphPlatform.TV, 11, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(638494, "Re:ゼロから始める休憩時間 4th season", "Re：从零开始的休息时间4", PackedDate(2026, 4, 8), SubjectRelationGraphPlatform.WEB, 0, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.DERIVED),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(633836, "Re:ゼロから始める異世界生活 4th season 奪還編", "Re：从零开始的异世界生活 第四季 夺还篇", PackedDate(2026, 8, 12), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = emptyList(),
            ),
        ),
        truncated = false,
    )

    /** 鬼灭之刃: 剧场版和 7 话的 "无限列车篇" TV 版在主线上, 总集篇列在正片下. 第一部之前的 "兄妹的羁绊" 是剧场版形式的总集篇 */
    val Kimetsu = SubjectRelationGraph(
        subjectId = 328195,
        mainline = listOf(
            SubjectRelationGraphMainNode(
                subject(245665, "鬼滅の刃", "鬼灭之刃", PackedDate(2019, 4, 6), SubjectRelationGraphPlatform.TV, 26, UnifiedCollectionType.DONE),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(294137, "鬼滅の刃 兄妹の絆", "鬼灭之刃 兄妹的羁绊", PackedDate(2019, 3, 29), SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.DONE), SubjectRelation.COMPILATION),
                    SubjectRelationGraphBranch(subject(349032, "鬼滅の刃 浅草編", "鬼灭之刃 浅草篇", PackedDate(2021, 9, 12), SubjectRelationGraphPlatform.TV, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                    SubjectRelationGraphBranch(subject(349033, "鬼滅の刃 鼓屋敷編", "鬼灭之刃 鼓屋敷篇", PackedDate(2021, 9, 18), SubjectRelationGraphPlatform.TV, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                    SubjectRelationGraphBranch(subject(317002, "鬼滅の刃 那田蜘蛛山編", "鬼灭之刃 那田蜘蛛山篇", PackedDate(2020, 10, 17), SubjectRelationGraphPlatform.TV, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                    SubjectRelationGraphBranch(subject(322102, "鬼滅の刃 柱合会議・蝶屋敷編", "鬼灭之刃 柱合会议・蝶屋敷篇", PackedDate(2020, 12, 20), SubjectRelationGraphPlatform.TV, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(291494, "劇場版 鬼滅の刃 無限列車編", "剧场版 鬼灭之刃 无限列车篇", PackedDate(2020, 10, 16), SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.DONE),
                isMinor = true,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(350764, "鬼滅の刃 無限列車編", "鬼灭之刃 无限列车篇", PackedDate(2021, 10, 10), SubjectRelationGraphPlatform.TV, 7, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = true,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(328195, "鬼滅の刃 遊郭編", "鬼灭之刃 游郭篇", PackedDate(2021, 12, 5), SubjectRelationGraphPlatform.TV, 11, UnifiedCollectionType.DOING),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(422759, "鬼滅の刃 遊郭編 特別編集版", "鬼灭之刃 游郭篇 特別编集版", PackedDate(2023, 4, 1), SubjectRelationGraphPlatform.TV, 2, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                    SubjectRelationGraphBranch(subject(410499, "鬼滅の刃 上弦集結、そして刀鍛冶の里へ", "鬼灭之刃 上弦集结、前往锻刀村", PackedDate(2023, 2, 3), SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(369768, "鬼滅の刃 刀鍛冶の里編", "鬼灭之刃 刀匠村篇", PackedDate(2023, 4, 9), SubjectRelationGraphPlatform.TV, 11, UnifiedCollectionType.WISH),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(484412, "鬼滅の刃 刀鍛冶の里編 特別編集版", "鬼灭之刃 刀匠村篇 特別编集版", PackedDate(2024, 5, 4), SubjectRelationGraphPlatform.TV, 2, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                    SubjectRelationGraphBranch(subject(469668, "鬼滅の刃 絆の奇跡、そして柱稽古へ", "鬼灭之刃 绊之奇迹，然后与柱训练", PackedDate(2024, 2, 2), SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(441939, "鬼滅の刃 柱稽古編", "鬼灭之刃 柱训练篇", PackedDate(2024, 5, 12), SubjectRelationGraphPlatform.TV, 8, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(569579, "鬼滅の刃 柱稽古編 特別編集版", "鬼灭之刃 柱训练篇 特別编集版", PackedDate(2025, 7, 16), SubjectRelationGraphPlatform.TV, 2, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.COMPILATION),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(501958, "劇場版 鬼滅の刃 無限城編 第一章 猗窩座再来", "剧场版 鬼灭之刃 无限城篇 第一章 猗窝座再袭", PackedDate(2025, 7, 18), SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = true,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(501960, "劇場版 鬼滅の刃 無限城編 第二部", "剧场版 鬼灭之刃 无限城篇 第二部", PackedDate.Invalid, SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = true,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(501961, "劇場版 鬼滅の刃 無限城編 第三部", "剧场版 鬼灭之刃 无限城篇 第三部", PackedDate.Invalid, SubjectRelationGraphPlatform.MOVIE, 1, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = true,
                branches = emptyList(),
            ),
        ),
        truncated = false,
    )

    /** 某科学的超电磁炮: 主线本身是衍生作品, 原作作为 "主线故事" 列出 */
    val Railgun = SubjectRelationGraph(
        subjectId = 2585,
        mainline = listOf(
            SubjectRelationGraphMainNode(
                subject(2585, "とある科学の超電磁砲", "某科学的超电磁炮", PackedDate(2009, 10, 2), SubjectRelationGraphPlatform.TV, 24, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(1014, "とある魔術の禁書目録", "魔法禁书目录", PackedDate(2008, 10, 4), SubjectRelationGraphPlatform.TV, 24, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.MAIN_STORY),
                    SubjectRelationGraphBranch(subject(98371, "とある科学の超電磁砲 炎天下の撮影モデルも楽じゃありませんわね", "某科学的超电磁炮 OVA", PackedDate(2010, 10, 29), SubjectRelationGraphPlatform.OVA, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.SPECIAL),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(51928, "とある科学の超電磁砲S", "某科学的超电磁炮S", PackedDate(2013, 4, 12), SubjectRelationGraphPlatform.TV, 24, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = listOf(
                    SubjectRelationGraphBranch(subject(97197, "とある科学の超電磁砲S 大事なことはぜんぶ銭湯に教わった", "某科学的超电磁炮S OVA", PackedDate(2014, 3, 27), SubjectRelationGraphPlatform.OVA, 1, UnifiedCollectionType.NOT_COLLECTED), SubjectRelation.SPECIAL),
                ),
            ),
            SubjectRelationGraphMainNode(
                subject(262940, "とある科学の超電磁砲T", "某科学的超电磁炮T", PackedDate(2020, 1, 10), SubjectRelationGraphPlatform.TV, 25, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = emptyList(),
            ),
            SubjectRelationGraphMainNode(
                subject(537743, "とある科学の超電磁砲 第4期", "某科学的超电磁炮 第四季", PackedDate.Invalid, SubjectRelationGraphPlatform.TV, 0, UnifiedCollectionType.NOT_COLLECTED),
                isMinor = false,
                branches = emptyList(),
            ),
        ),
        truncated = false,
    )

    /**
     * 第一部有 [branchCount] 个番外的系列
     */
    fun manyBranches(branchCount: Int): SubjectRelationGraph = SubjectRelationGraph(
        subjectId = 2,
        mainline = listOf(
            SubjectRelationGraphMainNode(
                subject(1, "Season 1", "第一季", PackedDate(2020, 1, 1), SubjectRelationGraphPlatform.TV, 12),
                isMinor = false,
                branches = (1..branchCount).map {
                    SubjectRelationGraphBranch(
                        subject(100 + it, "OVA $it", "番外 $it", PackedDate(2020, 6, it), SubjectRelationGraphPlatform.OVA, 1),
                        SubjectRelation.SPECIAL,
                    )
                },
            ),
            SubjectRelationGraphMainNode(
                subject(2, "Season 2", "第二季", PackedDate(2021, 1, 1), SubjectRelationGraphPlatform.TV, 12),
                isMinor = false,
                branches = emptyList(),
            ),
        ),
        truncated = true,
    )

    private fun subject(
        subjectId: Int,
        name: String,
        nameCn: String,
        airDate: PackedDate,
        platform: SubjectRelationGraphPlatform?,
        episodeCount: Int,
        collectionType: UnifiedCollectionType = UnifiedCollectionType.NOT_COLLECTED,
    ) = SubjectRelationGraphSubject(subjectId, name, nameCn, image = "", airDate, platform, episodeCount, collectionType)
}
