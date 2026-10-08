/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.models.subject

/**
 * 几个系列的主线条目, 名字、sort 范围、特别篇的 sort 取自 2026-10 的 Bangumi 数据. 第一个名字是中文名, 其余是日文名和别名.
 */
object SplitSeasonTestData {
    private fun subject(id: Int, sorts: IntRange?, vararg names: String, specials: List<Float> = emptyList()) = SplitSeason.Candidate(
        subjectId = id,
        names = names.toList(),
        firstSort = sorts?.first,
        lastSort = sorts?.last,
        episodeCount = sorts?.count() ?: 0,
        specialSorts = specials,
    )

    /**
     * 全系列 sort 连续 (1 至 85), 第二季到第四季各拆成两个条目.
     */
    val ReZero = listOf(
        subject(
            140001, 1..25,
            "Re：从零开始的异世界生活", "Re:ゼロから始める異世界生活", "Re:Zero kara Hajimeru Isekai Seikatsu",
            "rezero", "Re0", "Re: Life a Different World from Zero",
        ),
        subject(
            278826, 26..38,
            "Re：从零开始的异世界生活 第二季", "Re:ゼロから始める異世界生活 2nd season",
            "Re:Zero kara Hajimeru Isekai Seikatsu (2020)", "Re0 第二季", "Re:ゼロから始める異世界生活2",
        ),
        subject(
            316247, 39..50,
            "Re：从零开始的异世界生活 第二季 后半部分", "Re:ゼロから始める異世界生活 2nd season 後半クール",
            "Re:Zero kara Hajimeru Isekai Seikatsu (2021)", "Re0 第二季后半",
        ),
        subject(
            425998, 51..58,
            "Re：从零开始的异世界生活 第三季 袭击篇", "Re:ゼロから始める異世界生活 3rd season 襲擊編",
            "Re：從零開始的異世界生活 第三季 襲擊篇", "Re0 第三季", "Re0 3", "rezero s3",
            "Re:Zero kara Hajimeru Isekai Seikatsu (2024)", "Re:ゼロから始める異世界生活 (2024)",
            "Re:Zero kara Hajimeru Isekai Seikatsu 3rd Season Shuugeki Hen",
            "Re:ZERO -Starting Life in Another World- Season 3 Shuugeki Hen",
        ),
        subject(
            510728, 59..66,
            "Re：从零开始的异世界生活 第三季 反击篇", "Re:ゼロから始める異世界生活 3rd season 反擊編",
            "Re：從零開始的異世界生活 第三季 反擊篇", "Re:Zero kara Hajimeru Isekai Seikatsu (2025)",
            "Re:Zero - Starting Life in Another World (2025)", "re0 第三季 反击篇", "re0 第三季 反擊篇",
        ),
        subject(
            547888, 67..77,
            "Re：从零开始的异世界生活 第四季 丧失篇", "Re:ゼロから始める異世界生活 4th season 喪失編",
            "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4",
            "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季 喪失篇",
            "re0 第四季 丧失篇", "re0 第四季 喪失篇",
        ),
        subject(
            633836, 78..85,
            "Re：从零开始的异世界生活 第四季 夺还篇", "Re:ゼロから始める異世界生活 4th season 奪還編",
            "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4",
            "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季 奪還篇",
            "re0 第四季 夺还篇", "re0 第四季 奪還篇",
        ),
    )

    /**
     * 第一季拆成两个条目 (1 至 23), 第二季的 sort 从序章 0 重新开始, 第2部分从 13 接上; 第三季从 1 开始, 第2部分还没有剧集.
     */
    val MushokuTensei = listOf(
        subject(
            277554, 1..11,
            "无职转生～到了异世界就拿出真本事～", "無職転生 ～異世界行ったら本気だす～", "无职转生 ～在异世界认真地活下去～",
            "Jobless Reincarnation ~I Will Seriously Try If I Go to Another World~", "Mushoku Tensei ~Isekai Ittara Honki Dasu~",
        ),
        subject(
            325585, 12..23,
            "无职转生～到了异世界就拿出真本事～ 第2部分", "無職転生 ～異世界行ったら本気だす～ 第2クール",
            "无职转生 ～在异世界认真地活下去～ 第2部分", "Mushoku Tensei: Isekai Ittara Honki Dasu Part 2",
            "Mushoku Tensei: Jobless Reincarnation Part 2", "無職轉生 到了異世界就拿出真本事 後半", "无职转生 ～在异世界认真地活下去～ 后半",
            specials = listOf(24f),
        ),
        subject(
            373247, 0..12,
            "无职转生 第二季 ～到了异世界就拿出真本事～", "無職転生Ⅱ ～異世界行ったら本気だす～",
            "无职转生 第二季 ～在异世界认真地活下去～", "Mushoku Tensei II: Isekai Ittara Honki Dasu",
            "Mushoku Tensei: Jobless Reincarnation Season 2",
        ),
        subject(
            444557, 13..24,
            "无职转生 第二季 ～到了异世界就拿出真本事～ 第2部分", "無職転生Ⅱ ～異世界行ったら本気だす～ 第2クール",
            "无职转生 第二季 ～在异世界认真地活下去～ 第2部分", "Mushoku Tensei II: Isekai Ittara Honki Dasu (2024)",
            "Mushoku Tensei: Jobless Reincarnation Season 2 (2024)", "Mushoku Tensei: Jobless Reincarnation Season 2 part 2",
            "Mushoku Tensei II: Isekai Ittara Honki Dasu part 2",
            specials = listOf(18.5f),
        ),
        subject(
            501963, 1..14,
            "无职转生 第三季 ～到了异世界就拿出真本事～", "無職転生Ⅲ ～異世界行ったら本気だす～",
            "Mushoku Tensei: Jobless Reincarnation Season 3", "Mushoku Tensei III: Isekai Ittara Honki Dasu",
            "无职转生 第三季 ～在异世界认真地活下去～",
        ),
        subject(
            708197, null,
            "无职转生 第三季 ～到了异世界就拿出真本事～ 第2部分", "無職転生Ⅲ ～異世界行ったら本気だす～ 第2クール",
        ),
    )

    /**
     * 第三季和最终季各拆成两个条目, 完结篇前后篇各只有一集且 sort 从 1 重新开始.
     */
    val AttackOnTitan = listOf(
        subject(217300, 38..49, "进击的巨人 第三季", "進撃の巨人 Season 3", "Attack on Titan Season 3"),
        subject(263750, 50..59, "进击的巨人 第三季 Part.2", "進撃の巨人 Season 3 Part.2", "Attack on Titan Season 3 Part 2"),
        subject(
            285666, 60..75,
            "进击的巨人 最终季", "進撃の巨人 The Final Season", "进击的巨人 第四季", "Attack on Titan Final Season",
            "Shingeki no Kyojin: The Final Season",
        ),
        subject(
            331752, 76..87,
            "进击的巨人 最终季 Part.2", "進撃の巨人 The Final Season Part.2", "Attack on Titan Final Season Part 2",
            "Shingeki no Kyojin: The Final Season Part 2",
            specials = listOf(1f, 2f, 3f, 4f, 5f, 6f),
        ),
        subject(376739, 1..1, "进击的巨人 最终季 完结篇 前篇", "進撃の巨人 The Final Season 完結編 前編"),
        subject(415779, 1..1, "进击的巨人 最终季 完结篇 后篇", "進撃の巨人 The Final Season 完結編 後編"),
    )

    /**
     * 第一季拆成两个条目, 之后每季一个条目, 全系列 sort 连续.
     */
    val SpyFamily = listOf(
        subject(329906, 1..12, "间谍过家家", "SPY×FAMILY", "Spy x Family", "スパイファミリー", "SPY×FAMILY间谍家家酒(港/台)"),
        subject(
            373267, 13..25,
            "间谍过家家 第2部分", "SPY×FAMILY 第2クール", "Spy x Family (2022)", "スパイファミリー 第2クール", "SPY×FAMILY Part 2",
            specials = listOf(0f, 15.5f),
        ),
        subject(
            411427, 26..37,
            "间谍过家家 第二季", "SPY×FAMILY Season 2", "スパイファミリー (2023)", "Spy x Family (2023)", "SPY×FAMILY 間諜家家酒 Season 2",
        ),
        subject(498378, 38..50, "间谍过家家 第三季", "SPY×FAMILY Season 3"),
    )

    /**
     * 第二季的 sort 从 1 重新开始, 第2部分从 13 接上.
     */
    val AncientMagusBride = listOf(
        subject(210864, 1..24, "魔法使的新娘", "魔法使いの嫁", specials = listOf(12.5f)),
        subject(
            399820, 1..12,
            "魔法使的新娘 第二季", "魔法使いの嫁 SEASON2", "Mahoutsukai no Yome SEASON 2", "魔法使之嫁 第二季",
            "The Ancient Magus' Bride SEASON2",
        ),
        subject(
            442523, 13..24,
            "魔法使的新娘 第二季 第2部分", "魔法使いの嫁 SEASON2 第2クール", "Mahoutsukai no Yome Season 2 Part 2",
            "The Ancient Magus' Bride Season 2 Part 2",
        ),
    )

    /**
     * 第一季拆成两个条目, 前半的 11 集之后还有 11.5 集, 后半中间也有特别篇.
     */
    val EightySix = listOf(
        subject(
            302189, 1..11, "86 -不存在的战区-", "86―エイティシックス―", "86 -不存在的地域-", "86-エイティシックス-",
            specials = listOf(11.5f),
        ),
        subject(
            331887, 12..23,
            "86 -不存在的战区- 第2部分", "86―エイティシックス― 第2クール", "86: Eighty Six 2nd Season", "86: Eighty Six Part 2",
            specials = listOf(17.5f, 18.5f, 21.5f),
        ),
    )

    /**
     * 连续放送的半年番只有一个条目, 第二季的 sort 接着第一季.
     */
    val Frieren = listOf(
        subject(400602, 1..28, "葬送的芙莉莲", "葬送のフリーレン", "Frieren: Beyond Journey's End", "Sousou no Frieren", "葬送的芙莉蓮"),
        subject(515759, 29..38, "葬送的芙莉莲 第二季", "葬送のフリーレン 第2期"),
    )

    /**
     * 第二季是一个 23 集的条目, 第三季的日文名带篇章名.
     */
    val JujutsuKaisen = listOf(
        subject(294993, 1..24, "咒术回战", "呪術廻戦"),
        subject(369304, 25..47, "咒术回战 第二季", "呪術廻戦 懐玉・玉折／渋谷事変"),
        subject(472741, 48..59, "咒术回战 第三季", "呪術廻戦 死滅回游 前編"),
    )

    val Apothecary = listOf(
        subject(420628, 1..24, "药屋少女的呢喃", "薬屋のひとりごと"),
        subject(486347, 25..48, "药屋少女的呢喃 第二季", "薬屋のひとりごと 第2期"),
        subject(568244, 49..60, "药屋少女的呢喃 第三季", "薬屋のひとりごと 第3期", "Kusuriya no Hitorigoto 3rd Season"),
        subject(599893, 61..72, "药屋少女的呢喃 第三季 第2部分", "薬屋のひとりごと 第3期 第2クール"),
    )
}
