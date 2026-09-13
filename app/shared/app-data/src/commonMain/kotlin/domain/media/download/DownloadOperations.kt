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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import me.him188.ani.app.domain.media.cache.DeleteCacheUseCase
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn

/**
 * 提交时已不存在的下载被忽略, 不计入任何集合.
 */
data class DownloadOperationResult(
    /**
     * 执行时抛出异常的下载.
     */
    val failures: Map<String, Throwable> = emptyMap(),
    /**
     * 提交时正忙而被拒绝的下载.
     */
    val rejected: Set<String> = emptySet(),
)

/**
 * 在应用作用域执行暂停、继续与删除: 不同下载并发, 同一下载忙碌时拒绝. 页面关闭不会中断已提交的操作.
 */
class DownloadOperations(
    private val downloadManager: MediaDownloadManager,
    private val deleteCache: DeleteCacheUseCase,
    private val executionScope: CoroutineScope,
) {
    /**
     * 目标集合在提交时确定. 取消返回的 [Deferred] 只停止等待, 操作继续执行.
     */
    fun submit(ids: Set<String>, operation: DownloadOperation): Deferred<DownloadOperationResult> {
        // 直接挂在 executionScope 下, 取消汇总协程不影响它们.
        val tasks = ids.map { id -> executionScope.async { id to execute(id, operation) } }
        return executionScope.async {
            val outcomes = tasks.awaitAll()
            DownloadOperationResult(
                failures = outcomes.mapNotNull { (id, outcome) -> (outcome as? Outcome.Failed)?.let { id to it.cause } }.toMap(),
                rejected = outcomes.mapNotNullTo(hashSetOf()) { (id, outcome) -> id.takeIf { outcome == Outcome.Rejected } },
            )
        }
    }

    private suspend fun execute(id: String, operation: DownloadOperation): Outcome {
        val download = downloadManager.findDownload(id) ?: return Outcome.Done
        return try {
            when (operation) {
                DownloadOperation.Pause -> download.pause()
                DownloadOperation.Resume -> download.resume()
                DownloadOperation.Delete -> deleteCache(download.cache)
            }
            Outcome.Done
        } catch (_: DownloadBusyException) {
            Outcome.Rejected
        } catch (e: CancellationException) {
            // 执行器被取消时向上传播, 单个下载内部的取消只算该项失败.
            currentCoroutineContext().ensureActive()
            Outcome.Failed(e)
        } catch (e: Exception) {
            logger.warn(e) { "Download operation $operation failed for $id" }
            Outcome.Failed(e)
        }
    }

    private sealed interface Outcome {
        data object Done : Outcome
        data object Rejected : Outcome
        data class Failed(val cause: Throwable) : Outcome
    }

    private companion object {
        private val logger = logger<DownloadOperations>()
    }
}
