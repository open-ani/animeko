/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import io.ktor.http.Cookie
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebSourceCookieJarTest {
    // 关键用例 7: jar 域后缀匹配 — 域级 cookie 覆盖到子域
    @Test
    fun `domain cookie covers subdomains`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies(
            "https://www.example.com/search",
            listOf(
                BrowserCookie(name = "cf_clearance", value = "token", domain = ".example.com", path = "/"),
            ),
        )

        assertEquals(
            listOf("cf_clearance=token"),
            jar.getCookieHeaderValues("https://play.example.com/embed/1"),
        )
        assertEquals(
            listOf("cf_clearance=token"),
            jar.getCookieHeaderValues("https://example.com/search"),
        )
        // 不同站点不可见
        assertEquals(emptyList(), jar.getCookieHeaderValues("https://other.com/"))
    }

    @Test
    fun `host only cookie matches www variant but not sibling subdomain`() = runTest {
        val jar = WebSourceCookieJar()
        // Android 降级路径: 无属性, 按页面 host (去 www) 存
        jar.addBrowserCookies(
            "https://www.example.com/search",
            listOf(BrowserCookie(name = "PHPSESSID", value = "abc")),
        )

        assertEquals(listOf("PHPSESSID=abc"), jar.getCookieHeaderValues("https://example.com/a"))
        assertEquals(listOf("PHPSESSID=abc"), jar.getCookieHeaderValues("https://www.example.com/a"))
        assertEquals(emptyList(), jar.getCookieHeaderValues("https://play.example.com/a"))
    }

    @Test
    fun `ktor storage roundtrip with expiry`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addCookie(
            Url("https://example.com/"),
            Cookie(name = "expired", value = "x", maxAge = -1),
        )
        jar.addCookie(
            Url("https://example.com/"),
            Cookie(name = "alive", value = "y"),
        )

        val cookies = jar.get(Url("https://example.com/"))
        assertEquals(listOf("alive"), cookies.map { it.name })
    }

    @Test
    fun `secure cookie is not sent over http`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies(
            "https://example.com/",
            listOf(BrowserCookie(name = "cf_clearance", value = "t", domain = ".example.com", secure = true)),
        )

        assertEquals(emptyList(), jar.getCookieHeaderValues("http://example.com/"))
        assertEquals(listOf("cf_clearance=t"), jar.getCookieHeaderValues("https://example.com/"))
    }

    @Test
    fun `clearForHost drops host and subdomain cookies`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies(
            "https://example.com/",
            listOf(
                BrowserCookie(name = "a", value = "1", domain = ".example.com"),
                BrowserCookie(name = "b", value = "2"),
            ),
        )
        jar.addBrowserCookies(
            "https://other.com/",
            listOf(BrowserCookie(name = "c", value = "3")),
        )

        jar.clearForHost("example.com")

        assertEquals(emptyList(), jar.getCookieHeaderValues("https://example.com/"))
        assertEquals(listOf("c=3"), jar.getCookieHeaderValues("https://other.com/"))
    }

    @Test
    fun `same name cookie is replaced`() = runTest {
        val jar = WebSourceCookieJar()
        jar.addBrowserCookies("https://example.com/", listOf(BrowserCookie(name = "k", value = "old")))
        jar.addBrowserCookies("https://example.com/", listOf(BrowserCookie(name = "k", value = "new")))

        assertEquals(listOf("k=new"), jar.getCookieHeaderValues("https://example.com/"))
    }

    // 关键用例 7: UA 覆写只作用于已 solve 的 host
    @Test
    fun `identity registry matches host and parent domain only`() {
        val registry = WebSourceIdentityRegistry()
        registry.setUserAgent("www.example.com", "RealBrowserUA/1.0")

        assertEquals("RealBrowserUA/1.0", registry.userAgentFor("example.com"))
        assertEquals("RealBrowserUA/1.0", registry.userAgentFor("www.example.com"))
        // 子域走父域 UA (cf_clearance 域级生效, UA 也须一致)
        assertEquals("RealBrowserUA/1.0", registry.userAgentFor("play.example.com"))
        // 未 solve 的 host 不覆写
        assertNull(registry.userAgentFor("other.com"))
        assertTrue(registry.userAgentFor("com") == null)
    }
}
