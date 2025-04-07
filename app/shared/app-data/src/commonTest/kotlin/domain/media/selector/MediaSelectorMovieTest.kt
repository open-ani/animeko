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
import me.him188.ani.app.domain.media.selector.testFramework.addSimpleMediaSelectorTest
import me.him188.ani.test.TestContainer
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests

/**
 * 测试主番与剧场版的关系
 */
@TestContainer
class MediaSelectorMovieTest {
    @TestFactory
    fun `exclude movie when playing main subject`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "玉子市场",
            {
                initSubject("玉子市场") {
                    aliases(
                        "Tamako Market",
                        "たまこまーけっと",
                    )
                    seriesInfo(seasonSort = 1) {
                        series(
                            // 剧场版:
                            "玉子市场 剧场版",
                            "玉子爱情故事",
                            "Tamako Love Story",
                            "たまこラブストーリー",
                        )
                    }
                }
            },
        ) {
            checkSubjectExclusion {
                expect(
                    "玉子市场" to null,
                    "玉子市场 剧场版" to FromSeriesSeason,
                    "玉子爱情故事" to FromSeriesSeason,
                    "Tamako Market" to null,
                    "Tamako Love Story" to FromSeriesSeason,
                    "たまこまーけっと" to null,
                    "たまこラブストーリー" to FromSeriesSeason,
                )
            }
        }
    }

    @TestFactory
    fun `exclude main subject when playing movie`() = runDynamicTests {
        addSimpleMediaSelectorTest(
            "玉子市场剧场版",
            {
                initSubject("玉子市场 剧场版") {
                    aliases(
                        "玉子爱情故事",
                        "Tamako Love Story",
                        "たまこラブストーリー",
                    )
                    seriesInfo(seasonSort = 1) {
                        series(
                            // 主番:
                            "玉子市场",
                            "Tamako Market",
                            "たまこまーけっと",
                        )
                    }
                }
            },
        ) {
            checkSubjectExclusion {
                expect(
                    "玉子市场" to FromSeriesSeason,
                    "玉子市场 剧场版" to null,
                    "玉子爱情故事" to null,
                    "Tamako Market" to FromSeriesSeason,
                    "Tamako Love Story" to null,
                    "たまこまーけっと" to FromSeriesSeason,
                    "たまこラブストーリー" to null,
                )
            }
        }
    }
}