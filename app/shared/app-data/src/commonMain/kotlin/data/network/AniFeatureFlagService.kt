/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.RepositoryException
import me.him188.ani.app.domain.featureflag.FeatureFlagValue
import me.him188.ani.client.apis.FeatureFlagsAniApi
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.ktor.ApiInvoker
import kotlin.coroutines.CoroutineContext

/**
 * 功能开关 (`GET /v2/feature-flags`). 未登录也能调用: 服务端对登录用户按用户 id 固定分桶并应用名单, 对未登录的请求按安装 ID 分桶.
 */
open class AniFeatureFlagService(
    private val api: ApiInvoker<FeatureFlagsAniApi>,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    /**
     * 服务端为本次请求求出的全部开关的值, 按开关名. 响应里没有的开关 (服务端没有配置或已删除) 由调用方用客户端自己的默认值;
     * 客户端随机策略的开关带 [FeatureFlagValue.threshold], 由 `FeatureFlagManager` 抽签.
     */
    open suspend fun getFeatureFlags(): Map<String, FeatureFlagValue> = withContext(ioDispatcher) {
        try {
            api.invoke { getFeatureFlags().body() }.flags.mapValues { (_, value) ->
                FeatureFlagValue(enabled = value.enabled, threshold = value.threshold)
            }
        } catch (e: Exception) {
            throw RepositoryException.wrapOrThrowCancellation(e)
        }
    }
}
