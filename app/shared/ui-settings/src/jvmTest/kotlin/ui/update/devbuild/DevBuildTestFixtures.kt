/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import me.him188.ani.app.platform.Context
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.io.SystemPath
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal const val SHA_A = "aaaaaaaa11111111aaaaaaaa11111111aaaaaaaa"
internal const val SHA_B = "bbbbbbbb22222222bbbbbbbb22222222bbbbbbbb"

internal const val COMMITS_JSON = """
[
  {
    "sha": "$SHA_A",
    "html_url": "https://github.com/open-ani/animeko/commit/$SHA_A",
    "commit": {
      "message": "feat(update): first line\n\nbody",
      "author": {"name": "Alice", "date": "2026-09-18T05:04:36Z"},
      "committer": {"name": "GitHub", "date": "2026-09-18T05:04:36Z"}
    },
    "author": {"login": "alice"}
  },
  {
    "sha": "$SHA_B",
    "html_url": "https://github.com/open-ani/animeko/commit/$SHA_B",
    "commit": {
      "message": "fix(ci): second",
      "author": {"name": "Bob", "date": "2026-09-17T05:04:36Z"}
    },
    "author": null
  }
]
"""

internal const val RUNS_JSON = """
{
  "total_count": 3,
  "workflow_runs": [
    {"id": 300, "head_sha": "$SHA_B", "head_branch": "main", "status": "in_progress", "conclusion": null, "html_url": "https://github.com/open-ani/animeko/actions/runs/300"},
    {"id": 200, "head_sha": "$SHA_A", "head_branch": "main", "status": "completed", "conclusion": "success", "html_url": "https://github.com/open-ani/animeko/actions/runs/200"},
    {"id": 100, "head_sha": "$SHA_A", "head_branch": "main", "status": "completed", "conclusion": "failure", "html_url": "https://github.com/open-ani/animeko/actions/runs/100"}
  ]
}
"""

internal fun artifactsJson(name: String) = """
{
  "total_count": 3,
  "artifacts": [
    {"id": 11, "name": "$name", "size_in_bytes": 5, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/11/zip", "expired": true,
     "workflow_run": {"id": 200, "head_sha": "$SHA_A", "head_branch": "main"}},
    {"id": 10, "name": "$name", "size_in_bytes": 1234, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/10/zip", "expired": false,
     "workflow_run": {"id": 200, "head_sha": "$SHA_A", "head_branch": "main"}},
    {"id": 9, "name": "$name", "size_in_bytes": 1, "archive_download_url": "https://api.github.com/repos/open-ani/animeko/actions/artifacts/9/zip", "expired": false,
     "workflow_run": {"id": 50, "head_sha": "cccccccc", "head_branch": "feature"}}
  ]
}
"""

internal fun zipBytes(vararg entries: Pair<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, bytes) in entries) {
            zip.putNextEntry(ZipEntry(name))
            if (!name.endsWith("/")) zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

internal fun MockRequestHandleScope.respondJson(json: String, status: HttpStatusCode = HttpStatusCode.OK): HttpResponseData =
    respond(json, status, headersOf(HttpHeaders.ContentType, "application/json"))

/**
 * 模拟 GitHub API 的 [HttpClient], 配置与 [DevBuildsViewModel] 中一致.
 */
internal fun gitHubMockClient(
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClient(MockEngine { request -> handler(request) }) {
    followRedirects = false
    expectSuccess = false
}

/**
 * 模拟完整的 GitHub 交互: 列表接口返回固定数据, artifact 10 的下载重定向到 [downloadUrl], 该地址返回 [archive].
 */
internal fun fullGitHubMockClient(
    artifactName: String,
    archive: ByteArray,
    expectedToken: String = "token",
    downloadUrl: String = "https://blob.example.com/artifact.zip",
): HttpClient = gitHubMockClient(fullGitHubMockHandler(artifactName, archive, expectedToken, downloadUrl))

internal fun fullGitHubMockHandler(
    artifactName: String,
    archive: ByteArray,
    expectedToken: String = "token",
    downloadUrl: String = "https://blob.example.com/artifact.zip",
): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { request ->
    val url = request.url
    val path = url.encodedPath
    when {
        url.host == "blob.example.com" -> {
            check(request.headers[HttpHeaders.Authorization] == null) { "download URL must not carry Authorization" }
            respond(archive, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, archive.size.toString()))
        }

        path.endsWith("/artifacts/10/zip") -> {
            if (request.headers[HttpHeaders.Authorization] != "Bearer $expectedToken") {
                respondJson("""{"message": "Bad credentials"}""", HttpStatusCode.Unauthorized)
            } else {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, downloadUrl))
            }
        }

        path.endsWith("/commits") -> respondJson(COMMITS_JSON)
        path.endsWith("/runs") -> respondJson(RUNS_JSON)
        path.endsWith("/actions/artifacts") -> {
            if (url.parameters["name"] == artifactName) respondJson(artifactsJson(artifactName))
            else respondJson("""{"total_count": 0, "artifacts": []}""")
        }

        else -> error("Unexpected request: $url")
    }
}

internal class FakeInstaller(
    private val result: InstallationResult = InstallationResult.Succeed,
) : UpdateInstaller {
    val installed = mutableListOf<SystemPath>()
    val revealed = mutableListOf<SystemPath>()

    override fun install(file: SystemPath, context: Context): InstallationResult {
        installed += file
        return result
    }

    override suspend fun openForManualInstallation(file: SystemPath, context: Context): Boolean {
        revealed += file
        return true
    }
}

internal val testContext = object : Context() {}
