/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource

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
            assertEquals("超元气3姐妹", removeSpecials("超元气三姐妹"))
        }
        add("中二病也要谈恋爱") {
            assertEquals("中2病也要谈恋爱！", removeSpecials("中二病也要谈恋爱！"))
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
    fun `removeSpecials seasons`() = runDynamicTests {
        add("second season") {
            assertEquals("测试 第2季", removeSpecials("测试 第二季"))
        }
    }

    private fun removeSpecials(
        string: String,
        removeWhitespace: Boolean = false,
    ) = MediaListFilters.removeSpecials(
        string,
        removeWhitespace = removeWhitespace,
    )
}
