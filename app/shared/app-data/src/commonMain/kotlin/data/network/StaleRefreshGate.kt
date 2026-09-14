/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 永久性持久缓存的"陈旧命中"重取闸门, 与数据类型无关 (文字/图片/任意元数据通用).
 *
 * 适用场景: 缓存按 key 永久保存 (无 TTL), 平时直接命中; 但调用方有时能从业务侧判断
 * "这次命中的内容缺了我要的东西" (如连载番缓存里没有新播集). 直接把这种情况当 miss
 * 重取会在数据源本身尚无数据时无限重复请求 —— 本闸门把重取频率压到**进程内每 key 最多一次**:
 * 本次启动试过 (无论成败) 就不再放行, 下次启动自然重试.
 *
 * 用法:
 * ```
 * private val gate = StaleRefreshGate<Int>() // 常驻单例持有 (如 Koin service)
 *
 * cached?.let {
 *     if (!gate.shouldRefresh(key) { it.lacksWhatCallerWants() }) return it
 *     // 放行: 重取; 失败/结果更差时调用方自行决定保留旧缓存
 * }
 * ```
 *
 * [shouldRefresh] 的判定函数应只依据缓存内容与调用方诉求 (纯函数), 不要在其中发请求.
 */
class StaleRefreshGate<K : Any> {
    private val lock = Mutex()
    private val attempted = mutableSetOf<K>()

    /**
     * 若 [isStale] 判定为陈旧且本进程内该 [key] 尚未放行过, 返回 true (调用方应重取);
     * 否则返回 false (直接用缓存). 返回过 true 的 key 本进程内不会再返回 true.
     */
    suspend fun shouldRefresh(key: K, isStale: () -> Boolean): Boolean {
        if (!isStale()) return false
        return lock.withLock { attempted.add(key) }
    }
}
