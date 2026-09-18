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
import kotlin.time.Instant

class DevBuildCommitTest {
    private val spec = DevBuildPackageSpec(
        listOf("ani-android-arm64-v8a-release", "ani-android-universal-release"),
        DevBuildPackageKind.ANDROID_APK,
    )

    private fun commit(sha: String, message: String = "msg", login: String? = "user", date: String = "2026-09-18T05:04:36Z") =
        GitHubCommit(
            sha = sha,
            commit = GitHubCommit.Detail(
                message = message,
                author = GitHubCommit.Person("Name", date),
                committer = GitHubCommit.Person("GitHub", date),
            ),
            htmlUrl = "https://example.com/$sha",
            author = login?.let { GitHubCommit.User(it) },
        )

    private fun run(id: Long, sha: String, status: String, conclusion: String? = null) =
        GitHubWorkflowRun(id = id, headSha = sha, headBranch = "main", status = status, conclusion = conclusion, htmlUrl = "run/$id")

    private fun artifact(id: Long, sha: String, name: String, expired: Boolean = false) =
        GitHubArtifact(
            id = id,
            name = name,
            sizeInBytes = 100,
            archiveDownloadUrl = "artifact/$id/zip",
            expired = expired,
            workflowRun = GitHubArtifact.Run(id = 1, headSha = sha, headBranch = "main"),
        )

    @Test
    fun `workflow run status maps to build status`() {
        assertEquals(DevBuildStatus.QUEUED, DevBuildStatus.fromWorkflowRun("queued", null))
        assertEquals(DevBuildStatus.QUEUED, DevBuildStatus.fromWorkflowRun("waiting", null))
        assertEquals(DevBuildStatus.IN_PROGRESS, DevBuildStatus.fromWorkflowRun("in_progress", null))
        assertEquals(DevBuildStatus.SUCCESS, DevBuildStatus.fromWorkflowRun("completed", "success"))
        assertEquals(DevBuildStatus.FAILURE, DevBuildStatus.fromWorkflowRun("completed", "failure"))
        assertEquals(DevBuildStatus.FAILURE, DevBuildStatus.fromWorkflowRun("completed", "timed_out"))
        assertEquals(DevBuildStatus.CANCELLED, DevBuildStatus.fromWorkflowRun("completed", "cancelled"))
        assertEquals(DevBuildStatus.CANCELLED, DevBuildStatus.fromWorkflowRun("completed", "skipped"))
        assertEquals(DevBuildStatus.OTHER, DevBuildStatus.fromWorkflowRun("completed", "neutral"))
        assertEquals(DevBuildStatus.OTHER, DevBuildStatus.fromWorkflowRun(null, null))
    }

    @Test
    fun `merges commits with their latest run and preferred artifact in commit order`() {
        val result = buildDevBuildCommits(
            commits = listOf(commit(SHA_A, "feat: title\n\nbody"), commit(SHA_B, "fix: b", login = null)),
            runs = listOf(
                run(100, SHA_A, "completed", "failure"),
                run(200, SHA_A, "completed", "success"),
                run(300, SHA_B, "in_progress"),
            ),
            artifacts = listOf(
                artifact(1, SHA_A, "ani-android-universal-release"),
                artifact(2, SHA_A, "ani-android-arm64-v8a-release"),
                artifact(3, SHA_A, "ani-android-arm64-v8a-release", expired = true),
                artifact(4, SHA_A, "ani-windows-portable"),
                artifact(5, SHA_B, "ani-android-arm64-v8a-release", expired = true),
            ),
            spec = spec,
        )

        assertEquals(listOf(SHA_A, SHA_B), result.map { it.sha })

        val a = result[0]
        assertEquals("feat: title", a.title)
        assertEquals("user", a.author)
        assertEquals("aaaaaaaa", a.shortSha)
        assertEquals(Instant.parse("2026-09-18T05:04:36Z"), a.committedAt)
        assertEquals(DevBuildRun(200, DevBuildStatus.SUCCESS, "run/200"), a.build)
        assertEquals(DevBuildArtifact(2, "ani-android-arm64-v8a-release", 100, "artifact/2/zip"), a.artifact)

        val b = result[1]
        assertEquals("Name", b.author)
        assertEquals(DevBuildRun(300, DevBuildStatus.IN_PROGRESS, "run/300"), b.build)
        assertNull(b.artifact)
    }

    @Test
    fun `falls back to the universal artifact when the abi specific one is missing`() {
        val result = buildDevBuildCommits(
            commits = listOf(commit(SHA_A)),
            runs = emptyList(),
            artifacts = listOf(artifact(7, SHA_A, "ani-android-universal-release")),
            spec = spec,
        )
        assertNull(result.single().build)
        assertEquals(7, result.single().artifact?.id)
    }

    @Test
    fun `newer artifact wins among the same name`() {
        val result = buildDevBuildCommits(
            commits = listOf(commit(SHA_A)),
            runs = emptyList(),
            artifacts = listOf(
                artifact(7, SHA_A, "ani-android-arm64-v8a-release"),
                artifact(9, SHA_A, "ani-android-arm64-v8a-release"),
            ),
            spec = spec,
        )
        assertEquals(9, result.single().artifact?.id)
    }

    @Test
    fun `parses the main branch sha from dev version names only`() {
        assertEquals("28ec14ac", parseMainBranchShortSha("4.12.0-main-28ec14ac"))
        assertEquals("abcdef12", parseMainBranchShortSha("4.12.0-main-ABCDEF12"))
        assertNull(parseMainBranchShortSha("4.12.0"))
        assertNull(parseMainBranchShortSha("4.12.0-beta01"))
        assertNull(parseMainBranchShortSha("4.12.0-feature-28ec14ac"))
        assertNull(parseMainBranchShortSha("4.12.0-main-zz"))
    }
}
