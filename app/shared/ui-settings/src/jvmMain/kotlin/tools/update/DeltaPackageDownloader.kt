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
 * [assembler] 决定拼出来的是 zip 还是解压好的目录; 回退的整包下载总是 zip, 所以安装器两种都要能装.
 */
class DeltaPackageDownloader(
    private val assembler: DeltaPackageAssembler,
    private val fallback: FileDownloader,
) : FileDownloader {
    constructor(
        client: ScopedHttpClient,
        installRoot: File,
        output: PackageOutput = PackageOutput.Zip,
        fallback: FileDownloader = DefaultFileDownloader(client),
    ) : this(DesktopDeltaPackageAssembler(client, installRoot, output), fallback)

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
                _progress.value = 1f
                state.value = FileDownloaderState.Succeed(url, target, checked = true)
                return target
            } catch (e: CancellationException) {
                state.value = FileDownloaderState.Cancelled(e)
                throw e
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
    ): SystemPath? = coroutineScope {
        val progressMirror = launch {
            fallback.progress.collect { _progress.value = it }
        }
        try {
            fallback.download(alternativeUrls, filenameProvider, saveDir)
        } finally {
            progressMirror.cancel()
            // download 返回或抛出时 [fallback] 已经处于最终状态 (成功、失败或取消); 进度的最后一次更新可能还没被收集到.
            state.value = fallback.state.value
            if (state.value is FileDownloaderState.Succeed) _progress.value = 1f
        }
    }

    private companion object {
        private val logger = logger<DeltaPackageDownloader>()
    }
}
