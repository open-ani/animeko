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
 * 几个系列的主线条目名字和服务端识别出的拆分季. 名字取自 2026-10 的 Bangumi 数据, 第一个名字是中文名, 其余是日文名和别名;
 * 拆分季是服务端 `SplitSeasonDetector` 对这些条目的输出.
 */
object SplitSeasonTestData {
    class Series(
        /**
         * 按播出顺序排列的主线条目及其名字.
         */
        val subjects: List<Pair<Int, List<String>>>,
        /**
         * 系列里的拆分季, 以第一段的视角 (`selfIndex` 为 0) 记录.
         */
        val seasons: List<SplitSeason>,
    ) {
        fun names(subjectId: Int): List<String> = subjects.first { it.first == subjectId }.second

        /**
         * 服务端对条目 [subjectId] 下发的拆分季, 不是拆分季的一段时为 `null`.
         */
        fun splitSeason(subjectId: Int): SplitSeason? = seasons.firstOrNull { season -> season.parts.any { it.subjectId == subjectId } }
            ?.let { season -> season.copy(selfIndex = season.parts.indexOfFirst { it.subjectId == subjectId }) }
    }

    val ReZero = Series(
        subjects = listOf(
            140001 to listOf("Re：从零开始的异世界生活", "Re:ゼロから始める異世界生活", "Re:Zero kara Hajimeru Isekai Seikatsu", "rezero", "Re0", "Re: Life a Different World from Zero"),
            278826 to listOf("Re：从零开始的异世界生活 第二季", "Re:ゼロから始める異世界生活 2nd season", "Re:Zero kara Hajimeru Isekai Seikatsu (2020)", "Re0 第二季", "Re:ゼロから始める異世界生活2"),
            316247 to listOf("Re：从零开始的异世界生活 第二季 后半部分", "Re:ゼロから始める異世界生活 2nd season 後半クール", "Re:Zero kara Hajimeru Isekai Seikatsu (2021)", "Re0 第二季后半"),
            425998 to listOf("Re：从零开始的异世界生活 第三季 袭击篇", "Re:ゼロから始める異世界生活 3rd season 襲擊編", "Re：從零開始的異世界生活 第三季 襲擊篇", "Re0 第三季", "Re0 3", "rezero s3", "Re:Zero kara Hajimeru Isekai Seikatsu (2024)", "Re:ゼロから始める異世界生活 (2024)", "Re:Zero kara Hajimeru Isekai Seikatsu 3rd Season Shuugeki Hen", "Re:ZERO -Starting Life in Another World- Season 3 Shuugeki Hen"),
            510728 to listOf("Re：从零开始的异世界生活 第三季 反击篇", "Re:ゼロから始める異世界生活 3rd season 反擊編", "Re：從零開始的異世界生活 第三季 反擊篇", "Re:Zero kara Hajimeru Isekai Seikatsu (2025)", "Re:Zero - Starting Life in Another World (2025)", "re0 第三季 反击篇", "re0 第三季 反擊篇"),
            547888 to listOf("Re：从零开始的异世界生活 第四季 丧失篇", "Re:ゼロから始める異世界生活 4th season 喪失編", "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4", "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季 喪失篇", "re0 第四季 丧失篇", "re0 第四季 喪失篇"),
            633836 to listOf("Re：从零开始的异世界生活 第四季 夺还篇", "Re:ゼロから始める異世界生活 4th season 奪還編", "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4", "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季 奪還篇", "re0 第四季 夺还篇", "re0 第四季 奪還篇"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 278826,
                        names = listOf("Re：从零开始的异世界生活 第二季", "Re:ゼロから始める異世界生活 2nd season", "Re:Zero kara Hajimeru Isekai Seikatsu (2020)", "Re0 第二季", "Re:ゼロから始める異世界生活2"),
                        markers = emptyList(),
                        firstSort = 26,
                        episodeCount = 13,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 316247,
                        names = listOf("Re：从零开始的异世界生活 第二季 后半部分", "Re:ゼロから始める異世界生活 2nd season 後半クール", "Re:Zero kara Hajimeru Isekai Seikatsu (2021)", "Re0 第二季后半"),
                        markers = listOf("后半部分", "後半クール", "后半"),
                        firstSort = 39,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("Re：从零开始的异世界生活 第二季", "Re:ゼロから始める異世界生活 2nd season", "Re:Zero kara Hajimeru Isekai Seikatsu (2020)", "Re0 第二季", "Re:ゼロから始める異世界生活2", "Re:Zero kara Hajimeru Isekai Seikatsu (2021)"),
                otherSeasonNumbers = listOf(1, 3, 4),
            ),
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 425998,
                        names = listOf("Re：从零开始的异世界生活 第三季 袭击篇", "Re:ゼロから始める異世界生活 3rd season 襲擊編", "Re：從零開始的異世界生活 第三季 襲擊篇", "Re0 第三季", "Re0 3", "rezero s3", "Re:Zero kara Hajimeru Isekai Seikatsu (2024)", "Re:ゼロから始める異世界生活 (2024)", "Re:Zero kara Hajimeru Isekai Seikatsu 3rd Season Shuugeki Hen", "Re:ZERO -Starting Life in Another World- Season 3 Shuugeki Hen"),
                        markers = listOf("袭击篇", "襲擊編", "襲擊篇"),
                        firstSort = 51,
                        episodeCount = 8,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 510728,
                        names = listOf("Re：从零开始的异世界生活 第三季 反击篇", "Re:ゼロから始める異世界生活 3rd season 反擊編", "Re：從零開始的異世界生活 第三季 反擊篇", "Re:Zero kara Hajimeru Isekai Seikatsu (2025)", "Re:Zero - Starting Life in Another World (2025)", "re0 第三季 反击篇", "re0 第三季 反擊篇"),
                        markers = listOf("反击篇", "反擊編", "反擊篇"),
                        firstSort = 59,
                        episodeCount = 8,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("Re：从零开始的异世界生活 第三季", "Re:ゼロから始める異世界生活 3rd season", "Re：從零開始的異世界生活 第三季", "Re0 第三季", "Re0 3", "rezero s3", "Re:Zero kara Hajimeru Isekai Seikatsu (2024)", "Re:ゼロから始める異世界生活 (2024)", "Re:Zero kara Hajimeru Isekai Seikatsu 3rd Season Shuugeki Hen", "Re:ZERO -Starting Life in Another World- Season 3 Shuugeki Hen", "Re:Zero kara Hajimeru Isekai Seikatsu (2025)", "Re:Zero - Starting Life in Another World (2025)", "re0 第三季"),
                otherSeasonNumbers = listOf(1, 2, 4),
            ),
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 547888,
                        names = listOf("Re：从零开始的异世界生活 第四季 丧失篇", "Re:ゼロから始める異世界生活 4th season 喪失編", "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4", "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季 喪失篇", "re0 第四季 丧失篇", "re0 第四季 喪失篇"),
                        markers = listOf("丧失篇", "喪失編", "喪失篇"),
                        firstSort = 67,
                        episodeCount = 11,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 633836,
                        names = listOf("Re：从零开始的异世界生活 第四季 夺还篇", "Re:ゼロから始める異世界生活 4th season 奪還編", "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4", "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季 奪還篇", "re0 第四季 夺还篇", "re0 第四季 奪還篇"),
                        markers = listOf("夺还篇", "奪還編", "奪還篇"),
                        firstSort = 78,
                        episodeCount = 8,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("Re：从零开始的异世界生活 第四季", "Re:ゼロから始める異世界生活 4th season", "Re:Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re:ZERO -Starting Life in Another World- Season 4", "Re Zero kara Hajimeru Isekai Seikatsu 4th Season", "Re：從零開始的異世界生活 第四季", "re0 第四季"),
                otherSeasonNumbers = listOf(1, 2, 3),
            ),
        ),
    )

    val MushokuTensei = Series(
        subjects = listOf(
            277554 to listOf("无职转生～到了异世界就拿出真本事～", "無職転生 ～異世界行ったら本気だす～", "无职转生 ～在异世界认真地活下去～", "Jobless Reincarnation ~I Will Seriously Try If I Go to Another World~", "Mushoku Tensei ~Isekai Ittara Honki Dasu~"),
            325585 to listOf("无职转生～到了异世界就拿出真本事～ 第2部分", "無職転生 ～異世界行ったら本気だす～ 第2クール", "无职转生 ～在异世界认真地活下去～ 第2部分", "Mushoku Tensei: Isekai Ittara Honki Dasu Part 2", "Mushoku Tensei: Jobless Reincarnation Part 2", "無職轉生 到了異世界就拿出真本事 後半", "无职转生 ～在异世界认真地活下去～ 后半"),
            373247 to listOf("无职转生 第二季 ～到了异世界就拿出真本事～", "無職転生Ⅱ ～異世界行ったら本気だす～", "无职转生 第二季 ～在异世界认真地活下去～", "Mushoku Tensei II: Isekai Ittara Honki Dasu", "Mushoku Tensei: Jobless Reincarnation Season 2"),
            444557 to listOf("无职转生 第二季 ～到了异世界就拿出真本事～ 第2部分", "無職転生Ⅱ ～異世界行ったら本気だす～ 第2クール", "无职转生 第二季 ～在异世界认真地活下去～ 第2部分", "Mushoku Tensei II: Isekai Ittara Honki Dasu (2024)", "Mushoku Tensei: Jobless Reincarnation Season 2 (2024)", "Mushoku Tensei: Jobless Reincarnation Season 2 part 2", "Mushoku Tensei II: Isekai Ittara Honki Dasu part 2"),
            501963 to listOf("无职转生 第三季 ～到了异世界就拿出真本事～", "無職転生Ⅲ ～異世界行ったら本気だす～", "Mushoku Tensei: Jobless Reincarnation Season 3", "Mushoku Tensei III: Isekai Ittara Honki Dasu", "无职转生 第三季 ～在异世界认真地活下去～"),
            708197 to listOf("无职转生 第三季 ～到了异世界就拿出真本事～ 第2部分", "無職転生Ⅲ ～異世界行ったら本気だす～ 第2クール"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 277554,
                        names = listOf("无职转生～到了异世界就拿出真本事～", "無職転生 ～異世界行ったら本気だす～", "无职转生 ～在异世界认真地活下去～", "Jobless Reincarnation ~I Will Seriously Try If I Go to Another World~", "Mushoku Tensei ~Isekai Ittara Honki Dasu~"),
                        markers = emptyList(),
                        firstSort = 1,
                        episodeCount = 11,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 325585,
                        names = listOf("无职转生～到了异世界就拿出真本事～ 第2部分", "無職転生 ～異世界行ったら本気だす～ 第2クール", "无职转生 ～在异世界认真地活下去～ 第2部分", "Mushoku Tensei: Isekai Ittara Honki Dasu Part 2", "Mushoku Tensei: Jobless Reincarnation Part 2", "無職轉生 到了異世界就拿出真本事 後半", "无职转生 ～在异世界认真地活下去～ 后半"),
                        markers = listOf("第2部分", "第2クール", "Part 2", "後半", "后半"),
                        firstSort = 12,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("无职转生～到了异世界就拿出真本事～", "無職転生 ～異世界行ったら本気だす～", "无职转生 ～在异世界认真地活下去～", "Jobless Reincarnation ~I Will Seriously Try If I Go to Another World~", "Mushoku Tensei ~Isekai Ittara Honki Dasu~", "Mushoku Tensei: Isekai Ittara Honki Dasu", "Mushoku Tensei: Jobless Reincarnation", "無職轉生 到了異世界就拿出真本事"),
                otherSeasonNumbers = listOf(2, 3),
            ),
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 373247,
                        names = listOf("无职转生 第二季 ～到了异世界就拿出真本事～", "無職転生Ⅱ ～異世界行ったら本気だす～", "无职转生 第二季 ～在异世界认真地活下去～", "Mushoku Tensei II: Isekai Ittara Honki Dasu", "Mushoku Tensei: Jobless Reincarnation Season 2"),
                        markers = emptyList(),
                        firstSort = 0,
                        episodeCount = 13,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 444557,
                        names = listOf("无职转生 第二季 ～到了异世界就拿出真本事～ 第2部分", "無職転生Ⅱ ～異世界行ったら本気だす～ 第2クール", "无职转生 第二季 ～在异世界认真地活下去～ 第2部分", "Mushoku Tensei II: Isekai Ittara Honki Dasu (2024)", "Mushoku Tensei: Jobless Reincarnation Season 2 (2024)", "Mushoku Tensei: Jobless Reincarnation Season 2 part 2", "Mushoku Tensei II: Isekai Ittara Honki Dasu part 2"),
                        markers = listOf("第2部分", "第2クール", "part 2"),
                        firstSort = 13,
                        episodeCount = 12,
                        inlineSpecialCount = 1,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("无职转生 第二季 ～到了异世界就拿出真本事～", "無職転生Ⅱ ～異世界行ったら本気だす～", "无职转生 第二季 ～在异世界认真地活下去～", "Mushoku Tensei II: Isekai Ittara Honki Dasu", "Mushoku Tensei: Jobless Reincarnation Season 2", "Mushoku Tensei II: Isekai Ittara Honki Dasu (2024)", "Mushoku Tensei: Jobless Reincarnation Season 2 (2024)"),
                otherSeasonNumbers = listOf(1, 3),
            ),
        ),
    )

    val AttackOnTitan = Series(
        subjects = listOf(
            217300 to listOf("进击的巨人 第三季", "進撃の巨人 Season 3", "Attack on Titan Season 3"),
            263750 to listOf("进击的巨人 第三季 Part.2", "進撃の巨人 Season 3 Part.2", "Attack on Titan Season 3 Part 2"),
            285666 to listOf("进击的巨人 最终季", "進撃の巨人 The Final Season", "进击的巨人 第四季", "Attack on Titan Final Season", "Shingeki no Kyojin: The Final Season"),
            331752 to listOf("进击的巨人 最终季 Part.2", "進撃の巨人 The Final Season Part.2", "Attack on Titan Final Season Part 2", "Shingeki no Kyojin: The Final Season Part 2"),
            376739 to listOf("进击的巨人 最终季 完结篇 前篇", "進撃の巨人 The Final Season 完結編 前編"),
            415779 to listOf("进击的巨人 最终季 完结篇 后篇", "進撃の巨人 The Final Season 完結編 後編"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 217300,
                        names = listOf("进击的巨人 第三季", "進撃の巨人 Season 3", "Attack on Titan Season 3"),
                        markers = emptyList(),
                        firstSort = 38,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 263750,
                        names = listOf("进击的巨人 第三季 Part.2", "進撃の巨人 Season 3 Part.2", "Attack on Titan Season 3 Part 2"),
                        markers = listOf("Part.2", "Part 2"),
                        firstSort = 50,
                        episodeCount = 10,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("进击的巨人 第三季", "進撃の巨人 Season 3", "Attack on Titan Season 3"),
                otherSeasonNumbers = listOf(1, 4),
            ),
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 285666,
                        names = listOf("进击的巨人 最终季", "進撃の巨人 The Final Season", "进击的巨人 第四季", "Attack on Titan Final Season", "Shingeki no Kyojin: The Final Season"),
                        markers = emptyList(),
                        firstSort = 60,
                        episodeCount = 16,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 331752,
                        names = listOf("进击的巨人 最终季 Part.2", "進撃の巨人 The Final Season Part.2", "Attack on Titan Final Season Part 2", "Shingeki no Kyojin: The Final Season Part 2"),
                        markers = listOf("Part.2", "Part 2"),
                        firstSort = 76,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("进击的巨人 最终季", "進撃の巨人 The Final Season", "进击的巨人 第四季", "Attack on Titan Final Season", "Shingeki no Kyojin: The Final Season"),
                otherSeasonNumbers = listOf(1, 3),
            ),
        ),
    )

    val SpyFamily = Series(
        subjects = listOf(
            329906 to listOf("间谍过家家", "SPY×FAMILY", "Spy x Family", "スパイファミリー", "SPY×FAMILY间谍家家酒(港/台)"),
            373267 to listOf("间谍过家家 第2部分", "SPY×FAMILY 第2クール", "Spy x Family (2022)", "スパイファミリー 第2クール", "SPY×FAMILY Part 2"),
            411427 to listOf("间谍过家家 第二季", "SPY×FAMILY Season 2", "スパイファミリー (2023)", "Spy x Family (2023)", "SPY×FAMILY 間諜家家酒 Season 2"),
            498378 to listOf("间谍过家家 第三季", "SPY×FAMILY Season 3"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 329906,
                        names = listOf("间谍过家家", "SPY×FAMILY", "Spy x Family", "スパイファミリー", "SPY×FAMILY间谍家家酒(港/台)"),
                        markers = emptyList(),
                        firstSort = 1,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 373267,
                        names = listOf("间谍过家家 第2部分", "SPY×FAMILY 第2クール", "Spy x Family (2022)", "スパイファミリー 第2クール", "SPY×FAMILY Part 2"),
                        markers = listOf("第2部分", "第2クール", "Part 2"),
                        firstSort = 13,
                        episodeCount = 13,
                        inlineSpecialCount = 1,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("间谍过家家", "SPY×FAMILY", "Spy x Family", "スパイファミリー", "SPY×FAMILY间谍家家酒(港/台)", "Spy x Family (2022)"),
                otherSeasonNumbers = listOf(2, 3),
            ),
        ),
    )

    val AncientMagusBride = Series(
        subjects = listOf(
            210864 to listOf("魔法使的新娘", "魔法使いの嫁"),
            399820 to listOf("魔法使的新娘 第二季", "魔法使いの嫁 SEASON2", "Mahoutsukai no Yome SEASON 2", "魔法使之嫁 第二季", "The Ancient Magus' Bride SEASON2"),
            442523 to listOf("魔法使的新娘 第二季 第2部分", "魔法使いの嫁 SEASON2 第2クール", "Mahoutsukai no Yome Season 2 Part 2", "The Ancient Magus' Bride Season 2 Part 2"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 399820,
                        names = listOf("魔法使的新娘 第二季", "魔法使いの嫁 SEASON2", "Mahoutsukai no Yome SEASON 2", "魔法使之嫁 第二季", "The Ancient Magus' Bride SEASON2"),
                        markers = emptyList(),
                        firstSort = 1,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 442523,
                        names = listOf("魔法使的新娘 第二季 第2部分", "魔法使いの嫁 SEASON2 第2クール", "Mahoutsukai no Yome Season 2 Part 2", "The Ancient Magus' Bride Season 2 Part 2"),
                        markers = listOf("第2部分", "第2クール", "Part 2"),
                        firstSort = 13,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("魔法使的新娘 第二季", "魔法使いの嫁 SEASON2", "Mahoutsukai no Yome SEASON 2", "魔法使之嫁 第二季", "The Ancient Magus' Bride SEASON2", "Mahoutsukai no Yome Season 2", "The Ancient Magus' Bride Season 2"),
                otherSeasonNumbers = listOf(1),
            ),
        ),
    )

    val EightySix = Series(
        subjects = listOf(
            302189 to listOf("86 -不存在的战区-", "86―エイティシックス―", "86 -不存在的地域-", "86-エイティシックス-"),
            331887 to listOf("86 -不存在的战区- 第2部分", "86―エイティシックス― 第2クール", "86: Eighty Six 2nd Season", "86: Eighty Six Part 2"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 302189,
                        names = listOf("86 -不存在的战区-", "86―エイティシックス―", "86 -不存在的地域-", "86-エイティシックス-"),
                        markers = emptyList(),
                        firstSort = 1,
                        episodeCount = 11,
                        inlineSpecialCount = 1,
                    ),
                    SplitSeason.Part(
                        subjectId = 331887,
                        names = listOf("86 -不存在的战区- 第2部分", "86―エイティシックス― 第2クール", "86: Eighty Six 2nd Season", "86: Eighty Six Part 2"),
                        markers = listOf("第2部分", "第2クール", "Part 2"),
                        firstSort = 12,
                        episodeCount = 12,
                        inlineSpecialCount = 3,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("86 -不存在的战区-", "86―エイティシックス―", "86 -不存在的地域-", "86-エイティシックス-", "86: Eighty Six 2nd Season", "86: Eighty Six"),
                otherSeasonNumbers = emptyList(),
            ),
        ),
    )

    val Frieren = Series(
        subjects = listOf(
            400602 to listOf("葬送的芙莉莲", "葬送のフリーレン", "Frieren: Beyond Journey's End", "Sousou no Frieren", "葬送的芙莉蓮"),
            515759 to listOf("葬送的芙莉莲 第二季", "葬送のフリーレン 第2期"),
        ),
        seasons = listOf(
        ),
    )

    val JujutsuKaisen = Series(
        subjects = listOf(
            294993 to listOf("咒术回战", "呪術廻戦"),
            369304 to listOf("咒术回战 第二季", "呪術廻戦 懐玉・玉折／渋谷事変"),
            472741 to listOf("咒术回战 第三季", "呪術廻戦 死滅回游 前編"),
        ),
        seasons = listOf(
        ),
    )

    val Apothecary = Series(
        subjects = listOf(
            420628 to listOf("药屋少女的呢喃", "薬屋のひとりごと"),
            486347 to listOf("药屋少女的呢喃 第二季", "薬屋のひとりごと 第2期"),
            568244 to listOf("药屋少女的呢喃 第三季", "薬屋のひとりごと 第3期", "Kusuriya no Hitorigoto 3rd Season"),
            599893 to listOf("药屋少女的呢喃 第三季 第2部分", "薬屋のひとりごと 第3期 第2クール"),
        ),
        seasons = listOf(
            SplitSeason(
                parts = listOf(
                    SplitSeason.Part(
                        subjectId = 568244,
                        names = listOf("药屋少女的呢喃 第三季", "薬屋のひとりごと 第3期", "Kusuriya no Hitorigoto 3rd Season"),
                        markers = emptyList(),
                        firstSort = 49,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                    SplitSeason.Part(
                        subjectId = 599893,
                        names = listOf("药屋少女的呢喃 第三季 第2部分", "薬屋のひとりごと 第3期 第2クール"),
                        markers = listOf("第2部分", "第2クール"),
                        firstSort = 61,
                        episodeCount = 12,
                        inlineSpecialCount = 0,
                    ),
                ),
                selfIndex = 0,
                baseNames = listOf("药屋少女的呢喃 第三季", "薬屋のひとりごと 第3期", "Kusuriya no Hitorigoto 3rd Season"),
                otherSeasonNumbers = listOf(1, 2),
            ),
        ),
    )
}
