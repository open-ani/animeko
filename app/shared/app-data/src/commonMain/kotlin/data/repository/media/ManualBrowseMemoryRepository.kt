/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.data.repository.media

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.him188.ani.datasources.api.EpisodeSort
import me.him188.ani.datasources.api.source.BrowseChannel
import me.him188.ani.datasources.api.source.BrowseSubject

/**
 * 一个条目的手动查找记忆. 由「播放并记住」写入, 切集时由 [me.him188.ani.app.domain.media.selector.ReplayBrowseMemoryUseCase] 读取与更新.
 *
 * @property mediaSourceId 数据源 id (与偏好、记忆 Web 源使用的 id 相同; 不是 instanceId).
 * @property subject 浏览结果里的条目, `url` 跨浏览稳定.
 * @property channelIndex 线路在 `browseSubject` 返回列表中的下标; 回放时优先按下标且要求 [channelName] 一致.
 * @property channelName 线路标识 [BrowseChannel.name], 站点无线路概念时为 null; 下标失效时按它兜底.
 * @property episodeIndex 上次选中剧集在线路剧集列表中的位置 k.
 * @property episodeSort 上次选中剧集由数据源解析出的集号; `EpisodeSort.Unknown` 视为解析不出, 存为 null.
 * @property playedAsSort 上次把该项当作条目的哪一集播放 (写入时的当前集 `EpisodeInfo.sort`).
 *   按位置回放只在目标集恰好是它的下一集时才成立, 否则任意集打开、跳集、倒退都会把第 k+1 项当成目标集播放.
 */
@Serializable
data class ManualBrowseMemory(
    val mediaSourceId: String,
    val subject: BrowseSubject,
    val channelIndex: Int,
    val channelName: String?,
    val episodeIndex: Int,
    val episodeSort: EpisodeSort? = null,
    val playedAsSort: EpisodeSort,
)

@Serializable
data class ManualBrowseMemories(
    val bySubjectId: Map<Int, ManualBrowseMemory> = emptyMap(),
) {
    companion object {
        val Empty = ManualBrowseMemories()
    }
}

/**
 * 按 subjectId 隔离的手动查找记忆. 写入直接挂起完成, 不经 `MediaSelectorEvents.onChangePreference` 的 debounce 管线.
 */
interface ManualBrowseMemoryRepository {
    fun flow(subjectId: Int): Flow<ManualBrowseMemory?>
    suspend fun get(subjectId: Int): ManualBrowseMemory?

    /**
     * 整条覆盖该条目的记忆.
     */
    suspend fun set(subjectId: Int, memory: ManualBrowseMemory)

    /**
     * 当前记忆仍等于 [expected] 时才覆盖为 [memory]; 返回是否写入.
     * 回放读到记忆后要经过一次条目页请求才写回, 期间用户可能已「播放并记住」新记忆, 无条件写回会把它覆盖成旧值的变体.
     */
    suspend fun setIf(subjectId: Int, expected: ManualBrowseMemory, memory: ManualBrowseMemory): Boolean

    suspend fun remove(subjectId: Int)

    /**
     * 当前记忆仍等于 [expected] 时才删除; 返回是否删除. 与 [setIf] 同理, 回放判定失效的是本次读到的那条记忆, 不是用户刚写入的新记忆.
     */
    suspend fun removeIf(subjectId: Int, expected: ManualBrowseMemory): Boolean
}

/**
 * 构造参数全显式, 不用 `KoinPlatform.getKoin()` 默认参数.
 */
class ManualBrowseMemoryRepositoryImpl(
    private val store: DataStore<ManualBrowseMemories>,
) : ManualBrowseMemoryRepository {
    override fun flow(subjectId: Int): Flow<ManualBrowseMemory?> =
        store.data.map { it.bySubjectId[subjectId] }.distinctUntilChanged()

    override suspend fun get(subjectId: Int): ManualBrowseMemory? =
        store.data.first().bySubjectId[subjectId]

    override suspend fun set(subjectId: Int, memory: ManualBrowseMemory) {
        store.updateData { it.copy(bySubjectId = it.bySubjectId + (subjectId to memory)) }
    }

    override suspend fun setIf(subjectId: Int, expected: ManualBrowseMemory, memory: ManualBrowseMemory): Boolean {
        var applied = false
        store.updateData { memories ->
            applied = memories.bySubjectId[subjectId] == expected
            if (applied) memories.copy(bySubjectId = memories.bySubjectId + (subjectId to memory)) else memories
        }
        return applied
    }

    override suspend fun remove(subjectId: Int) {
        store.updateData { it.copy(bySubjectId = it.bySubjectId - subjectId) }
    }

    override suspend fun removeIf(subjectId: Int, expected: ManualBrowseMemory): Boolean {
        var applied = false
        store.updateData { memories ->
            applied = memories.bySubjectId[subjectId] == expected
            if (applied) memories.copy(bySubjectId = memories.bySubjectId - subjectId) else memories
        }
        return applied
    }
}
