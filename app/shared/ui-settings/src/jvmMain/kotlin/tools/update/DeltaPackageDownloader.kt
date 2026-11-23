/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

/**
 * 自动更新的桌面增量下载: 用 [assembler] 在本地拼出与发布包等价的安装包.
 *
 * 下载地址必须是 zip 发布包的地址, 清单地址由它派生 ([DesktopPackageManifest.manifestUrlOf]). 清单不存在、
 * 镜像不支持 Range、本地目录不符等任何一步失败, 都回退到 [fallback] 整包下载, 状态与进度也随之切到 [fallback] 的.
 *
 * 拼好或整包下载完成后, 交给 [prepare] 整理成安装时开销最小的形态 (通常是 [UpdateInstaller.prepareForInstallation]:
 * 把 zip 解压成目录), 整理期间对外仍是下载中, 整理好才算下载成功. 这样用户点「重启更新」时安装器只剩交换目录.
 * [prepare] 抛 [PackagePreparationException] 说明包本身不可用, 换源和整包下载都没用, 直接算下载失败.
 */
class DeltaPackageDownloader(
    private val assembler: DeltaPackageAssembler,
    private val fallback: FileDownloader,
    private val prepare: suspend (SystemPath) -> SystemPath = { it },
) : FileDownloader {
    constructor(
        client: ScopedHttpClient,
        installRoot: File,
        output: PackageOutput = PackageOutput.Zip,
        fallback: FileDownloader = DefaultFileDownloader(client),
        prepare: suspend (SystemPath) -> SystemPath = { it },
    ) : this(DesktopDeltaPackageAssembler(client, installRoot, output), fallback, prepare)

    override val state = MutableStateFlow<FileDownloaderState>(FileDownloaderState.Idle)

    private val _progress = MutableStateFlow(0f)
    override val progress: Flow<Float> get() = _progress

    override suspend fun download(
        alternativeUrls: List<String>,
        filenameProvider: (url: String) -> String,
        saveDir: SystemPath,
    ): SystemPath? {
        require(alternativeUrls.isNotEmpty()) { "No URLs provided." }
        state.update {
            if (it != FileDownloaderState.Idle && it !is FileDownloaderState.Completed) {
                return null
            }
            FileDownloaderState.Downloading
        }
        _progress.value = 0f

        for (url in alternativeUrls) {
            try {
                val target = assembler.assemble(
                    packageName = filenameProvider(url),
                    manifestUrl = DesktopPackageManifest.manifestUrlOf(url),
                    packageUrl = { url },
                    saveDir = saveDir,
                    onProgress = { _progress.value = it.fraction },
                )
                val prepared = prepare(target)
                _progress.value = 1f
                state.value = FileDownloaderState.Succeed(url, prepared, checked = true)
                return prepared
            } catch (e: CancellationException) {
                state.value = FileDownloaderState.Cancelled(e)
                throw e
            } catch (e: PackagePreparationException) {
                logger.warn(e) { "Package from $url is unusable, giving up" }
                state.value = FileDownloaderState.Failed(e)
                return null
            } catch (e: Throwable) {
                logger.warn(e) { "Delta download from $url failed, trying the next source" }
            }
        }

        logger.info { "Delta download failed for all sources, falling back to a full download" }
        return fallbackDownload(alternativeUrls, filenameProvider, saveDir)
    }

    private suspend fun fallbackDownload(
        alternativeUrls: List<String>,
        filenameProvider: (url: String) -> String,
        saveDir: SystemPath,
    ): SystemPath? {
        val file = try {
            coroutineScope {
                val progressMirror = launch {
                    fallback.progress.collect { _progress.value = it }
                }
                try {
                    fallback.download(alternativeUrls, filenameProvider, saveDir)
                } finally {
                    progressMirror.cancel()
                }
            }
        } catch (e: Throwable) {
            // download 抛出时 [fallback] 已经处于失败或取消状态.
            state.value = fallback.state.value
            throw e
        }
        val fallbackState = fallback.state.value
        if (file == null || fallbackState !is FileDownloaderState.Succeed) {
            state.value = fallbackState
            return null
        }
        // 整包下载完成, 整理成能直接安装的形态; 进度的最后一次更新可能还没被收集到, 整理好后补到 1.
        val prepared = try {
            prepare(file)
        } catch (e: CancellationException) {
            state.value = FileDownloaderState.Cancelled(e)
            throw e
        } catch (e: Throwable) {
            logger.warn(e) { "Failed to prepare the downloaded package $file for installation" }
            state.value = FileDownloaderState.Failed(e)
            return null
        }
        _progress.value = 1f
        state.value = FileDownloaderState.Succeed(fallbackState.url, prepared, fallbackState.checked)
        return prepared
    }

    private companion object {
        private val logger = logger<DeltaPackageDownloader>()
    }
}
