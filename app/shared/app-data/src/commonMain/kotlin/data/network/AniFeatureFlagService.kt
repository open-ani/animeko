/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.client.apis.FeatureFlagsAniApi
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

/**
 * 功能开关 (`GET /v2/feature-flags/{key}`). 未登录也能调用: 服务端对登录用户按用户 id 固定分桶并应用名单, 对未登录的请求按安装 ID 分桶.
 */
open class AniFeatureFlagService(
    private val api: ApiInvoker<FeatureFlagsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 服务端为本次请求命中的取值的属性表. 没有命中任何取值 (总开关关闭, 关闭名单, 平台 / 版本不符, 没分到) 时服务端返回 `{}`;
     * 服务端没有这个开关 (404, 没有创建或已删除) 时也返回空对象, 由调用方按默认值行为.
     */
    open suspend fun getFeatureFlag(key: String): JsonObject = withContext(ioDispatcher) {
        try {
            api.invoke { getFeatureFlag(key).body() }
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                return@withContext JsonObject(emptyMap())
            }
            throw RepositoryException.wrapOrThrowCancellation(e)
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
