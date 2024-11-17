/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.format

import me.him188.ani.test.TestContainer
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests
import kotlin.test.assertEquals

/**
 * @see SelectorHelpers.computeAbsoluteUrl
 */
@TestContainer
class SelectorHelpersTest {
    @TestFactory
    fun computeAbsoluteUrlTest() = runDynamicTests {
        fun case(
            expected: String,
            baseUrl: String,
            relativeUrl: String,
        ) = add("$baseUrl + $relativeUrl = $expected") {
            assertEquals(
                expected,
                SelectorHelpers.computeAbsoluteUrl(baseUrl, relativeUrl),
            )
        }

        case(
            "https://example.com/relative",
            "https://example.com", "relative",
        )

        case(
            "https://example.com/relative",
            "https://example.com", "/relative",
        )

        case(
            "https://example.com/relative",
            "https://example.com/", "/relative",
        )

        case(
            "https://example.com/relative/",
            "https://example.com/", "/relative/",
        )

        case(
            "https://example.com/relative/foo",
            "https://example.com/", "/relative/foo",
        )

        case(
            "https://example.com/relative/foo/../bar",
            "https://example.com/", "/relative/foo/../bar",
        )

        case(
            "https://example.com/",
            "https://example.com/", "",
        )

        case(
            "/",
            "", "",
        )

        case(
            "/test",
            "", "/test",
        )

        case(
            "/test",
            "", "test",
        )
    }
}