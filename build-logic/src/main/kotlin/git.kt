/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.kotlin.dsl.of
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import java.io.Serializable
import javax.inject.Inject

/**
 * 构建时嵌入到 `AniBuildConfig` 的 git 信息.
 * 字段为空字符串表示无法获取, 比如从源码包构建没有 `.git`, 或者机器上没有 `git`.
 */
data class GitInfo(
    /** 分支名, 如 `main`. detached HEAD (CI 的 tag / PR 构建) 时回退到 GitHub Actions 的环境变量. */
    val branch: String,
    /** HEAD 的完整 commit sha (40 位). */
    val commitSha: String,
    /** HEAD commit 的提交时间, ISO-8601, 如 `2026-09-20T12:00:00+08:00`. */
    val commitTime: String,
) : Serializable {
    companion object {
        val UNKNOWN = GitInfo(branch = "", commitSha = "", commitTime = "")
    }
}

/**
 * 用 ValueSource 而不是配置期直接 exec: 这样配置缓存只把结果登记为输入,
 * 每次构建开始时重新求值并与缓存比较, commit 变了才让缓存失效.
 *
 * 嵌入的值变了只会重编 app-platform 本身: 生成的是 `override val`, 不进 ABI, 下游模块编译 0 个文件.
 */
abstract class GitInfoValueSource : ValueSource<GitInfo, GitInfoValueSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        val rootDir: DirectoryProperty
    }

    @get:Inject
    abstract val execOperations: ExecOperations

    override fun obtain(): GitInfo {
        val commitSha = git("rev-parse", "HEAD") ?: return GitInfo.UNKNOWN
        val branch = git("rev-parse", "--abbrev-ref", "HEAD")
            ?.takeIf { it != "HEAD" } // detached HEAD
            ?: System.getenv("GITHUB_HEAD_REF")?.takeIf { it.isNotBlank() } // PR 构建: 源分支
            ?: System.getenv("GITHUB_REF_NAME")?.takeIf { it.isNotBlank() } // push / tag 构建
            ?: ""
        val commitTime = git("show", "-s", "--format=%cI", "HEAD") ?: ""
        // 故意不嵌入工作区是否 dirty: 那会在 commit 后第一次改文件时再触发一次 app-platform 重编.
        return GitInfo(
            branch = branch,
            commitSha = commitSha,
            commitTime = commitTime,
        )
    }

    /** 运行 git 命令并返回去掉首尾空白的 stdout. git 不存在或退出码非 0 时返回 `null`. */
    private fun git(vararg args: String): String? {
        val stdout = ByteArrayOutputStream()
        val result = runCatching {
            execOperations.exec {
                workingDir = parameters.rootDir.get().asFile
                commandLine("git", *args)
                standardOutput = stdout
                errorOutput = ByteArrayOutputStream()
                isIgnoreExitValue = true
            }
        }.getOrNull() ?: return null
        if (result.exitValue != 0) return null
        return stdout.toString(Charsets.UTF_8).trim()
    }
}

/** 当前 checkout 的 git 信息, 每次构建只求值一次. */
val Project.gitInfo: Provider<GitInfo>
    get() = providers.of(GitInfoValueSource::class) {
        parameters.rootDir.set(layout.settingsDirectory)
    }
