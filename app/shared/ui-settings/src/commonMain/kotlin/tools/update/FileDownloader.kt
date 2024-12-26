/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.tools.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.get
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.readAvailable
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.datasources.api.topic.FileSize.Companion.bytes
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.withExceptionCollector
import me.him188.ani.utils.io.DEFAULT_BUFFER_SIZE
import me.him188.ani.utils.io.DigestAlgorithm
import me.him188.ani.utils.io.SystemPath
import me.him188.ani.utils.io.absolutePath
import me.him188.ani.utils.io.bufferedSink
import me.him188.ani.utils.io.bufferedSource
import me.him188.ani.utils.io.delete
import me.him188.ani.utils.io.exists
import me.him188.ani.utils.io.length
import me.him188.ani.utils.io.readAndDigest
import me.him188.ani.utils.io.resolve
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import kotlin.time.Duration.Companion.seconds

/**
 * 文件下载器.
 *
 * - 从多个 URL 中顺序尝试下载文件
 * - 为成功下载的文件做校验 (使用 URL 的同级 .sha1)
 * - 提供下载进度 [progress] 与下载状态 [state]
 */
interface FileDownloader {
    /**
     * Range: `[0, 1]`.
     */
    val progress: Flow<Float>
    val state: StateFlow<FileDownloaderState>

    /**
     * 开始在后台从 [alternativeUrls]（顺序）下载文件到 [saveDir] 并验证:
     * - 如果目标文件已存在且校验通过，则跳过下载
     * - 如果校验失败，删除重新下载
     *
     * @return `true` if this call starts or skips a download successfully,
     *         `false` if there is already a running download.
     */
    suspend fun download(
        alternativeUrls: List<String>,
        filenameProvider: (url: String) -> String,
        saveDir: SystemPath,
    ): Boolean
}

sealed class FileDownloaderState {
    /**
     * [FileDownloader.download] 尚未被调用.
     */
    data object Idle : FileDownloaderState()

    /**
     * 正在尝试下载某一个 URL.
     */
    data object Downloading : FileDownloaderState()

    sealed class Completed : FileDownloaderState()

    /**
     * 下载成功并通过校验.
     *
     * @param url 下载的源地址
     * @param file 保存的文件
     */
    data class Succeed(
        val url: String,
        val file: SystemPath,
        val checked: Boolean,
    ) : Completed()

    /**
     * 所有地址均下载失败, 或其他异常.
     */
    data class Failed(val throwable: Throwable) : Completed()
}

class DefaultFileDownloader(
    private val client: HttpClient,
) : FileDownloader {
    private companion object {
        private val logger = logger<DefaultFileDownloader>()
    }

    override val state = MutableStateFlow<FileDownloaderState>(FileDownloaderState.Idle)

    private val _progress = MutableStateFlow(0f)
    override val progress: Flow<Float> get() = _progress

    @OptIn(ExperimentalStdlibApi::class)
    override suspend fun download(
        alternativeUrls: List<String>,
        filenameProvider: (url: String) -> String,
        saveDir: SystemPath,
    ): Boolean {
        // Ensure there's at least one URL
        require(alternativeUrls.isNotEmpty()) { "No URLs provided." }

        // Transition to "Downloading" only if not already in a valid state
        state.update {
            if (it != FileDownloaderState.Idle && it !is FileDownloaderState.Completed) {
                return false
            }
            FileDownloaderState.Downloading
        }

        _progress.value = 0f
        withExceptionCollector {
            for (url in alternativeUrls) {
                try {
                    val filename = filenameProvider(url)
                    val targetFile = saveDir.resolve(filename)

                    // 1. 获取远程校验和
                    val remoteChecksum = fetchRemoteChecksum(client, url)

                    if (remoteChecksum == null) {
                        // 2. 没有校验和 => 下载
                        logger.info { "No remote SHA-1 found for: $url" }
                    } else {
                        // 2. 如果本地已存在，检查校验和（SHA-1）
                        if (targetFile.exists()) {
                            logger.info { "File $filename already exists, size=${targetFile.length().bytes}, verifying SHA-1..." }
                            val localChecksum = computeLocalChecksum(targetFile, DigestAlgorithm.SHA1)
                            if (localChecksum == remoteChecksum) {
                                // 已存在且校验通过 => 跳过下载
                                logger.info { "File $filename already exists and SHA-1 matches. Skipping download." }
                                state.value = FileDownloaderState.Succeed(url, targetFile, checked = true)
                                return true
                            } else {
                                // 校验失败 => 删除旧文件
                                logger.info { "File $filename exists but SHA-1 mismatch. Deleting old file..." }
                                withContext(Dispatchers.IO_) {
                                    targetFile.delete()
                                }
                            }
                        }
                    }

                    // 3. 下载文件
                    tryDownload(client, url, targetFile)

                    // 4. 再次校验
                    if (remoteChecksum != null) {
                        val localChecksum = computeLocalChecksum(targetFile, DigestAlgorithm.SHA1)
                        if (localChecksum != remoteChecksum) {
                            logger.info { "File $filename SHA-1 mismatch after download. Deleting file..." }
                            withContext(Dispatchers.IO_) {
                                targetFile.delete()
                            }
                            state.value = FileDownloaderState.Failed(
                                IllegalStateException("Downloaded file $filename SHA-1 mismatch after download."),
                            )
                            return false
                        }
                    }

                    // 下载完成且校验成功
                    state.value = FileDownloaderState.Succeed(url, targetFile, checked = remoteChecksum != null)
                    return true

                } catch (e: CancellationException) {
                    // Propagate cancellation
                    throw e
                } catch (e: Throwable) {
                    // Collect, mark as failed, and try next URL
                    collect(e)
                    state.value = FileDownloaderState.Failed(getLast()!!)
                }
            }
            // If we exhausted all URLs, throw the last error we collected
            throwLast()
        }
        // Unreachable in normal flow
        return false
    }

    /**
     * 获取远程 SHA-1 校验和: 对应的 URL 为 [url].sha1
     */
    private suspend fun fetchRemoteChecksum(client: HttpClient, url: String): String? {
        return try {
            // The server should serve the checksum as plain text
            client.get("$url.sha1").body()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            if (e.response.status == io.ktor.http.HttpStatusCode.NotFound) {
                logger.info { "No remote SHA-1 found for: $url" }
                null
            } else {
                throw e
            }
        }
    }

    /**
     * 下载单个文件并更新进度 [_progress]. 如果下载失败, 抛出异常.
     */
    private suspend fun tryDownload(client: HttpClient, url: String, file: SystemPath) {
        cancellableCoroutineScope {
            logger.info { "Attempting download: $url" }
            try {
                client.prepareRequest(url).execute { resp ->
                    val length = resp.contentLength()
                    logger.info { "Downloading $url to ${file.absolutePath}, length=${(length ?: 0).bytes}" }

                    val downloaded = object {
                        val value = atomic(0L)
                    }

                    val input = resp.bodyAsChannel()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                    if (length != null) {
                        launch {
                            while (isActive) {
                                delay(1.seconds)
                                _progress.value = downloaded.value.value.toFloat() / length
                            }
                        }
                    }

                    file.bufferedSink().use { output ->
                        while (!input.isClosedForRead) {
                            val read = input.readAvailable(buffer)
                            if (read == -1) {
                                break
                            }
                            downloaded.value.addAndGet(read.toLong())
                            withContext(Dispatchers.IO_) {
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                    _progress.value = 1f

                    logger.info { "Successfully downloaded: $url" }
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.info(e) { "Failed to download $url" }
                throw e
            } finally {
                // Cancel any extra coroutines in the same scope
                cancelScope()
            }
        }
    }

    /**
     * 计算 [file] 的 [DigestAlgorithm.SHA1] 校验和并返回 Hex 字符串.
     */
    private fun computeLocalChecksum(file: SystemPath, algorithm: DigestAlgorithm): String {
        return file.bufferedSource().use {
            it.readAndDigest(algorithm).toHexString()
        }
    }
}
