/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/**
 * 用户在「安装指定版本」输入框中粘贴的内容解析后的结果.
 */
sealed interface DevBuildInput {
    /**
     * commit 链接或 sha (完整或缩写, 至少 7 位).
     */
    data class Commit(val sha: String) : DevBuildInput

    /**
     * PR 链接或 `#123`.
     */
    data class PullRequest(val number: Int) : DevBuildInput

    /**
     * Build workflow 某次运行的链接.
     */
    data class WorkflowRun(val runId: Long) : DevBuildInput

    /**
     * GitHub Actions artifact 的链接, 网页链接或 API 的下载地址均可.
     */
    data class Artifact(val artifactId: Long) : DevBuildInput

    /**
     * 安装包的直接下载地址, 例如 Release 附件. [fileName] 是地址路径的最后一段, 已解码.
     */
    data class PackageUrl(val url: String, val fileName: String) : DevBuildInput
}

/**
 * 解析 [text]. 识别以下形式, 无法识别时返回 `null`:
 *
 * - commit sha: 7 到 40 位十六进制
 * - PR 编号: `123` 或 `#123`
 * - `https://github.com/{repository}/commit/{sha}`
 * - `https://github.com/{repository}/pull/{number}[/...]`
 * - `https://github.com/{repository}/pull/{number}/commits/{sha}`
 * - `https://github.com/{repository}/actions/runs/{runId}[/...]`
 * - `https://github.com/{repository}/actions/runs/{runId}/artifacts/{artifactId}`
 * - `https://api.github.com/repos/{repository}/commits/{sha}`, `.../pulls/{number}`, `.../actions/runs/{runId}`,
 *   `.../actions/artifacts/{artifactId}[/zip]`
 * - 其他 http(s) 地址 (包括 GitHub Release 附件), 且路径最后一段带扩展名: 视为安装包直链
 *
 * 指向其他仓库的 commit / PR / artifact 链接视为无法识别.
 */
fun parseDevBuildInput(text: String, repository: String): DevBuildInput? {
    val input = text.trim()
    if (input.isEmpty()) return null
    if (SHA_REGEX.matches(input)) return DevBuildInput.Commit(input.lowercase())
    PR_NUMBER_REGEX.matchEntire(input)?.let { return DevBuildInput.PullRequest(it.groupValues[1].toInt()) }

    val url = runCatching { Url(input) }.getOrNull() ?: return null
    if (url.protocol != URLProtocol.HTTP && url.protocol != URLProtocol.HTTPS) return null
    val segments = url.segments.filter { it.isNotEmpty() }
    return when (url.host.lowercase()) {
        // Release 附件也在 github.com 下 (`/releases/download/...`), 不是已知页面时按直链处理
        "github.com", "www.github.com" -> parseGitHubPath(segments, repository) ?: parsePackageUrl(input, segments)
        "api.github.com" -> parseGitHubApiPath(segments, repository)
        else -> parsePackageUrl(input, segments)
    }
}

private fun parseGitHubPath(segments: List<String>, repository: String): DevBuildInput? {
    if (segments.size < 4 || !isRepository(segments[0], segments[1], repository)) return null
    val rest = segments.drop(2)
    return when (rest[0]) {
        "commit" -> rest.getOrNull(1)?.takeIf { SHA_REGEX.matches(it) }?.let { DevBuildInput.Commit(it.lowercase()) }
        "pull" -> {
            val number = rest.getOrNull(1)?.toIntOrNull() ?: return null
            val sha = rest.getOrNull(3)?.takeIf { rest.getOrNull(2) == "commits" && SHA_REGEX.matches(it) }
            if (sha != null) DevBuildInput.Commit(sha.lowercase()) else DevBuildInput.PullRequest(number)
        }

        "actions" -> {
            if (rest.getOrNull(1) != "runs") return null
            val runId = rest.getOrNull(2)?.toLongOrNull() ?: return null
            val artifactId = rest.getOrNull(4)?.takeIf { rest.getOrNull(3) == "artifacts" }?.toLongOrNull()
            if (artifactId != null) DevBuildInput.Artifact(artifactId) else DevBuildInput.WorkflowRun(runId)
        }

        else -> null
    }
}

private fun parseGitHubApiPath(segments: List<String>, repository: String): DevBuildInput? {
    if (segments.size < 5 || segments[0] != "repos" || !isRepository(segments[1], segments[2], repository)) return null
    val rest = segments.drop(3)
    return when (rest[0]) {
        "commits" -> rest.getOrNull(1)?.takeIf { SHA_REGEX.matches(it) }?.let { DevBuildInput.Commit(it.lowercase()) }
        "pulls" -> rest.getOrNull(1)?.toIntOrNull()?.let { DevBuildInput.PullRequest(it) }
        "actions" -> when (rest.getOrNull(1)) {
            "runs" -> rest.getOrNull(2)?.toLongOrNull()?.let { DevBuildInput.WorkflowRun(it) }
            "artifacts" -> rest.getOrNull(2)?.toLongOrNull()?.let { DevBuildInput.Artifact(it) }
            else -> null
        }

        else -> null
    }
}

private fun parsePackageUrl(url: String, segments: List<String>): DevBuildInput? {
    val fileName = segments.lastOrNull() ?: return null
    if (fileName.substringAfterLast('.', "").isEmpty()) return null
    return DevBuildInput.PackageUrl(url, fileName)
}

private fun isRepository(owner: String, name: String, repository: String): Boolean =
    "$owner/$name".equals(repository, ignoreCase = true)

private val SHA_REGEX = Regex("""[0-9a-fA-F]{7,40}""")
private val PR_NUMBER_REGEX = Regex("""#?(\d{1,6})""")
