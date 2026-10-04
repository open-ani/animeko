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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
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
import me.him188.ani.utils.io.bufferedSource
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolveSibling
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi

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
     * 服务器支持 Range 时分段并行下载, 否则单连接下载.
     * [onProgress] 在下载过程中周期性回调, 分段下载时可能从多个线程同时回调; 完成时最后回调一次.
     */
    suspend fun downloadFile(
        url: String,
        target: SystemPath,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) {
        var currentUrl = url
        var redirects = 0
        while (true) {
            when (val probe = probeDownload(currentUrl, target, onProgress)) {
                is DownloadProbe.Redirect -> {
                    if (++redirects > MAX_DOWNLOAD_REDIRECTS) {
                        throw GitHubApiException(HttpStatusCode.Found, "重定向次数过多: $url")
                    }
                    currentUrl = resolveRedirect(currentUrl, probe.location)
                }

                is DownloadProbe.RangeSupported -> return downloadInParts(currentUrl, target, probe.totalBytes, onProgress)
                DownloadProbe.Completed -> return
            }
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

    private sealed interface DownloadProbe {
        class Redirect(val location: String) : DownloadProbe
        class RangeSupported(val totalBytes: Long) : DownloadProbe

        /**
         * 服务器忽略了 Range, 完整的文件已经写入.
         */
        data object Completed : DownloadProbe
    }

    /**
     * 以 `Range: bytes=0-0` 请求 [url], 得知服务器是否支持分段下载以及文件大小. 服务器忽略 Range 时直接把完整响应写入 [target].
     *
     * 不用 HEAD 探测: 预签名直链的签名可能绑定了 GET 方法.
     */
    private suspend fun probeDownload(
        url: String,
        target: SystemPath,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): DownloadProbe {
        return client.prepareGet(url) {
            header(HttpHeaders.Range, "bytes=0-0")
            timeout {
                requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS
            }
        }.execute { response ->
            when {
                response.status.value in 300..399 -> DownloadProbe.Redirect(
                    response.headers[HttpHeaders.Location]
                        ?: throw GitHubApiException(response.status, "服务器返回了重定向但没有 Location 头"),
                )

                response.status == HttpStatusCode.PartialContent -> {
                    // Content-Range: bytes 0-0/<total>
                    val totalBytes = response.headers[HttpHeaders.ContentRange]
                        ?.substringAfterLast('/')?.toLongOrNull()?.takeIf { it > 0 }
                        ?: throw GitHubApiException(response.status, "服务器返回的 Content-Range 没有文件大小")
                    DownloadProbe.RangeSupported(totalBytes)
                }

                response.status.isSuccess() -> {
                    val total = response.contentLength()
                    logger.info { "Downloading dev build package in one connection, total=$total, target=$target" }
                    val progress = DownloadProgress(total, onProgress)
                    writeBody(response, target, progress::add)
                    progress.complete()
                    DownloadProbe.Completed
                }

                else -> throw response.toApiException()
            }
        }
    }

    /**
     * 把 [url] 处共 [totalBytes] 字节的文件分成至多 [DOWNLOAD_PARTS] 段同时下载.
     * 第一段直接写入 [target], 其余各段写入旁边的临时文件, 全部完成后依次追加到 [target].
     */
    private suspend fun downloadInParts(
        url: String,
        target: SystemPath,
        totalBytes: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        val partCount = (totalBytes / MIN_PART_BYTES).coerceIn(1, DOWNLOAD_PARTS.toLong()).toInt()
        val partSize = (totalBytes + partCount - 1) / partCount
        val ranges = List(partCount) { i -> i * partSize..<minOf((i + 1) * partSize, totalBytes) }
        val partFiles = List(partCount) { i -> if (i == 0) target else target.resolveSibling("${target.name}.part$i") }
        logger.info { "Downloading dev build package in $partCount parts, total=$totalBytes, target=$target" }

        val progress = DownloadProgress(totalBytes, onProgress)
        try {
            coroutineScope {
                for (i in 0 until partCount) {
                    launch { downloadPart(url, ranges[i], partFiles[i], progress::add) }
                }
            }
            withContext(Dispatchers.IO_) {
                target.bufferedSink(append = true).use { sink ->
                    for (part in partFiles.drop(1)) {
                        part.bufferedSource().use { it.transferTo(sink) }
                        // 边拼接边删除, 磁盘峰值占用约为文件大小加一段
                        part.delete()
                    }
                }
            }
        } finally {
            partFiles.drop(1).forEach { it.delete() }
        }
        progress.complete()
    }

    private suspend fun downloadPart(url: String, range: LongRange, file: SystemPath, onRead: (Int) -> Unit) {
        client.prepareGet(url) {
            header(HttpHeaders.Range, "bytes=${range.first}-${range.last}")
            timeout {
                requestTimeoutMillis = DOWNLOAD_TIMEOUT_MILLIS
            }
        }.execute { response ->
            when {
                response.status == HttpStatusCode.PartialContent -> {}
                // 不读响应体: 服务器没按 Range 返回时响应体是整个文件
                response.status.isSuccess() -> throw GitHubApiException(response.status, "服务器没有按 Range 返回分段")
                else -> throw response.toApiException()
            }
            val written = writeBody(response, file, onRead)
            val expected = range.last - range.first + 1
            if (written != expected) {
                throw GitHubApiException(response.status, "分段 $range 应有 $expected 字节, 实际收到 $written 字节")
            }
        }
    }

    /**
     * 把 [response] 的响应体写入 [target], 每读到一批数据就以其字节数回调 [onRead].
     * @return 写入的总字节数
     */
    private suspend fun writeBody(response: HttpResponse, target: SystemPath, onRead: (Int) -> Unit): Long {
        val channel = response.bodyAsChannel()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var written = 0L
        withContext(Dispatchers.IO_) {
            target.bufferedSink().use { sink ->
                while (true) {
                    val read = channel.readAvailable(buffer)
                    if (read == -1) break
                    sink.write(buffer, 0, read)
                    written += read
                    onRead(read)
                }
            }
        }
        return written
    }

    /**
     * 累计各连接读到的字节数, 每跨过一个 [PROGRESS_REPORT_INTERVAL_BYTES] 回调一次 [onProgress]. [add] 可以从多个线程同时调用.
     */
    @OptIn(ExperimentalAtomicApi::class)
    private class DownloadProgress(
        private val totalBytes: Long?,
        private val onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        private val downloaded = AtomicLong(0)

        fun add(bytes: Int) {
            val after = downloaded.addAndFetch(bytes.toLong())
            if (after / PROGRESS_REPORT_INTERVAL_BYTES != (after - bytes) / PROGRESS_REPORT_INTERVAL_BYTES) {
                onProgress(after, totalBytes)
            }
        }

        fun complete() {
            val bytes = downloaded.load()
            onProgress(bytes, totalBytes ?: bytes)
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

        /**
         * 直链只在短时间内有效 (artifact 约 1 分钟), 所以各段必须同时开始, 不能排队等前面的段下完.
         * 直链源站 (Azure Blob 等) 走 HTTP/1.1, 每段占一条连接; OkHttp 默认每个 host 只放行 5 个请求,
         * NSURLSession 默认每个 host 只开 4 条连接, 超出的请求会在引擎里排队.
         */
        private const val DOWNLOAD_PARTS = 4

        /**
         * 每段至少这么大, 更小的文件分段省下的时间抵不过多出的连接开销.
         */
        private const val MIN_PART_BYTES = 1024 * 1024L
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
