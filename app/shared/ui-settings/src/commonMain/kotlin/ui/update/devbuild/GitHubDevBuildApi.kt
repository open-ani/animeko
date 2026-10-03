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
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentLength
import io.ktor.http.takeFrom
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.DEFAULT_BUFFER_SIZE
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.bufferedSink
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger

/**
 * 查询 GitHub 仓库的 commits, PR, Build workflow 的运行记录和上传的 artifacts, 以及下载 artifact 和安装包.
 *
 * 列表接口无需登录, 但匿名请求的速率限制很低 (每小时 60 次); 下载 artifact 必须提供 token.
 *
 * @param client 必须关闭自动跟随重定向 (`followRedirects = false`) 且不抛出非 2xx 异常 (`expectSuccess = false`):
 * artifact 下载接口返回 302 指向一个短期有效的直链, 该直链不能再附带 `Authorization` 头, 所以由本类手动处理重定向.
 */
class GitHubDevBuildApi(
    private val client: HttpClient,
    /**
     * `owner/name`
     */
    val repository: String = DEFAULT_REPOSITORY,
    private val branch: String = DEFAULT_BRANCH,
    private val workflowFileName: String = DEFAULT_WORKFLOW_FILE_NAME,
    private val apiBaseUrl: String = "https://api.github.com",
) {
    /**
     * [branch] 上最新的 commits, 新的在前.
     */
    suspend fun listCommits(token: String?, perPage: Int = 30): List<GitHubCommit> {
        val text = getText(token, "$apiBaseUrl/repos/$repository/commits") {
            parameter("sha", branch)
            parameter("per_page", perPage)
        }
        return json.decodeFromString(ListSerializer(GitHubCommit.serializer()), text)
    }

    /**
     * 单个 commit. [ref] 可以是完整或缩写的 sha, 返回的 [GitHubCommit.sha] 总是完整的.
     */
    suspend fun getCommit(token: String?, ref: String): GitHubCommit {
        val text = getText(token, "$apiBaseUrl/repos/$repository/commits/$ref") {}
        return json.decodeFromString(GitHubCommit.serializer(), text)
    }

    suspend fun getPullRequest(token: String?, number: Int): GitHubPullRequest {
        val text = getText(token, "$apiBaseUrl/repos/$repository/pulls/$number") {}
        return json.decodeFromString(GitHubPullRequest.serializer(), text)
    }

    /**
     * [branch] 上由 push 触发的 Build workflow 运行记录, 新的在前.
     */
    suspend fun listWorkflowRuns(token: String?, perPage: Int = 50): List<GitHubWorkflowRun> {
        val text = getText(token, "$apiBaseUrl/repos/$repository/actions/workflows/$workflowFileName/runs") {
            parameter("branch", branch)
            parameter("event", "push")
            parameter("per_page", perPage)
        }
        return json.decodeFromString(WorkflowRunsResponse.serializer(), text).workflowRuns
    }

    /**
     * 以 [sha] 为 head 的 Build workflow 运行记录, 不限分支和触发事件, 新的在前. [sha] 必须是完整的.
     */
    suspend fun listWorkflowRunsForCommit(token: String?, sha: String, perPage: Int = 20): List<GitHubWorkflowRun> {
        val text = getText(token, "$apiBaseUrl/repos/$repository/actions/workflows/$workflowFileName/runs") {
            parameter("head_sha", sha)
            parameter("per_page", perPage)
        }
        return json.decodeFromString(WorkflowRunsResponse.serializer(), text).workflowRuns
    }

    suspend fun getWorkflowRun(token: String?, runId: Long): GitHubWorkflowRun {
        val text = getText(token, "$apiBaseUrl/repos/$repository/actions/runs/$runId") {}
        return json.decodeFromString(GitHubWorkflowRun.serializer(), text)
    }

    /**
     * 某次 workflow 运行上传的全部 artifacts.
     */
    suspend fun listRunArtifacts(token: String?, runId: Long, perPage: Int = 100): List<GitHubArtifact> {
        val text = getText(token, "$apiBaseUrl/repos/$repository/actions/runs/$runId/artifacts") {
            parameter("per_page", perPage)
        }
        return json.decodeFromString(ArtifactsResponse.serializer(), text).artifacts
    }

    suspend fun getArtifact(token: String?, artifactId: Long): GitHubArtifact {
        val text = getText(token, "$apiBaseUrl/repos/$repository/actions/artifacts/$artifactId") {}
        return json.decodeFromString(GitHubArtifact.serializer(), text)
    }

    /**
     * 仓库内名为 [name] 的 artifacts (所有分支), 新的在前.
     */
    suspend fun listArtifacts(token: String?, name: String, perPage: Int = 100): List<GitHubArtifact> {
        val text = getText(token, "$apiBaseUrl/repos/$repository/actions/artifacts") {
            parameter("name", name)
            parameter("per_page", perPage)
        }
        return json.decodeFromString(ArtifactsResponse.serializer(), text).artifacts
    }

    /**
     * 取得 artifact zip 的直链. 直链短期有效, 需立即开始下载.
     */
    suspend fun resolveArtifactDownloadUrl(token: String, archiveDownloadUrl: String): String {
        val response = client.get(archiveDownloadUrl) {
            configureApiRequest(token)
        }
        if (response.status.value in 300..399) {
            return response.headers[HttpHeaders.Location]
                ?: throw GitHubApiException(response.status, "GitHub 返回了重定向但没有 Location 头")
        }
        throw response.toApiException()
    }

    /**
     * 下载 [url] 到 [target], 不附带 token. 跟随最多 [MAX_DOWNLOAD_REDIRECTS] 次重定向 (Release 附件的直链会重定向到对象存储).
     * [onProgress] 在下载过程中周期性回调, 完成时最后回调一次.
     */
    suspend fun downloadFile(
        url: String,
        target: SystemPath,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        var currentUrl = url
        var redirects = 0
        while (true) {
            val redirectedTo = downloadFileOnce(currentUrl, target, onProgress) ?: return
            if (++redirects > MAX_DOWNLOAD_REDIRECTS) {
                throw GitHubApiException(HttpStatusCode.Found, "重定向次数过多: $url")
            }
            currentUrl = resolveRedirect(currentUrl, redirectedTo)
        }
    }

    /**
     * 按 RFC 3986 把 `Location` 头 [location] 解析为绝对地址: 绝对地址原样使用, 相对地址基于 [base] 解析且不继承 [base] 的 query.
     */
    private fun resolveRedirect(base: String, location: String): String {
        if (ABSOLUTE_URL_REGEX.containsMatchIn(location)) return location
        return URLBuilder(base).apply {
            parameters.clear()
            fragment = ""
            takeFrom(location)
        }.buildString()
    }

    /**
     * @return 需要跟随的重定向地址; 下载完成时为 `null`.
     */
    private suspend fun downloadFileOnce(
        url: String,
        target: SystemPath,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): String? {
        return client.prepareGet(url) {
            timeout {
                requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS
            }
        }.execute { response ->
            if (response.status.value in 300..399) {
                return@execute response.headers[HttpHeaders.Location]
                    ?: throw GitHubApiException(response.status, "服务器返回了重定向但没有 Location 头")
            }
            if (!response.status.isSuccess()) {
                throw response.toApiException()
            }
            val total = response.contentLength()
            logger.info { "Downloading dev build package, total=$total, target=$target" }
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var downloaded = 0L
            var lastReported = 0L
            withContext(Dispatchers.IO_) {
                target.bufferedSink().use { sink ->
                    while (true) {
                        val read = channel.readAvailable(buffer)
                        if (read == -1) break
                        sink.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReported >= PROGRESS_REPORT_INTERVAL_BYTES) {
                            lastReported = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
            onProgress(downloaded, total ?: downloaded)
            null
        }
    }

    private suspend fun getText(
        token: String?,
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ): String {
        val response = client.get(url) {
            configureApiRequest(token)
            block()
        }
        if (!response.status.isSuccess()) {
            throw response.toApiException()
        }
        return response.bodyAsText()
    }

    private fun HttpRequestBuilder.configureApiRequest(token: String?) {
        header(HttpHeaders.Accept, "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
        if (!token.isNullOrBlank()) {
            header(HttpHeaders.Authorization, "Bearer ${token.trim()}")
        }
    }

    private suspend fun HttpResponse.toApiException(): GitHubApiException {
        val body = runCatching { bodyAsText() }.getOrDefault("")
        val message = runCatching {
            json.parseToJsonElement(body).jsonObject["message"]?.jsonPrimitive?.content
        }.getOrNull()
        val rateLimitRemaining = headers["x-ratelimit-remaining"]
        val isRateLimited = status == HttpStatusCode.TooManyRequests ||
                (status == HttpStatusCode.Forbidden && rateLimitRemaining == "0")
        return GitHubApiException(
            status = status,
            message = message ?: body.take(200).ifBlank { status.toString() },
            isRateLimited = isRateLimited,
        )
    }

    private fun HttpStatusCode.isSuccess(): Boolean = value in 200..299

    companion object {
        const val DEFAULT_REPOSITORY = "open-ani/animeko"
        const val DEFAULT_BRANCH = "main"
        const val DEFAULT_WORKFLOW_FILE_NAME = "build.yml"

        private const val DOWNLOAD_TIMEOUT_MILLIS = 1_000_000L
        private const val MAX_DOWNLOAD_REDIRECTS = 5
        private val ABSOLUTE_URL_REGEX = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*://""")
        private const val PROGRESS_REPORT_INTERVAL_BYTES = 512 * 1024L

        private val logger = logger<GitHubDevBuildApi>()
        private val json = Json {
            ignoreUnknownKeys = true
        }
    }
}

/**
 * GitHub API 返回了非成功状态.
 *
 * @param isRateLimited 是否因为触发速率限制. 匿名请求每小时只能调用 60 次, 提供 token 后为 5000 次.
 */
class GitHubApiException(
    val status: HttpStatusCode,
    message: String,
    val isRateLimited: Boolean = false,
) : Exception("GitHub API ${status.value}: $message") {
    val isUnauthorized: Boolean get() = status == HttpStatusCode.Unauthorized
    val isNotFound: Boolean get() = status == HttpStatusCode.NotFound
}

@Serializable
data class GitHubCommit(
    val sha: String,
    val commit: Detail,
    @SerialName("html_url") val htmlUrl: String = "",
    val author: User? = null,
) {
    @Serializable
    data class Detail(
        val message: String = "",
        val author: Person? = null,
        val committer: Person? = null,
    )

    @Serializable
    data class Person(
        val name: String = "",
        /**
         * ISO-8601, 例如 `2026-09-18T05:04:36Z`
         */
        val date: String = "",
    )

    @Serializable
    data class User(
        val login: String = "",
    )
}

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    @SerialName("head_sha") val headSha: String,
    @SerialName("head_branch") val headBranch: String? = null,
    /**
     * `queued`, `in_progress`, `completed`, `waiting`, `pending`, `requested`
     */
    val status: String? = null,
    /**
     * [status] 为 `completed` 时: `success`, `failure`, `cancelled`, `skipped`, `timed_out`, `action_required`, `startup_failure`, ...
     */
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("run_attempt") val runAttempt: Int = 1,
)

@Serializable
data class GitHubPullRequest(
    val number: Int,
    val title: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val head: Head,
) {
    @Serializable
    data class Head(
        val sha: String,
        val ref: String = "",
        /**
         * 分支所在的仓库. fork 已被删除时为 `null`.
         */
        val repo: Repo? = null,
    )

    @Serializable
    data class Repo(
        @SerialName("full_name") val fullName: String = "",
    )
}

@Serializable
private data class WorkflowRunsResponse(
    @SerialName("workflow_runs") val workflowRuns: List<GitHubWorkflowRun> = emptyList(),
)

@Serializable
data class GitHubArtifact(
    val id: Long,
    val name: String,
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0,
    @SerialName("archive_download_url") val archiveDownloadUrl: String,
    val expired: Boolean = false,
    @SerialName("workflow_run") val workflowRun: Run? = null,
) {
    @Serializable
    data class Run(
        val id: Long,
        @SerialName("head_sha") val headSha: String,
        @SerialName("head_branch") val headBranch: String? = null,
    )
}

@Serializable
private data class ArtifactsResponse(
    val artifacts: List<GitHubArtifact> = emptyList(),
)
