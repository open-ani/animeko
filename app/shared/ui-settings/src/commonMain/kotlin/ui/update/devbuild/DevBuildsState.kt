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
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import me.him188.ani.app.platform.ContextMP
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.tools.update.InstallationResult
import me.him188.ani.app.tools.update.UpdateInstaller
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.createDirectories
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.deleteRecursively
import me.him188.ani.utils.io.moveTo
import me.him188.ani.utils.io.name
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.error
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.coroutines.cancellation.CancellationException

/**
 * 开发者功能「安装指定版本」的状态.
 *
 * [refresh] 从 GitHub 拉取 main 分支最新的 commits, Build workflow 的运行结果, 以及当前平台的安装包 artifact, 合并为 [listState].
 * [lookup] 解析用户粘贴的 commit / PR / workflow 运行 / artifact 链接或安装包直链, 结果放入 [lookupState] 供用户确认后安装.
 * [install] 下载所选 commit 的 artifact, 取出安装包, 然后交给安装器; [installPackage] 直接下载安装包.
 * 桌面端安装成功会退出当前进程并由外部更新程序重启, Android 会拉起系统安装器.
 * 不支持自动安装的平台 (Linux) 下载完成后进入 [DevBuildInstallState.ReadyForManualInstall].
 *
 * @param saveDir 专用于存放本功能下载的文件的目录, 每次安装前会清空.
 * @param getToken 读取用户配置的 GitHub token, 空字符串表示未设置.
 * @param installDispatcher 调用安装器的调度器. 桌面端安装器需要在主线程调用.
 */
@Stable
class DevBuildsState(
    private val api: GitHubDevBuildApi,
    val spec: DevBuildPackageSpec,
    private val installer: UpdateInstaller,
    private val saveDir: SystemPath,
    private val getToken: suspend () -> String,
    currentVersionName: String,
    backgroundScope: CoroutineScope,
    private val installDispatcher: CoroutineDispatcher = Dispatchers.Main,
) {
    /**
     * 当前运行的版本对应的 main 分支 commit sha (短). 非 main 分支的开发版本或正式版本为 `null`.
     */
    val currentCommitShortSha: String? = parseMainBranchShortSha(currentVersionName)

    /**
     * 输入框支持的 GitHub 仓库, `owner/name`.
     */
    val repository: String get() = api.repository

    private val _listState = MutableStateFlow<DevBuildListState>(DevBuildListState.Idle)
    val listState: StateFlow<DevBuildListState> = _listState.asStateFlow()

    private val refreshTasker = MonoTasker(backgroundScope)

    /**
     * 是否正在拉取列表. 拉取期间保留上一次的 [listState].
     */
    val isRefreshing: StateFlow<Boolean> get() = refreshTasker.isRunning

    private val _lookupState = MutableStateFlow<DevBuildLookupState>(DevBuildLookupState.Idle)
    val lookupState: StateFlow<DevBuildLookupState> = _lookupState.asStateFlow()

    private val lookupTasker = MonoTasker(backgroundScope)

    private val _installState = MutableStateFlow<DevBuildInstallState>(DevBuildInstallState.Idle)
    val installState: StateFlow<DevBuildInstallState> = _installState.asStateFlow()

    private val installTasker = MonoTasker(backgroundScope)

    fun isCurrentCommit(commit: DevBuildCommit): Boolean {
        val current = currentCommitShortSha ?: return false
        return commit.sha.startsWith(current, ignoreCase = true)
    }

    fun refresh() {
        refreshTasker.launch {
            try {
                val token = getToken().trim().ifEmpty { null }
                val commits = coroutineScope {
                    val commits = async { api.listCommits(token) }
                    val runs = async { api.listWorkflowRuns(token) }
                    val artifacts = spec.artifactNames.map { name ->
                        async { api.listArtifacts(token, name) }
                    }
                    buildDevBuildCommits(commits.await(), runs.await(), artifacts.awaitAll().flatten(), spec)
                }
                _listState.value = DevBuildListState.Loaded(commits)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Failed to load dev builds" }
                _listState.value = DevBuildListState.Failed(e)
            }
        }
    }

    /**
     * 解析并查询用户输入的 [text], 见 [parseDevBuildInput]. 取消上一次未完成的查询.
     */
    fun lookup(text: String) {
        lookupTasker.launch {
            val input = parseDevBuildInput(text, repository)
            if (input == null) {
                _lookupState.value = DevBuildLookupState.Failed(DevBuildLookupFailure.Unrecognized)
                return@launch
            }
            _lookupState.value = DevBuildLookupState.Loading
            try {
                _lookupState.value = resolve(input, getToken().trim().ifEmpty { null })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error(e) { "Failed to look up dev build for input: $text" }
                _lookupState.value = DevBuildLookupState.Failed(DevBuildLookupFailure.Error(e))
            }
        }
    }

    private suspend fun resolve(input: DevBuildInput, token: String?): DevBuildLookupState = when (input) {
        is DevBuildInput.Commit -> DevBuildLookupState.Resolved(
            DevBuildLookupResult.Commit(resolveCommit(token, api.getCommit(token, input.sha))),
        )

        is DevBuildInput.PullRequest -> {
            val pr = api.getPullRequest(token, input.number)
            DevBuildLookupState.Resolved(
                DevBuildLookupResult.Commit(
                    commit = resolveCommit(token, api.getCommit(token, pr.head.sha)),
                    pullRequest = DevBuildPullRequest(
                        number = pr.number,
                        title = pr.title,
                        htmlUrl = pr.htmlUrl,
                        headRef = pr.head.ref,
                        isFromFork = pr.head.repo?.fullName?.equals(repository, ignoreCase = true) != true,
                    ),
                ),
            )
        }

        is DevBuildInput.WorkflowRun -> {
            val run = api.getWorkflowRun(token, input.runId)
            val (commit, artifacts) = coroutineScope {
                val commit = async { api.getCommit(token, run.headSha) }
                val artifacts = async { api.listRunArtifacts(token, run.id) }
                commit.await() to artifacts.await()
            }
            DevBuildLookupState.Resolved(
                DevBuildLookupResult.Commit(buildDevBuildCommits(listOf(commit), listOf(run), artifacts, spec).single()),
            )
        }

        is DevBuildInput.Artifact -> {
            val artifact = api.getArtifact(token, input.artifactId)
            if (artifact.name !in spec.candidateArtifactNames) {
                DevBuildLookupState.Failed(DevBuildLookupFailure.ArtifactNotForPlatform(artifact.name))
            } else {
                val runRef = artifact.workflowRun
                    ?: throw IllegalStateException("Artifact ${artifact.id} has no workflow run")
                val (commit, run) = coroutineScope {
                    val commit = async { api.getCommit(token, runRef.headSha) }
                    val run = async { api.getWorkflowRun(token, runRef.id) }
                    commit.await() to run.await()
                }
                DevBuildLookupState.Resolved(
                    DevBuildLookupResult.Commit(
                        buildDevBuildCommits(listOf(commit), listOf(run), listOf(artifact), spec).single(),
                    ),
                )
            }
        }

        is DevBuildInput.PackageUrl -> {
            if (input.fileName.substringAfterLast('.').equals(spec.kind.packageExtension, ignoreCase = true)) {
                DevBuildLookupState.Resolved(DevBuildLookupResult.Package(input.url, input.fileName))
            } else {
                DevBuildLookupState.Failed(DevBuildLookupFailure.UnsupportedPackage(input.fileName))
            }
        }
    }

    /**
     * 查询 [commit] 的所有 Build workflow 运行记录及其 artifacts, 合并为 [DevBuildCommit].
     * 同一个 commit 可能同时有 push 和 pull_request 事件触发的运行, artifact 在所有运行中按 [DevBuildPackageSpec.candidateArtifactNames] 择优.
     */
    private suspend fun resolveCommit(token: String?, commit: GitHubCommit): DevBuildCommit {
        val runs = api.listWorkflowRunsForCommit(token, commit.sha)
        val artifacts = coroutineScope {
            runs.map { run -> async { api.listRunArtifacts(token, run.id) } }.awaitAll().flatten()
        }
        return buildDevBuildCommits(listOf(commit), runs, artifacts, spec).single()
    }

    fun clearLookup() {
        lookupTasker.cancel()
        _lookupState.value = DevBuildLookupState.Idle
    }

    /**
     * 下载并安装 [commit] 的安装包. [commit] 没有安装包时忽略. 已有安装任务在进行时忽略.
     */
    fun install(commit: DevBuildCommit, context: ContextMP) {
        val artifact = commit.artifact ?: return
        launchInstall(DevBuildInstallTarget.of(commit), context) { target ->
            val token = getToken().trim()
            if (token.isEmpty()) throw TokenRequiredException()
            _installState.value = DevBuildInstallState.Downloading(target, 0, artifact.sizeInBytes)
            val downloadUrl = api.resolveArtifactDownloadUrl(token, artifact.archiveDownloadUrl)

            prepareSaveDir()
            val archive = saveDir.resolve("${artifact.name}-${commit.shortSha}.zip")
            api.downloadFile(downloadUrl, archive) { downloaded, total ->
                _installState.value = DevBuildInstallState.Downloading(target, downloaded, total ?: artifact.sizeInBytes)
            }

            _installState.value = DevBuildInstallState.Extracting(target)
            extractPackage(archive, saveDir.resolve(spec.packageFileName(commit.shortSha)))
        }
    }

    /**
     * 下载 [url] 处的安装包并安装. [fileName] 用作本地文件名, 必须带有当前平台的安装包扩展名. 已有安装任务在进行时忽略.
     */
    fun installPackage(url: String, fileName: String, context: ContextMP) {
        launchInstall(DevBuildInstallTarget.of(url, fileName), context) { target ->
            _installState.value = DevBuildInstallState.Downloading(target, 0, null)
            prepareSaveDir()
            val file = saveDir.resolve(fileName)
            api.downloadFile(url, file) { downloaded, total ->
                _installState.value = DevBuildInstallState.Downloading(target, downloaded, total)
            }
            if (spec.kind == DevBuildPackageKind.LINUX_APPIMAGE) {
                markExecutable(file)
            }
            file
        }
    }

    /**
     * @param preparePackage 下载并返回可交给安装器的安装包, 期间自行更新 [installState] 的进度.
     */
    private fun launchInstall(
        target: DevBuildInstallTarget,
        context: ContextMP,
        preparePackage: suspend (DevBuildInstallTarget) -> SystemPath,
    ) {
        if (installTasker.isRunning.value) return
        installTasker.launch {
            try {
                val file = preparePackage(target)
                logger.info { "Dev build package ready: $file" }

                if (spec.kind.supportsAutomaticInstall) {
                    _installState.value = DevBuildInstallState.Installing(target)
                    val result = withContext(installDispatcher) {
                        installer.install(file, packageUrls = emptyList(), context = context)
                    }
                    _installState.value = when (result) {
                        // 桌面端此时进程即将退出; Android 已拉起系统安装器
                        InstallationResult.Succeed -> DevBuildInstallState.Idle
                        is InstallationResult.Failed -> DevBuildInstallState.Failed(
                            target,
                            DevBuildInstallFailure.Installer(result),
                            file,
                        )
                    }
                } else {
                    _installState.value = DevBuildInstallState.ReadyForManualInstall(target, file)
                }
            } catch (e: CancellationException) {
                _installState.value = DevBuildInstallState.Idle
                throw e
            } catch (e: TokenRequiredException) {
                _installState.value = DevBuildInstallState.Failed(target, DevBuildInstallFailure.TokenRequired, null)
            } catch (e: DevBuildPackageNotFoundException) {
                logger.error(e) { "Dev build artifact does not contain a package" }
                _installState.value = DevBuildInstallState.Failed(target, DevBuildInstallFailure.PackageNotFound, null)
            } catch (e: Throwable) {
                logger.error(e) { "Failed to install dev build ${target.label}" }
                _installState.value = DevBuildInstallState.Failed(target, DevBuildInstallFailure.Error(e), null)
            }
        }
    }

    private suspend fun prepareSaveDir() {
        withContext(Dispatchers.IO_) {
            saveDir.deleteRecursively()
            saveDir.createDirectories()
        }
    }

    /**
     * 从 artifact zip [archive] 得到可交给安装器的安装包 [target]. 成功后 [archive] 不再存在.
     */
    private suspend fun extractPackage(archive: SystemPath, target: SystemPath): SystemPath {
        val kind = spec.kind
        if (!kind.extractsFromArchive) {
            withContext(Dispatchers.IO_) { archive.moveTo(target) }
            return target
        }
        val found = extractZipEntryByExtension(archive, kind.packageExtension, target)
        if (!found) {
            throw DevBuildPackageNotFoundException(archive.name, kind.packageExtension)
        }
        withContext(Dispatchers.IO_) { archive.delete() }
        if (kind == DevBuildPackageKind.LINUX_APPIMAGE) {
            markExecutable(target)
        }
        return target
    }

    /**
     * 取消进行中的下载或安装.
     */
    fun cancelInstall() {
        installTasker.cancel()
        _installState.update { state ->
            if (state is DevBuildInstallState.Busy) DevBuildInstallState.Idle else state
        }
    }

    /**
     * 关闭安装失败或等待手动安装的提示.
     */
    fun dismissInstallResult() {
        _installState.update { state ->
            when (state) {
                is DevBuildInstallState.Failed, is DevBuildInstallState.ReadyForManualInstall -> DevBuildInstallState.Idle
                else -> state
            }
        }
    }

    /**
     * 在文件管理器中显示已下载的安装包, 供手动安装.
     */
    suspend fun revealPackage(file: SystemPath, context: ContextMP): Boolean =
        installer.openForManualInstallation(file, context)

    /**
     * 等待进行中的刷新, 查询和安装任务结束.
     */
    @TestOnly
    suspend fun joinTasks() {
        refreshTasker.join()
        lookupTasker.join()
        installTasker.join()
    }

    private class TokenRequiredException : Exception()

    private companion object {
        val logger = logger<DevBuildsState>()
    }
}

@Stable
sealed interface DevBuildListState {
    /**
     * 尚未拉取过
     */
    @Immutable
    data object Idle : DevBuildListState

    @Immutable
    data class Loaded(val commits: List<DevBuildCommit>) : DevBuildListState

    @Immutable
    data class Failed(val throwable: Throwable) : DevBuildListState
}

@Stable
sealed interface DevBuildLookupState {
    @Immutable
    data object Idle : DevBuildLookupState

    @Immutable
    data object Loading : DevBuildLookupState

    @Immutable
    data class Resolved(val result: DevBuildLookupResult) : DevBuildLookupState

    @Immutable
    data class Failed(val failure: DevBuildLookupFailure) : DevBuildLookupState
}

/**
 * 用户输入解析并查询后得到的待安装版本.
 */
@Stable
sealed interface DevBuildLookupResult {
    /**
     * 输入对应到仓库里的一个 commit. [commit] 没有安装包时 (构建未完成或失败) 不能安装, 但仍显示其构建状态.
     *
     * @param pullRequest 输入是 PR 链接时的 PR 信息, [commit] 是该 PR 分支最新的 commit.
     */
    @Immutable
    data class Commit(
        val commit: DevBuildCommit,
        val pullRequest: DevBuildPullRequest? = null,
    ) : DevBuildLookupResult

    /**
     * 输入是安装包的直接下载地址.
     */
    @Immutable
    data class Package(val url: String, val fileName: String) : DevBuildLookupResult
}

@Stable
sealed interface DevBuildLookupFailure {
    /**
     * 输入不是支持的链接或 sha.
     */
    @Immutable
    data object Unrecognized : DevBuildLookupFailure

    /**
     * 安装包直链的扩展名不是当前平台的安装包.
     */
    @Immutable
    data class UnsupportedPackage(val fileName: String) : DevBuildLookupFailure

    /**
     * 输入的 artifact 不是当前平台的安装包.
     */
    @Immutable
    data class ArtifactNotForPlatform(val artifactName: String) : DevBuildLookupFailure

    @Immutable
    data class Error(val throwable: Throwable) : DevBuildLookupFailure
}

/**
 * 一次安装的目标, 用于在 UI 上标识进行中或失败的安装.
 *
 * @param key 唯一标识, 用于匹配列表中的条目: commit sha 或安装包地址.
 * @param label 短标识: commit 短 sha 或安装包文件名.
 * @param title commit 标题或安装包地址.
 */
@Immutable
data class DevBuildInstallTarget(
    val key: String,
    val label: String,
    val title: String,
) {
    companion object {
        fun of(commit: DevBuildCommit) = DevBuildInstallTarget(commit.sha, commit.shortSha, commit.title)
        fun of(url: String, fileName: String) = DevBuildInstallTarget(url, fileName, url)
    }
}

@Stable
sealed interface DevBuildInstallState {
    @Immutable
    data object Idle : DevBuildInstallState

    /**
     * 正在下载, 解压或安装, 可以取消.
     */
    sealed interface Busy : DevBuildInstallState {
        val target: DevBuildInstallTarget
    }

    @Immutable
    data class Downloading(
        override val target: DevBuildInstallTarget,
        val downloadedBytes: Long,
        /**
         * `null` 表示大小未知
         */
        val totalBytes: Long?,
    ) : Busy {
        /**
         * `[0, 1]`, 大小未知时为 `null`
         */
        val progress: Float?
            get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
    }

    @Immutable
    data class Extracting(override val target: DevBuildInstallTarget) : Busy

    @Immutable
    data class Installing(override val target: DevBuildInstallTarget) : Busy

    /**
     * 安装包已下载到 [file], 当前平台不支持自动安装, 等待用户手动安装.
     */
    @Immutable
    data class ReadyForManualInstall(
        val target: DevBuildInstallTarget,
        val file: SystemPath,
    ) : DevBuildInstallState

    /**
     * @param file 已准备好的安装包. 安装器安装失败时非 `null`, 供用户手动安装.
     */
    @Immutable
    data class Failed(
        val target: DevBuildInstallTarget,
        val failure: DevBuildInstallFailure,
        val file: SystemPath?,
    ) : DevBuildInstallState
}

@Stable
sealed interface DevBuildInstallFailure {
    /**
     * 未设置 GitHub token. 下载 artifact 必须登录.
     */
    @Immutable
    data object TokenRequired : DevBuildInstallFailure

    /**
     * artifact zip 里没有当前平台的安装包.
     */
    @Immutable
    data object PackageNotFound : DevBuildInstallFailure

    @Immutable
    data class Installer(val result: InstallationResult.Failed) : DevBuildInstallFailure

    @Immutable
    data class Error(val throwable: Throwable) : DevBuildInstallFailure
}

class DevBuildPackageNotFoundException(
    archiveName: String,
    extension: String,
) : Exception("No .$extension file found in artifact archive $archiveName")
