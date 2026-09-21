/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import me.him188.ani.utils.io.SystemPaths
import me.him188.ani.utils.io.createTempDirectory
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.readBytes
import me.him188.ani.utils.io.resolve
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitHubDevBuildApiTest {
    @Test
    fun `lists commits of the branch with github headers`() = runTest {
        var authorization: String? = "unset"
        val client = gitHubMockClient { request ->
            assertEquals("/repos/open-ani/animeko/commits", request.url.encodedPath)
            assertEquals("main", request.url.parameters["sha"])
            assertEquals("30", request.url.parameters["per_page"])
            assertEquals("application/vnd.github+json", request.headers[HttpHeaders.Accept])
            authorization = request.headers[HttpHeaders.Authorization]
            respondJson(COMMITS_JSON)
        }
        val api = GitHubDevBuildApi(client)

        val commits = api.listCommits(token = null)
        assertNull(authorization)
        assertEquals(listOf(SHA_A, SHA_B), commits.map { it.sha })
        assertEquals("alice", commits[0].author?.login)
        assertEquals("feat(update): first line\n\nbody", commits[0].commit.message)
        assertNull(commits[1].author)

        api.listCommits(token = " token ")
        assertEquals("Bearer token", authorization)
    }

    @Test
    fun `lists push runs of the build workflow on the branch`() = runTest {
        val client = gitHubMockClient { request ->
            assertEquals("/repos/open-ani/animeko/actions/workflows/build.yml/runs", request.url.encodedPath)
            assertEquals("main", request.url.parameters["branch"])
            assertEquals("push", request.url.parameters["event"])
            respondJson(RUNS_JSON)
        }
        val runs = GitHubDevBuildApi(client).listWorkflowRuns(token = null)
        assertEquals(listOf(300L, 200L, 100L), runs.map { it.id })
        assertEquals("in_progress", runs[0].status)
        assertNull(runs[0].conclusion)
        assertEquals("success", runs[1].conclusion)
    }

    @Test
    fun `lists artifacts by name`() = runTest {
        val client = gitHubMockClient { request ->
            assertEquals("/repos/open-ani/animeko/actions/artifacts", request.url.encodedPath)
            assertEquals("ani-macos-dmg-aarch64", request.url.parameters["name"])
            respondJson(artifactsJson("ani-macos-dmg-aarch64"))
        }
        val artifacts = GitHubDevBuildApi(client).listArtifacts(token = null, name = "ani-macos-dmg-aarch64")
        assertEquals(listOf(11L, 10L, 9L), artifacts.map { it.id })
        assertTrue(artifacts[0].expired)
        assertEquals(SHA_A, artifacts[1].workflowRun?.headSha)
        assertEquals(1234, artifacts[1].sizeInBytes)
    }

    @Test
    fun `api errors carry the github message and rate limit info`() = runTest {
        val client = gitHubMockClient { _ ->
            respond(
                """{"message": "API rate limit exceeded"}""",
                HttpStatusCode.Forbidden,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "x-ratelimit-remaining" to listOf("0"),
                ),
            )
        }
        val e = assertFailsWith<GitHubApiException> { GitHubDevBuildApi(client).listCommits(null) }
        assertEquals(HttpStatusCode.Forbidden, e.status)
        assertTrue(e.isRateLimited)
        assertEquals("GitHub API 403: API rate limit exceeded", e.message)
    }

    @Test
    fun `artifact download url comes from the redirect and requires a valid token`() = runTest {
        val client = gitHubMockClient { request ->
            if (request.headers[HttpHeaders.Authorization] == "Bearer good") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://blob.example.com/a.zip"))
            } else {
                respondJson("""{"message": "Bad credentials"}""", HttpStatusCode.Unauthorized)
            }
        }
        val api = GitHubDevBuildApi(client)
        assertEquals(
            "https://blob.example.com/a.zip",
            api.resolveArtifactDownloadUrl("good", "https://api.github.com/repos/open-ani/animeko/actions/artifacts/10/zip"),
        )
        val e = assertFailsWith<GitHubApiException> {
            api.resolveArtifactDownloadUrl("bad", "https://api.github.com/repos/open-ani/animeko/actions/artifacts/10/zip")
        }
        assertTrue(e.isUnauthorized)
    }

    @Test
    fun `downloads a file and reports the final progress`() = runTest {
        val content = ByteArray(3000) { it.toByte() }
        val client = gitHubMockClient { request ->
            assertNull(request.headers[HttpHeaders.Authorization])
            respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, content.size.toString()))
        }
        val dir = SystemPaths.createTempDirectory("dev-build-api-test")
        try {
            val target = dir.resolve("a.zip")
            val reports = mutableListOf<Pair<Long, Long?>>()
            GitHubDevBuildApi(client).downloadFile("https://blob.example.com/a.zip", target) { downloaded, total ->
                reports += downloaded to total
            }
            assertContentEquals(content, target.readBytes())
            assertEquals(3000L to 3000L, reports.last())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `download failure surfaces the status`() = runTest {
        val client = gitHubMockClient { _ -> respond("gone", HttpStatusCode.NotFound) }
        val dir = SystemPaths.createTempDirectory("dev-build-api-test")
        try {
            val e = assertFailsWith<GitHubApiException> {
                GitHubDevBuildApi(client).downloadFile("https://blob.example.com/a.zip", dir.resolve("a.zip"))
            }
            assertEquals(HttpStatusCode.NotFound, e.status)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `download follows relative and absolute redirects and rejects loops`() = runTest {
        val content = byteArrayOf(1, 2, 3)
        val visited = mutableListOf<String>()
        val client = gitHubMockClient { request ->
            visited += request.url.toString()
            when (request.url.encodedPath) {
                "/start" -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/next?x=1"))
                "/next" -> respond(
                    "",
                    HttpStatusCode.Found,
                    headersOf(HttpHeaders.Location, "https://objects.example.com/final"),
                )

                "/final" -> respond(content, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "3"))
                "/loop" -> respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/loop"))
                else -> error("Unexpected request: ${request.url}")
            }
        }
        val dir = SystemPaths.createTempDirectory("dev-build-api-test")
        try {
            val target = dir.resolve("a.dmg")
            GitHubDevBuildApi(client).downloadFile("https://github.com/start", target)
            assertContentEquals(content, target.readBytes())
            assertEquals(
                listOf("https://github.com/start", "https://github.com/next?x=1", "https://objects.example.com/final"),
                visited,
            )

            assertFailsWith<GitHubApiException> {
                GitHubDevBuildApi(client).downloadFile("https://github.com/loop", dir.resolve("b.dmg"))
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `fetches single commit, pull request, run and artifact`() = runTest {
        val client = fullGitHubMockClient("ani-macos-dmg-aarch64", zipBytes())
        val api = GitHubDevBuildApi(client)

        assertEquals(SHA_A, api.getCommit(null, SHA_A.take(7)).sha)
        val pr = api.getPullRequest(null, 42)
        assertEquals(SHA_A, pr.head.sha)
        assertEquals("open-ani/animeko", pr.head.repo?.fullName)
        assertEquals(listOf(200L, 100L), api.listWorkflowRunsForCommit(null, SHA_A).map { it.id })
        assertEquals("success", api.getWorkflowRun(null, 200).conclusion)
        assertEquals(listOf(11L, 10L), api.listRunArtifacts(null, 200).map { it.id })
        assertEquals("ani-macos-dmg-aarch64", api.getArtifact(null, 10).name)
        assertTrue(assertFailsWith<GitHubApiException> { api.getArtifact(null, 999) }.isNotFound)
    }
}
