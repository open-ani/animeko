/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.ktor

import io.ktor.client.HttpClient
import kotlin.contracts.contract

abstract class WrapperHttpClient {
    /**
     * 使用一个满足需求的 [HttpClient] 实例.
     *
     * 在调用此方法时, client 一定会有效. 但是在调用结束后, client 可能会被销毁, 因此不要将对 client 的引用带出此方法.
     */
    @OptIn(UnsafeWrapperHttpClientApi::class)
    inline fun <R> use(
        action: HttpClient.() -> R,
    ): R {
        contract {
            callsInPlace(action, kotlin.contracts.InvocationKind.EXACTLY_ONCE)
        }
        val client = borrow()
        try {
            return action(client.client)
        } finally {
            returnClient(client)
        }
    }

    /**
     * 借用一个 [HttpClient] 实例. 返回一个 [Ticket], 可从中获取 [HttpClient] 实例. 在归还时需要调用 [returnClient] 并传入相同的 ticket.
     */
    @UnsafeWrapperHttpClientApi
    abstract fun borrow(): Ticket

    /**
     * 永久持有一个 [HttpClient] 实例 (只要不调用 [returnClient]), 此实例永远不会被销毁.
     *
     * 效果跟 [borrow] 一样, 但是为了突出此实例永久持有的特性.
     *
     * **如何选择 [borrow] 还是 [borrowForever]?**
     * - 如果你未来会归还此实例, 请使用 [borrow].
     * - 如果你确保不会归还此实例, 请使用 [borrowForever].
     */
    @UnsafeWrapperHttpClientApi
    fun borrowForever(): Ticket = borrow()

    /**
     * 归还一个 [HttpClient] 实例. 确保传入的 ticket 是由 [borrow] 返回的.
     */
    @UnsafeWrapperHttpClientApi
    abstract fun returnClient(ticket: Ticket)

    /**
     * 由 [WrapperHttpClient] 实现类实现的接口, 它会包含借用的 [HttpClient] 实例以及在借用时刻的一些信息, 用于处理归还.
     * 不要自行实现此接口 (包括通过 `by` 关键字委托).
     */
    @SubclassOptInRequired(UnsafeWrapperHttpClientApi::class)
    @UnsafeWrapperHttpClientApi
    interface Ticket {
        val client: HttpClient
    }
}

/**
 * 用于标记 [WrapperHttpClient] 的 API 是不安全的, 使用不当可能导致内存泄漏.
 */
@RequiresOptIn(
    message = "This operates on unsafe reference counter. Incorrect usage may cause memory leak.",
    level = RequiresOptIn.Level.ERROR,
)
annotation class UnsafeWrapperHttpClientApi

/**
 * 将 [HttpClient] 封装为 [WrapperHttpClient].
 *
 * 为了性能考虑, 借用总是返回相同的 [this] 的 ticket, 归还时不做任何操作吗, 也就是说归还多次是被允许的 (与 [WrapperHttpClient] 的其他实现可能不同).
 */
fun HttpClient.asWrapperHttpClient(): WrapperHttpClient = object : WrapperHttpClient() {
    @UnsafeWrapperHttpClientApi
    private val ticket = object : Ticket {
        override val client = this@asWrapperHttpClient
    }

    @UnsafeWrapperHttpClientApi
    override fun borrow(): Ticket {
        return ticket
    }

    @UnsafeWrapperHttpClientApi
    override fun returnClient(ticket: Ticket) {
    }
}

