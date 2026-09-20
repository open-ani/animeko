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
 * 开发者功能「安装 main 分支的指定 commit」的状态.
 *
 * [refresh] 从 GitHub 拉取 main 分支最新的 commits, Build workflow 的运行结果, 以及当前平台的安装包 artifact, 合并为 [listState].
 * [install] 下载所选 commit 的 artifact, 取出安装包, 然后交给安装器; 桌面端安装成功会退出当前进程并由外部更新程序重启,
 * Android 会拉起系统安装器. 不支持自动安装的平台 (Linux) 下载完成后进入 [DevBuildInstallState.ReadyForManualInstall].
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

    private val _listState = MutableStateFlow<DevBuildListState>(DevBuildListState.Idle)
    val listState: StateFlow<DevBuildListState> = _listState.asStateFlow()

    private val refreshTasker = MonoTasker(backgroundScope)

    /**
     * 是否正在拉取列表. 拉取期间保留上一次的 [listState].
     */
    val isRefreshing: StateFlow<Boolean> get() = refreshTasker.isRunning

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
     * 下载并安装 [commit] 的安装包. [commit] 没有安装包时忽略. 已有安装任务在进行时忽略.
     */
    fun install(commit: DevBuildCommit, context: ContextMP) {
        val artifact = commit.artifact ?: return
        if (installTasker.isRunning.value) return
        installTasker.launch {
            try {
                val token = getToken().trim()
                if (token.isEmpty()) {
                    _installState.value = DevBuildInstallState.Failed(commit, DevBuildInstallFailure.TokenRequired, null)
                    return@launch
                }
                _installState.value = DevBuildInstallState.Downloading(commit, 0, artifact.sizeInBytes)
                val downloadUrl = api.resolveArtifactDownloadUrl(token, artifact.archiveDownloadUrl)

                withContext(Dispatchers.IO_) {
                    saveDir.deleteRecursively()
                    saveDir.createDirectories()
                }
                val archive = saveDir.resolve("${artifact.name}-${commit.shortSha}.zip")
                api.downloadFile(downloadUrl, archive) { downloaded, total ->
                    _installState.value = DevBuildInstallState.Downloading(commit, downloaded, total ?: artifact.sizeInBytes)
                }

                _installState.value = DevBuildInstallState.Extracting(commit)
                val file = preparePackage(archive, commit)
                logger.info { "Dev build package ready: $file" }

                if (spec.kind.supportsAutomaticInstall) {
                    _installState.value = DevBuildInstallState.Installing(commit)
                    val result = withContext(installDispatcher) {
                        installer.install(file, packageUrls = emptyList(), context = context)
                    }
                    _installState.value = when (result) {
                        // 桌面端此时进程即将退出; Android 已拉起系统安装器
                        InstallationResult.Succeed -> DevBuildInstallState.Idle
                        is InstallationResult.Failed -> DevBuildInstallState.Failed(
                            commit,
                            DevBuildInstallFailure.Installer(result),
                            file,
                        )
                    }
                } else {
                    _installState.value = DevBuildInstallState.ReadyForManualInstall(commit, file)
                }
            } catch (e: CancellationException) {
                _installState.value = DevBuildInstallState.Idle
                throw e
            } catch (e: DevBuildPackageNotFoundException) {
                logger.error(e) { "Dev build artifact does not contain a package" }
                _installState.value = DevBuildInstallState.Failed(commit, DevBuildInstallFailure.PackageNotFound, null)
            } catch (e: Throwable) {
                logger.error(e) { "Failed to install dev build ${commit.shortSha}" }
                _installState.value = DevBuildInstallState.Failed(commit, DevBuildInstallFailure.Error(e), null)
            }
        }
    }

    /**
     * 从 artifact zip [archive] 得到可交给安装器的安装包. 成功后 [archive] 不再存在.
     */
    private suspend fun preparePackage(archive: SystemPath, commit: DevBuildCommit): SystemPath {
        val target = saveDir.resolve(spec.packageFileName(commit.shortSha))
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
     * 等待进行中的刷新和安装任务结束.
     */
    @TestOnly
    suspend fun joinTasks() {
        refreshTasker.join()
        installTasker.join()
    }

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
sealed interface DevBuildInstallState {
    @Immutable
    data object Idle : DevBuildInstallState

    /**
     * 正在下载, 解压或安装, 可以取消.
     */
    sealed interface Busy : DevBuildInstallState {
        val commit: DevBuildCommit
    }

    @Immutable
    data class Downloading(
        override val commit: DevBuildCommit,
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
    data class Extracting(override val commit: DevBuildCommit) : Busy

    @Immutable
    data class Installing(override val commit: DevBuildCommit) : Busy

    /**
     * 安装包已下载到 [file], 当前平台不支持自动安装, 等待用户手动安装.
     */
    @Immutable
    data class ReadyForManualInstall(
        val commit: DevBuildCommit,
        val file: SystemPath,
    ) : DevBuildInstallState

    /**
     * @param file 已准备好的安装包. 安装器安装失败时非 `null`, 供用户手动安装.
     */
    @Immutable
    data class Failed(
        val commit: DevBuildCommit,
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
