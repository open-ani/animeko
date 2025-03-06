/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.topic.EpisodeRange
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests
import kotlin.test.assertEquals

class MediaListFiltersTest {
    @TestFactory
    fun `removeSpecials removes spaces`() = runDynamicTests {
        add("when removeWhitespace = true") {
            assertEquals("abc", removeSpecials("a b c", removeWhitespace = true))
        }
        add("when removeWhitespace = false") {
            assertEquals("a b c", removeSpecials("a b c", removeWhitespace = false))
        }
    }

    @TestFactory
    fun `removeSpecials numbers`() = runDynamicTests {
        add("超元气三姐妹") {
            assertEquals("超元气三姐妹", removeSpecials("超元气三姐妹"))
        }
        add("中二病也要谈恋爱") {
            assertEquals("中二病也要谈恋爱", removeSpecials("中二病也要谈恋爱！"))
        }
    }

    @TestFactory
    fun `removeSpecials re`() = runDynamicTests {
        add("Re：从零开始的异世界生活") {
            assertEquals("Re：从零开始的异世界生活", removeSpecials("Re：从零开始的异世界生活"))
        }
        add("Re:从零开始的异世界生活") {
            assertEquals("Re从零开始的异世界生活", removeSpecials("Re:从零开始的异世界生活"))
        }
        add("Re: 从零开始的异世界生活") {
            assertEquals(
                "Re 从零开始的异世界生活",
                removeSpecials("Re: 从零开始的异世界生活", removeWhitespace = false),
            )
            assertEquals("Re从零开始的异世界生活", removeSpecials("Re: 从零开始的异世界生活", removeWhitespace = true))
        }
        add("Re：CREATORS") {
            assertEquals("Re：CREATORS", removeSpecials("Re：CREATORS"))
        }
    }

    @TestFactory
    fun `removeSpecials movie`() = runDynamicTests {
        add("剧场版 re0") {
            assertEquals("Re：从零开始的异世界生活", removeSpecials("剧场版 Re：从零开始的异世界生活"))
        }
        add("剧场版 with special") {
            assertEquals("从零开始的异世界生活", removeSpecials("剧场版 从零开始的异世界生活()"))
        }
        add("剧场版 紫罗兰") {
            assertEquals("紫罗兰永恒花园", removeSpecials("剧场版 紫罗兰永恒花园"))
        }
        add("剧场版 suffix") {
            assertEquals("紫罗兰永恒花园", removeSpecials("紫罗兰永恒花园剧场版"))
        }
        add("剧场版 suffix with space") {
            assertEquals("紫罗兰永恒花园", removeSpecials("紫罗兰永恒花园 剧场版"))
        }
    }

    @TestFactory
    fun `removeSpecials period`() = runDynamicTests {
        add("with period") {
            assertEquals("紫罗兰永恒花园", removeSpecials("紫罗兰永恒花园。"))
        }
        add("with comma") {
            assertEquals("紫罗兰永恒花园", removeSpecials("紫罗兰永恒花园，"))
        }
        add("with comma and space") {
            assertEquals("紫罗兰永恒花园", removeSpecials("紫罗兰永恒花园， "))
        }
    }

    @TestFactory
    fun `removeSpecials seasons`() = runDynamicTests {
        add("second season") {
            assertEquals("测试 第二季", removeSpecials("测试 第二季"))
        }
    }

    @TestFactory
    fun `removeSpecials leading specials`() = runDynamicTests {
        add("remove leading specials regardless of position") {
            assertEquals("测试 第二季", removeSpecials("~~~~~~~~~~!测试 第二季"))
        }
        add("remove infix specials") {
            // 需要两个非特殊字符后才会开始删除
            assertEquals("测!!!试 第二季", removeSpecials("测!!!试!!! 第二季"))
            assertEquals("测试 第二季", removeSpecials("测试!!!!!! 第二季"))
        }
    }

    @TestFactory
    fun `removeSpecials cases`() = runDynamicTests {
        fun case(
            expected: String,
            originalTitle: String,
            removeWhitespace: Boolean = false,
            replaceNumbers: Boolean = false,
        ) {
            add("$originalTitle -> $expected") {
                assertEquals(expected, removeSpecials(originalTitle, removeWhitespace, replaceNumbers))
            }
        }

        case(
            "香格里拉 弗陇提亚 屎作猎人向神作发起挑战  第二季",
            "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～ 第二季",
            removeWhitespace = false,
        )
        case(
            "香格里拉弗陇提亚屎作猎人向神作发起挑战第二季",
            "香格里拉·弗陇提亚～屎作猎人向神作发起挑战～ 第二季",
            removeWhitespace = true,
        )
        case(
            "香格里拉开拓异境粪作猎手挑战神作",
            "香格里拉・开拓异境～粪作猎手挑战神作～",
            removeWhitespace = true,
        )
        case(
            "五等分的新娘∬",
            "五等分的新娘∬",
            removeWhitespace = true,
        )
        case(
            "newgame",
            "new game!!",
            removeWhitespace = true,
        )
        case(
            "理科生坠入情网故尝试证明 r=1-sinθ ♡",
            "理科生坠入情网故尝试证明[r=1-sinθ]♡",
            removeWhitespace = false,
        )
        case(
            "青出于蓝 缘",
            "青出于蓝～缘～",
            removeWhitespace = false,
        )
    }

    @TestFactory
    fun `test ContainsSubjectName`() = runDynamicTests {
        fun case(title: String, subjectName: String, expected: Boolean) {
            add("$title matches $subjectName") {
                val context = MediaListFilterContext(
                    subjectNames = setOf(subjectName),
                    episodeSort = EpisodeSort(1),
                    null, null,
                )
                context.run {
                    assertEquals(
                        expected,
                        MediaListFilters.ContainsSubjectName.applyOn(
                            object : MediaListFilter.Candidate {
                                override val originalTitle: String get() = title
                                override val episodeRange: EpisodeRange? get() = null
                            },
                        ),
                    )
                }
            }
        }

        // subject matches title
        infix fun String.matches(title: String) {
            case(title, this, true)
        }

        // subject matches title
        infix fun String.mismatches(title: String) {
            case(title, this, false)
        }

        "哥特萝莉侦探事件簿" matches "哥特萝莉侦探事件簿"
        "哥特萝莉侦探事件薄" matches "哥特萝莉侦探事件簿"
        "败犬女主太多了" matches "败犬女主太多啦"

        "地狱少女第一季" mismatches "地。 ―关于地球的运动―"
        "地狱少女" mismatches "地。"
    }

    private fun removeSpecials(
        string: String,
        removeWhitespace: Boolean = false,
        replaceNumbers: Boolean = false,
    ) = MediaListFilters.removeSpecials(
        string,
        removeWhitespace = removeWhitespace,
        replaceNumbers = replaceNumbers,
    )
}
