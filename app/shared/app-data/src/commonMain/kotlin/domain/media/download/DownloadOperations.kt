/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * 在应用作用域串行执行暂停、继续与删除: 同一时刻只有一个操作在执行, 页面关闭不会中断已提交的操作.
 */
class DownloadOperations(
    private val downloadManager: MediaDownloadManager,
    private val deleteCache: DeleteCacheUseCase,
    private val executionScope: CoroutineScope,
) {
    private val serial = Mutex()

    /**
     * 目标在提交时确定: 不存在的 id 被忽略, 已排队或执行中的下载被跳过, 其余从提交起显示为忙碌, 直到各自执行完毕.
     * 取消返回的 [Deferred] 只停止等待, 操作继续执行.
     * @return 执行时抛出异常的下载及其异常, 键为 [MediaDownload.id]
     */
    fun submit(ids: Set<String>, operation: DownloadOperation): Deferred<Map<String, Throwable>> {
        val queued = ArrayDeque(ids.mapNotNull { id -> downloadManager.findDownload(id)?.takeIf { it.claim(operation) } })
        if (queued.isEmpty()) return CompletableDeferred(emptyMap())
        val result = CompletableDeferred<Map<String, Throwable>>()
        val job = executionScope.launch {
            val failures = HashMap<String, Throwable>()
            serial.withLock {
                while (true) {
                    val download = queued.removeFirstOrNull() ?: break
                    try {
                        execute(download, operation)
                    } catch (e: CancellationException) {
                        // 执行器被取消时向上传播, 单个下载内部的取消只算该项失败.
                        currentCoroutineContext().ensureActive()
                        failures[download.id] = e
                    } catch (e: Exception) {
                        logger.warn(e) { "Download operation $operation failed for ${download.id}" }
                        failures[download.id] = e
                    } finally {
                        download.release()
                    }
                }
            }
            result.complete(failures)
        }
        job.invokeOnCompletion {
            // 执行器被取消时, 尚未执行的下载也要解除忙碌; 正常完成时队列已空, 结果已给出.
            queued.forEach { it.release() }
            result.cancel()
        }
        return result
    }

    private suspend fun execute(download: MediaDownload, operation: DownloadOperation) {
        when (operation) {
            DownloadOperation.Pause -> download.pause()
            DownloadOperation.Resume -> download.resume()
            DownloadOperation.Delete -> deleteCache(download.cache)
        }
    }

    private companion object {
        private val logger = logger<DownloadOperations>()
    }
}
