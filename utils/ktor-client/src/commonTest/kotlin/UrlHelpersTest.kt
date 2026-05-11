/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import me.him188.ani.test.TestContainer
import me.him188.ani.test.TestFactory
import me.him188.ani.test.runDynamicTests
import me.him188.ani.utils.ktor.UrlHelpers.computeAbsoluteUrl
import kotlin.test.assertEquals

@TestContainer
class UrlHelpersTest {
    @TestFactory
    fun computeAbsoluteUrlTest() = runDynamicTests {
        fun case(
            expected: String,
            baseUrl: String,
            relativeUrl: String,
        ) = add("$baseUrl + $relativeUrl = $expected") {
            assertEquals(
                expected,
                computeAbsoluteUrl(baseUrl, relativeUrl),
            )
        }

        case(
            "https://example.com/relative",
            "https://example.com", "relative",
        )

        case(
            "https://example.com/relative",
            "https://example.com/index.html", "relative",
        )

        case(
            "https://example.com/a.b/relative",
            "https://example.com/a.b/", "relative",
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
            "https://example.com/relative/foo",
            "https://example.com/test", "/relative/foo",
        )

        case(
            "https://example.com/relative/foo",
            "https://example.com/test/test", "/relative/foo",
        )

        case(
            "https://example.com/relative/foo",
            "https://example.com/test/test/", "/relative/foo",
        )

        case(
            "https://example.com/relative/foo/",
            "https://example.com/test/test/", "/relative/foo/",
        )

        case(
            "https://example.com/",
            "https://example.com/", "",
        )

        val encodedSubjectPath =
            "/%E6%91%87%E6%BB%9A%E4%B9%83%E6%98%AF%E6%B7%91%E5%A5%B3%E7%9A%84%E7%88%B1%E5%A5%BD%20(2025)%20%5Btmdbid=260523%5D/"
        case(
            "http://37.187.76.11:8000$encodedSubjectPath",
            "http://37.187.76.11:8000/?kw=keyword",
            encodedSubjectPath,
        )

        case(
            "http://192.168.31.2:8080/Downloads/TVAnimeSeason/202410/?raw=true",
            "http://192.168.31.2:8080/Downloads/TVAnimeSeason/?raw=true",
            "202410/?raw=true",
        )

        case(
            "http://192.168.31.2:8080/Downloads/TVAnimeSeason/202410/?raw=true",
            "http://192.168.31.2:8080/Downloads/TVAnimeSeason/?raw=true",
            "/Downloads/TVAnimeSeason/202410/?raw=true",
        )

//        case(
//            "/",
//            "", "",
//        )
//
//        case(
//            "/test",
//            "", "/test",
//        )
//
//        case(
//            "/test",
//            "", "test",
//        )
    }
}
