/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.update.devbuild

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

/**
 * 仓库里的一个 commit, 及其 Build workflow 的运行结果和当前平台的安装包.
 */
@Immutable
data class DevBuildCommit(
    val sha: String,
    /**
     * commit message 的第一行
     */
    val title: String,
    val author: String,
    val committedAt: Instant?,
    val htmlUrl: String,
    /**
     * 对应的 Build workflow 运行记录. `null` 表示没有找到 (例如 commit 只改了文档, 不触发构建, 或记录太旧).
     */
    val build: DevBuildRun?,
    /**
     * 当前平台可用的安装包. `null` 表示该 commit 没有上传当前平台的安装包, 或已过期.
     */
    val artifact: DevBuildArtifact?,
) {
    val shortSha: String get() = sha.take(SHORT_SHA_LENGTH)

    companion object {
        /**
         * 与 CI 的 `updateDevVersionNameFromGit` 任务写入版本号的 sha 长度一致.
         */
        const val SHORT_SHA_LENGTH = 8
    }
}

@Immutable
data class DevBuildRun(
    val id: Long,
    val status: DevBuildStatus,
    val htmlUrl: String,
)

enum class DevBuildStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILURE,
    CANCELLED,
    OTHER,
    ;

    companion object {
        fun fromWorkflowRun(status: String?, conclusion: String?): DevBuildStatus = when (status) {
            "queued", "waiting", "pending", "requested" -> QUEUED
            "in_progress" -> IN_PROGRESS
            "completed" -> when (conclusion) {
                "success" -> SUCCESS
                "failure", "timed_out", "startup_failure", "action_required" -> FAILURE
                "cancelled", "skipped" -> CANCELLED
                else -> OTHER
            }

            else -> OTHER
        }
    }
}

@Immutable
data class DevBuildArtifact(
    val id: Long,
    val name: String,
    val sizeInBytes: Long,
    val archiveDownloadUrl: String,
)

/**
 * 用户输入 PR 链接时, 解析出的 PR 信息. 安装的是 PR 分支最新 commit ([DevBuildCommit]) 的构建.
 *
 * @param isFromFork PR 分支来自 fork 仓库. 这种 PR 只有 pull_request 事件触发的构建, Android 没有 release 包.
 */
@Immutable
data class DevBuildPullRequest(
    val number: Int,
    val title: String,
    val htmlUrl: String,
    val headRef: String,
    val isFromFork: Boolean,
)

/**
 * 将 GitHub 返回的 commits, workflow runs 和 artifacts 按 commit sha 合并.
 *
 * 同一个 commit 有多条运行记录时取最新的 (id 最大); artifact 按 [DevBuildPackageSpec.candidateArtifactNames] 的顺序优先,
 * 同名取最新且未过期的.
 * 结果保持 [commits] 的顺序.
 */
fun buildDevBuildCommits(
    commits: List<GitHubCommit>,
    runs: List<GitHubWorkflowRun>,
    artifacts: List<GitHubArtifact>,
    spec: DevBuildPackageSpec,
): List<DevBuildCommit> {
    val runBySha = runs.groupBy { it.headSha }.mapValues { (_, list) -> list.maxBy { it.id } }
    val artifactsBySha = artifacts
        .filter { !it.expired && it.workflowRun != null && it.name in spec.candidateArtifactNames }
        .groupBy { it.workflowRun!!.headSha }

    return commits.map { commit ->
        val run = runBySha[commit.sha]
        val artifact = artifactsBySha[commit.sha]
            ?.sortedWith(
                compareBy<GitHubArtifact> { spec.candidateArtifactNames.indexOf(it.name) }.thenByDescending { it.id },
            )
            ?.firstOrNull()
        DevBuildCommit(
            sha = commit.sha,
            title = commit.commit.message.lineSequence().firstOrNull()?.trim().orEmpty(),
            author = commit.author?.login?.takeIf { it.isNotBlank() }
                ?: commit.commit.author?.name.orEmpty(),
            committedAt = (commit.commit.committer?.date ?: commit.commit.author?.date)
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { Instant.parse(it) }.getOrNull() },
            htmlUrl = commit.htmlUrl,
            build = run?.let {
                DevBuildRun(
                    id = it.id,
                    status = DevBuildStatus.fromWorkflowRun(it.status, it.conclusion),
                    htmlUrl = it.htmlUrl,
                )
            },
            artifact = artifact?.let {
                DevBuildArtifact(
                    id = it.id,
                    name = it.name,
                    sizeInBytes = it.sizeInBytes,
                    archiveDownloadUrl = it.archiveDownloadUrl,
                )
            },
        )
    }
}

/**
 * 从开发版本号中提取 commit sha. CI 的 `updateDevVersionNameFromGit` 任务把 main 分支的版本号写成 `4.12.0-main-28ec14ac`.
 * 非 main 分支的开发版本或正式版本返回 `null`.
 */
fun parseMainBranchShortSha(versionName: String, branch: String = GitHubDevBuildApi.DEFAULT_BRANCH): String? =
    Regex("""-${Regex.escape(branch)}-([0-9a-fA-F]{7,40})$""").find(versionName)?.groupValues?.get(1)?.lowercase()
