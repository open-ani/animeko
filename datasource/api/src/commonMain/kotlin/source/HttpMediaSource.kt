/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.datasources.api.source

import io.ktor.utils.io.core.Closeable
import me.him188.ani.utils.logging.thisLogger

fun Closeable.asAutoCloseable() = AutoCloseable { close() }

/**
 * 支持执行 HTTP 请求的 [MediaSource]. 封装一些便捷的操作
 */
abstract class HttpMediaSource : MediaSource {
    private val closeables = mutableListOf<AutoCloseable>()

    /**
     * `public` because used by both [useHttpClient] and inheritors from other modules.
     */
    val logger = thisLogger()

    fun addCloseable(closeable: AutoCloseable) {
        closeables.add(closeable)
    }

    override fun close() {
        super.close()
        this.closeables.forEach { it.close() }
    }

    companion object
}

