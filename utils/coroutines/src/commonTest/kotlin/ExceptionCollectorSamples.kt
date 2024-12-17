/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("RemoveRedundantBackticks")

package me.him188.ani.utils.coroutines

import me.him188.ani.test.Sample

@Sample
fun `ExceptionCollector_collectOps`() {
    fun method1(): String = error("1")
    fun method2(): String = error("2")

    // 假设我们有两种方法获取同一个资源 (例如请求代理域名和源站). 如果一个方法失败了, 我们可以尝试另一个方法.
    val result: String = withExceptionCollector {
        try {
            return@withExceptionCollector method1()
        } catch (e: Exception) {
            collect(e) // 方法 1 失败了, 收集异常后继续执行方法 2
        }

        // 继续尝试方法 2
        try {
            return@withExceptionCollector method2()
        } catch (e: Exception) {
            collect(e)
        }

        // 可以添加更多方法

        throwLast() // 抛出最后一个异常, 携带所有中间异常信息
    }
}
