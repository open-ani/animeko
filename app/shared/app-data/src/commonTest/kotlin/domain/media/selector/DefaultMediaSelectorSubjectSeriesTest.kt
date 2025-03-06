/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("SameParameterValue")

package me.him188.ani.app.domain.media.selector

import me.him188.ani.app.domain.media.selector.MediaExclusionReason.FromSeriesSeason
import me.him188.ani.app.domain.media.selector.MediaExclusionReason.SubjectNameMismatch
import me.him188.ani.test.TestContainer
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests

/**
 * 测试过滤掉其他季度的资源
 */
@TestContainer
class DefaultMediaSelectorSubjectSeriesTest {
    @TestFactory
    fun `exclude S1 when playing S2`() = runDynamicTests {
        addMediaSelectorTest(
            "香格里拉第二季",
            {
                initSubject("香格里拉边境 第二季") {
                    aliases(
                        "香格里拉边境~粪作猎人向神作游戏发起挑战~ 第二季",
                        "香格里拉・开拓异境～粪作猎手挑战神作～ 第二季",
                        "Shangri-La Frontier: Kusoge Hunter, Kamige ni Idoman to su 2nd Season",
                        "Shangri-La Frontier Season 2",
                        "シャングリラ・フロンティア～クソゲーハンター、神ゲーに挑まんとす～ 2nd season",
                    )
                    seriesInfo(seasonSort = 1) {
                        series(
                            // 第一季:
                            "香格里拉边境~粪作猎人向神作游戏发起挑战~",
                            "香格里拉・开拓异境～粪作猎手挑战神作～",
                            "香格里拉・開拓異境～糞作獵手挑戰神作～",
                        )
                    }
                }
            },
        ) {
            checkSubjectExclusion {
                expect(
                    "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～ 第二季" to tentatively(
                        SubjectNameMismatch,
                        null,
                    ),
                    "香格里拉·开拓异境～粪作猎手挑战神作～ 第二季" to null,
                    "香格里拉·开拓异境～粪作猎手挑战神作～第二季" to null,
                    "香格里拉·弗陇提亚~屎作猎人向神作发起挑战~第二季" to tentatively(
                        SubjectNameMismatch,
                        null,
                    ),
                    "香格里拉·开拓异境～粪作猎手挑战神作～" to null,
                    "香格里拉·开拓异境～粪作猎手挑战神作～ 2nd season" to tentatively(
                        SubjectNameMismatch,
                        null,
                    ),
                    "香格里拉" to SubjectNameMismatch, // 这确实是另一个番
                    "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉·弗陇提亚~屎作猎人向神作发起挑战~" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉边境" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉2009" to SubjectNameMismatch, // 这确实是另一个番
                    "香格里拉·开拓异境～粪作猎手挑战神作" to null,
                    "这儿是香格里拉" to SubjectNameMismatch,
                )
            }
        }
    }

    @TestFactory
    fun `exclude S2 when playing S1`() = runDynamicTests {
        addMediaSelectorTest(
            "香格里拉第一季",
            {
                initSubject("香格里拉边境~粪作猎人向神作游戏发起挑战~") {
                    aliases(
                        "香格里拉・开拓异境～粪作猎手挑战神作～",
                        "香格里拉・開拓異境～糞作獵手挑戰神作～",
                        "Shangri-La Frontier: Kusogee Hunter, Kamige ni Idoman to Su",
                        "シャングリラ・フロンティア～クソゲーハンター、神ゲーに挑まんとす～",
                    )
                    seriesInfo(seasonSort = 1) {
                        series(
                            "香格里拉边境~粪作猎人向神作游戏发起挑战~ 第二季",
                            "香格里拉・开拓异境～粪作猎手挑战神作～ 第二季",
                            "Shangri-La Frontier: Kusoge Hunter, Kamige ni Idoman to su 2nd Season",
                            "Shangri-La Frontier Season 2",
                            "シャングリラ・フロンティア～クソゲーハンター、神ゲーに挑まんとす～ 2nd season",
                        )
                    }
                }
            },
        ) {
            checkSubjectExclusion {
                expect(
                    "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～ 第二季" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉·开拓异境～粪作猎手挑战神作～ 第二季" to null,
                    "香格里拉·开拓异境～粪作猎手挑战神作～第二季" to null,
                    "香格里拉·弗陇提亚~屎作猎人向神作发起挑战~第二季" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉·开拓异境～粪作猎手挑战神作～" to null,
                    "香格里拉·开拓异境～粪作猎手挑战神作～ 2nd season" to tentatively(
                        null,
                        SubjectNameMismatch,
                    ),
                    "香格里拉" to SubjectNameMismatch, // 这确实是另一个番
                    "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉·弗陇提亚~屎作猎人向神作发起挑战~" to tentatively(
                        SubjectNameMismatch,
                        FromSeriesSeason,
                    ),
                    "香格里拉边境" to tentatively(
                        SubjectNameMismatch,
                        null,
                    ),
                    "香格里拉2009" to SubjectNameMismatch, // 这确实是另一个番
                    "香格里拉·开拓异境～粪作猎手挑战神作" to null,
                    "这儿是香格里拉" to SubjectNameMismatch,
                )
            }
        }
    }
}
