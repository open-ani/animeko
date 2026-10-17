/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.download

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.media.download.DownloadOperation
import me.him188.ani.app.domain.media.download.DownloadOperations

/**
 * 页面提交批量操作并累计失败数, 供失败提示使用. 操作在应用作用域执行, [scope] 只用于等待结果: 页面关闭后不再统计, 操作继续完成.
 */
class DownloadOperationRunner(
    private val operations: DownloadOperations,
    private val scope: CoroutineScope,
) {
    private val failures = MutableStateFlow(0)

    /**
     * 累计的失败数, [dismissFailures] 后归零.
     */
    val failedCount: StateFlow<Int> = failures.asStateFlow()

    fun run(ids: Set<String>, operation: DownloadOperation) {
        if (ids.isEmpty()) return
        val pending = operations.submit(ids, operation)
        scope.launch {
            val count = pending.await().size
            if (count > 0) failures.update { it + count }
        }
    }

    fun dismissFailures() {
        failures.value = 0
    }
}
