/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.coroutines

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching], 但协程取消 ([CancellationException]) 原样抛出, 不包进 [Result].
 *
 * 在协程里直接用 [runCatching] 会把取消也当作失败吞掉, 调用方随后在一个已被取消的协程里继续跑下去.
 * [block] 会挂起或运行在协程里时都应改用本函数. 其余异常 (包括 [Error]) 与 [runCatching] 一样包成 [Result.failure].
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}
