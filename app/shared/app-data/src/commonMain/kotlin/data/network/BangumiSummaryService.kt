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
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.utils.coroutines.IO_
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import kotlin.coroutines.CoroutineContext

/**
 * 直连 api.bgm.tv 补齐条目简介: Ani API 服务端部分条目 summary 为空
 * (实测 無職転生Ⅲ subject 501963, bgm.tv 有 271 字简介而 api.animeko.org 返回空串),
 * 此时用 Bangumi 原始简介整段替代 (仅替代空简介, 不做合并).
 *
 * bgm.tv v0 公开读接口无需鉴权 (仅 NSFW 条目要求 token, 拿不到时同"无简介"处理).
 * 结果按 subjectId 进程内缓存 (含"确认没有"的负缓存, 存空串); 网络错误不缓存, 下次重试.
 */
class BangumiSummaryService(
    httpClientProvider: HttpClientProvider,
    private val ioDispatcher: CoroutineContext = Dispatchers.IO_,
) {
    private val client = httpClientProvider.get()
    private val json = Json { ignoreUnknownKeys = true }

    private val cacheLock = Mutex()
    private val cache = mutableMapOf<Int, String>() // "" = 已确认 bgm.tv 也没有

    /**
     * 获取 bgm.tv 上该条目的简介 (trim 后); 确认没有时返回 null (含 NSFW 条目匿名 404).
     * 网络/解析错误抛出 (记 warn 后重抛, 不写缓存) —— 调用方自行 runCatching,
     * 切勿把错误当"确认没有"负缓存, 否则一次瞬时断网会让该条目整个会话不再重试.
     */
    suspend fun getSummary(subjectId: Int): String? = withContext(ioDispatcher) {
        cacheLock.withLock { cache[subjectId] }?.let { return@withContext it.ifEmpty { null } }

        val summary = try {
            val body = client.use { get("$BGM_API_BASE_URL/v0/subjects/$subjectId").bodyAsText() }
            json.decodeFromString(BangumiSubjectSummary.serializer(), body).summary.trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            // 4xx (条目不存在 / NSFW 条目匿名 404): 是确定性结果, 按"确认没有"负缓存
            logger.info { "bgm.tv subject $subjectId not accessible (${e.response.status}), treat as no summary" }
            ""
        } catch (e: Exception) {
            logger.warn(e) { "Failed to fetch bgm.tv summary for subject $subjectId, will retry next time" }
            throw e
        }

        logger.info { "bgm.tv summary for subject $subjectId: ${if (summary.isEmpty()) "not found" else "${summary.length} chars"}" }
        cacheLock.withLock { cache[subjectId] = summary }
        summary.ifEmpty { null }
    }

    @Serializable
    private data class BangumiSubjectSummary(
        val summary: String = "",
    )

    private companion object {
        private const val BGM_API_BASE_URL = "https://api.bgm.tv"
        private val logger = logger<BangumiSummaryService>()
    }
}
