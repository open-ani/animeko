/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DevBuildInputTest {
    private fun parse(text: String) = parseDevBuildInput(text, "open-ani/animeko")

    @Test
    fun `parses commit sha and commit links`() {
        assertEquals(DevBuildInput.Commit("28ec14ac"), parse("28EC14AC"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("  $SHA_A\n"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/open-ani/animeko/commit/$SHA_A"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/open-ani/animeko/commit/$SHA_A?diff=split#r1"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/open-ani/animeko/pull/12/commits/$SHA_A"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://api.github.com/repos/open-ani/animeko/commits/$SHA_A"))
        assertNull(parse("28ec14"), "too short for a sha")
        assertNull(parse("https://github.com/open-ani/animeko/commit/not-a-sha"))
    }

    @Test
    fun `parses pull request numbers and links`() {
        assertEquals(DevBuildInput.PullRequest(123), parse("123"))
        assertEquals(DevBuildInput.PullRequest(123), parse("#123"))
        assertEquals(DevBuildInput.PullRequest(123), parse("https://github.com/open-ani/animeko/pull/123"))
        assertEquals(DevBuildInput.PullRequest(123), parse("https://github.com/open-ani/animeko/pull/123/files"))
        assertEquals(DevBuildInput.PullRequest(123), parse("https://api.github.com/repos/open-ani/animeko/pulls/123"))
    }

    @Test
    fun `parses workflow run and artifact links`() {
        assertEquals(
            DevBuildInput.WorkflowRun(1234),
            parse("https://github.com/open-ani/animeko/actions/runs/1234"),
        )
        assertEquals(
            DevBuildInput.WorkflowRun(1234),
            parse("https://github.com/open-ani/animeko/actions/runs/1234/job/5678"),
        )
        assertEquals(
            DevBuildInput.Artifact(99),
            parse("https://github.com/open-ani/animeko/actions/runs/1234/artifacts/99"),
        )
        assertEquals(
            DevBuildInput.Artifact(99),
            parse("https://api.github.com/repos/open-ani/animeko/actions/artifacts/99/zip"),
        )
        assertEquals(
            DevBuildInput.WorkflowRun(1234),
            parse("https://api.github.com/repos/open-ani/animeko/actions/runs/1234"),
        )
    }

    @Test
    fun `parses direct package links by the last path segment`() {
        val url = "https://github.com/open-ani/animeko/releases/download/v4.12.0/ani-4.12.0-macos-aarch64.dmg"
        assertEquals(DevBuildInput.PackageUrl(url, "ani-4.12.0-macos-aarch64.dmg"), parse(url))
        assertEquals(
            DevBuildInput.PackageUrl("https://example.com/a/b/Ani%20Dev.dmg?token=1", "Ani Dev.dmg"),
            parse("https://example.com/a/b/Ani%20Dev.dmg?token=1"),
        )
        assertNull(parse("https://example.com/"), "no file name")
        assertNull(parse("https://example.com/downloads"), "no extension")
        assertNull(parse("ftp://example.com/a.dmg"), "not http")
    }

    @Test
    fun `rejects other repositories and unknown input`() {
        assertNull(parse(""))
        assertNull(parse("hello world"))
        assertNull(parse("https://github.com/other/repo/commit/$SHA_A"))
        assertNull(parse("https://github.com/other/repo/pull/1"))
        assertNull(parse("https://github.com/open-ani/animeko/issues/1"))
        assertEquals(DevBuildInput.Commit(SHA_A), parse("https://github.com/Open-Ani/Animeko/commit/$SHA_A"))
    }
}
