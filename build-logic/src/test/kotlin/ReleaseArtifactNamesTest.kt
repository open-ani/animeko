/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.GradleException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReleaseArtifactNamesTest {
    private fun bundleVersion(tag: String): String = ReleaseArtifactNames.iosBundleVersionFromTag(tag)

    /** CFBundleVersion 按段数值比较, 这里模拟 iOS 的比较规则. */
    private fun components(bundleVersion: String): List<Int> = bundleVersion.split(".").map { it.toInt() }

    private fun compareBundleVersions(a: String, b: String): Int {
        val ca = components(a)
        val cb = components(b)
        for (i in 0 until maxOf(ca.size, cb.size)) {
            val diff = (ca.getOrElse(i) { 0 }).compareTo(cb.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    @Test
    fun `stable release`() {
        assertEquals("6.1.99", bundleVersion("v6.1.0"))
        assertEquals("5.5.299", bundleVersion("v5.5.2"))
        assertEquals("6.0.1099", bundleVersion("v6.0.10"))
    }

    @Test
    fun `alpha and beta`() {
        assertEquals("6.1.1", bundleVersion("v6.1.0-alpha01"))
        assertEquals("6.1.3", bundleVersion("v6.1.0-alpha03"))
        assertEquals("6.1.9", bundleVersion("v6.1.0-alpha09"))
        assertEquals("6.1.10", bundleVersion("v6.1.0-alpha10"))
        assertEquals("6.1.110", bundleVersion("v6.1.1-alpha10"))
        assertEquals("6.1.29", bundleVersion("v6.1.0-alpha29"))
        assertEquals("6.1.31", bundleVersion("v6.1.0-beta01"))
        assertEquals("6.1.59", bundleVersion("v6.1.0-beta29"))
        assertEquals("6.1.101", bundleVersion("v6.1.1-alpha01"))
        assertEquals("6.1.131", bundleVersion("v6.1.1-beta01"))
        // 不带前导零的写法也接受
        assertEquals("6.1.1", bundleVersion("v6.1.0-alpha1"))
    }

    @Test
    fun `multi digit components`() {
        assertEquals("10.12.1299", bundleVersion("v10.12.12"))
        assertEquals("6.10.99", bundleVersion("v6.10.0"))
        assertEquals("6.100.99", bundleVersion("v6.100.0"))
        assertEquals("6.1.10099", bundleVersion("v6.1.100"))
    }

    @Test
    fun `is a valid CFBundleVersion`() {
        // 1 到 3 段, 只含数字和点, 第一段大于 0, 每段不带前导零
        for (tag in listOf("v6.1.0-alpha01", "v6.1.0-beta01", "v6.1.0", "v6.0.0", "v10.12.12", "v6.0.10-alpha05")) {
            val v = bundleVersion(tag)
            assertTrue(v matches Regex("""[1-9]\d*(\.(0|[1-9]\d*)){2}"""), "$tag -> $v")
        }
    }

    @Test
    fun `strictly increases in release order`() {
        val tags = listOf(
            "v6.1.0-alpha01", "v6.1.0-alpha02", "v6.1.0-alpha03", "v6.1.0-alpha09", "v6.1.0-alpha10", "v6.1.0-alpha11", "v6.1.0-alpha29",
            "v6.1.0-beta01", "v6.1.0-beta02", "v6.1.0-beta10", "v6.1.0-beta29",
            "v6.1.0",
            "v6.1.1-alpha01", "v6.1.1-beta01", "v6.1.1",
            "v6.1.2", "v6.1.10",
            "v6.2.0-alpha01", "v6.2.0",
            "v6.10.0-alpha01", "v6.10.0",
            "v7.0.0-alpha01", "v7.0.0",
            "v10.0.0-alpha01", "v10.0.0",
        )
        val versions = tags.map(::bundleVersion)
        versions.zipWithNext().forEachIndexed { i, (a, b) ->
            assertTrue(compareBundleVersions(a, b) < 0, "${tags[i]}=$a must be < ${tags[i + 1]}=$b")
        }
    }

    @Test
    fun `rejects unsupported tags`() {
        assertFailsWith<GradleException> { bundleVersion("6.1.0") }
        assertFailsWith<GradleException> { bundleVersion("v6.1") }
        assertFailsWith<GradleException> { bundleVersion("v6.1.0-rc01") }
        assertFailsWith<GradleException> { bundleVersion("v6.1.0-dev") }
        assertFailsWith<IllegalArgumentException> { bundleVersion("v0.1.0") }
        assertFailsWith<IllegalArgumentException> { bundleVersion("v6.1.0-alpha00") }
        assertFailsWith<IllegalArgumentException> { bundleVersion("v6.1.0-alpha30") }
        assertFailsWith<IllegalArgumentException> { bundleVersion("v6.1.0-beta30") }
    }
}
