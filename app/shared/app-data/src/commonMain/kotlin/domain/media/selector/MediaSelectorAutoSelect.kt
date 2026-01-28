/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.media.selector

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.selects.onTimeout
import kotlinx.coroutines.selects.select
import me.him188.ani.app.data.models.preference.MediaSelectorSettings
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchResult
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.fetch.awaitCompletion
import me.him188.ani.app.domain.media.fetch.isFinal
import me.him188.ani.app.domain.mediasource.codec.MediaSourceTier
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.coroutines.cancellableCoroutineScope
import me.him188.ani.utils.coroutines.childScope
import me.him188.ani.utils.logging.debug
import me.him188.ani.utils.logging.logger
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 访问 [MediaSelector] 的自动选择功能
 */
inline val MediaSelector.autoSelect get() = MediaSelectorAutoSelect(this)

/**
 * [MediaSelector] 自动选择功能.
 *
 * 有关数据源选择算法, 参阅 [MediaSelector], 尤其是 "快速选择" 部分.
 */
class MediaSelectorAutoSelect(
    private val mediaSelector: MediaSelector,
) {
    /**
     * 等待所有数据源查询完成, 然后根据用户的偏好设置自动选择.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     *
     * @param waitForKind 等待此数据源类型完成后, 才执行选择. 如果为 `null`, 则等待所有数据源查询完成.
     */
    suspend fun awaitCompletedAndSelectDefault(
        mediaFetchSession: MediaFetchSession,
        waitForKind: Flow<MediaSourceKind?> = flowOf(null)
    ): Media? {
        // 等全部加载完成
        mediaFetchSession.awaitCompletion { completedConditions ->
            return@awaitCompletion waitForKind.first()?.let {
                completedConditions[it]
            } ?: completedConditions.allCompleted()
        }
        if (mediaSelector.selected.value == null) {
            val selected = mediaSelector.trySelectDefault()
            return selected
        }
        return null
    }

    /**
     * 快速选择 Web 数据源的 [Media]. 逻辑详见 [MediaSelector] 中 "快速选择" 部分的说明.
     *
     * 返回成功选择的 [Media] 对象. 当用户已经手动选择过一个别的 [Media], 或者没有可选的 [Media] 时返回 `null`.
     *
     * @param overrideUserSelection 是否覆盖用户选择.
     * 若为 `true`, 则会忽略用户目前的选择, 使用此函数的结果替换选择.
     * 若为 `false`, 如果用户已经选择了一个 media, 则此函数不会做任何事情.
     * @param blacklistMediaIds 黑名单, 这些 media 不会被选择. 如果遇到黑名单中的 media, 将会跳过.
     * @param lowTierToleranceDuration 详见 [MediaSelector] 中 "快速选择" 部分的说明.
     * @param instantSelectTierThreshold Low Tier 与 High Tier 的分界线, 小于等于此 Tier 的数据源被视作 Low Tier.
     */ // #1323
    suspend fun fastSelectWebSources(
        mediaFetchSession: MediaFetchSession,
        sourceTiers: MediaSelectorSourceTiers,
        overrideUserSelection: Boolean = false,
        blacklistMediaIds: Set<String> = emptySet(),
        lowTierToleranceDuration: Duration = 5.seconds,
        instantSelectTierThreshold: MediaSourceTier = InstantSelectTierThreshold,
    ): Media? {

        fun MediaSourceFetchResult.getTier(): MediaSourceTier = sourceTiers[this.mediaSourceId]

        return cancellableCoroutineScope {
            val backgroundTasks = childScope()

            val webSourceResults = combine(
                mediaFetchSession.mediaSourceResults
                    .filter { it.kind == MediaSourceKind.WEB }
                    .map { result ->
                        result.state.transformWhile { !it.also { emit(result) }.isFinal }
                    },
                Array<MediaSourceFetchResult>::toList,
            ).shareIn(backgroundTasks, started = SharingStarted.Eagerly, replay = 1) // 至少 replay 一个可以让 select 里读到

            // 选择合适的数据源
            val selectedMedia = select {
                webSourceResults.mapLatest { list ->
                    // 所有满足 fast select 条件的源: low tier, succeeded, 有结果
                    val candidateResults = buildList {
                        list.forEach { result ->
                            if (result.getTier() > instantSelectTierThreshold) return@forEach // high tier 不考虑
                            if (result.state.value !is MediaSourceFetchState.Succeed) return@forEach // 没完事的不考虑
                            if (result.results.first().count() <= 0) return@forEach // 没结果的不考虑
                            add(result)
                        }
                    }.also {
                        // 如果没有候选源就不往下走了
                        if (it.isEmpty()) awaitCancellation()
                    }

                    logger.debug { "fastSources: select instantly from ${candidateResults.size} candidate sources." }

                    // 如果这些满足条件的源中没有选出任何一个 media, 这里会挂起.
                    // 当下一个满足条件的源查询好后, mapLatest 会将这里的挂起取消, 重新执行.
                    // 例如: 第一个源查询好了, 但是两条结果都被排除了, 这里就会挂起.
                    // 第二个查询好了, 有满足条件的结果, 那第一次选择就被取消, 第二次就会成功.
                    // 成功后结果给 select builder, select 就会返回, 这个就是最终结果.
                    mediaSelector.selectFromMediaSources(
                        candidateResults.map { it.mediaSourceId },
                        overrideUserSelection = overrideUserSelection,
                        blacklistMediaIds = blacklistMediaIds,
                        allowNonPreferred = true, // 快速选择源是 web 源, 可以不考虑偏好. 
                    )
                }
                    .filterNotNull()
                    .produceIn(backgroundTasks)
                    .onReceive { it }

                // 等了 lowTierToleranceDuration 之后上面还没选出结果的话
                // 就从所有已经成功查询的源选一个, 具体选择逻辑在 MediaSelector 中实现.
                onTimeout(lowTierToleranceDuration) {
                    val fallback = webSourceResults.first()
                        .filter { it.state.value is MediaSourceFetchState.Succeed }
                    logger.debug { "fastSources: low tier tolerance timeout, select from ${fallback.size} succeeded sources." }
                    mediaSelector.trySelectFromMediaSources(
                        fallback.map { it.mediaSourceId },
                        overrideUserSelection = overrideUserSelection,
                        blacklistMediaIds = blacklistMediaIds,
                        allowNonPreferred = true, // 快速选择源是 web 源, 可以不考虑偏好. 
                    )
                }
            }

            logger.debug { "fastSources: selected media: $selectedMedia" }
            backgroundTasks.cancel()

            selectedMedia
        }
    }

    /**
     * 快速选择之前用户手选的源, 没选到就一直挂起
     */
    suspend fun selectPreferredWebSource(
        mediaFetchSession: MediaFetchSession,
        preferredWebMediaSourceId: String?,
    ): Media? {
        if (preferredWebMediaSourceId == null) return null

        // 等待这个源查询完成
        mediaFetchSession.mediaSourceResults
            .firstOrNull { it.mediaSourceId == preferredWebMediaSourceId && it.kind == MediaSourceKind.WEB }
            ?.awaitCompletion()

        // 尝试选择, 没有就一直挂起
        return mediaSelector.selectFromMediaSources(
            listOf(preferredWebMediaSourceId),
            overrideUserSelection = false,
            blacklistMediaIds = emptySet(),
            allowNonPreferred = true,
        )
    }

    /**
     * 自动选择第一个 [MediaSourceKind.LocalCache] [Media].
     *
     * 当成功选择了一个 [Media] 时返回它. 若已经选择了一个别的, 或没有 [MediaSourceKind.LocalCache] 类型的 [Media] 供选择, 返回 `null`.
     */
    suspend fun selectCached(
        mediaFetchSession: MediaFetchSession,
        maxAttempts: Int = Int.MAX_VALUE,
    ): Media? {
        val isSuccess = object {
            @Volatile
            var value: Media? = null

            @Volatile
            var attempted = 0
        }
        combine(
            mediaFetchSession.cumulativeResults,
        ) { _ ->
            if (mediaSelector.selected.value != null) {
                // 用户已经选择了
                isSuccess.value = null
                return@combine STOP
            }

            val selected = mediaSelector.trySelectCached()
            if (selected != null) {
                isSuccess.value = selected
                STOP
            } else {
                if (++isSuccess.attempted >= maxAttempts) {
                    // 尝试次数过多
                    STOP
                } else {
                    // 继续等待
                    !STOP
                }
            }
        }.takeWhile { it == !STOP }.collect()
        return isSuccess.value
    }

    // #355 播放时自动启用上次临时启用选择的数据源
    suspend fun autoEnableLastSelected(mediaFetchSession: MediaFetchSession) {
        val lastSelectedId = mediaSelector.mediaSourceId.finalSelected.first()
        val lastSelected = mediaFetchSession.mediaSourceResults.firstOrNull {
            it.mediaSourceId == lastSelectedId
        } ?: return
        lastSelected.enable()
    }

    companion object {
        /**
         * 如果快速选择数据源功能为启用状态 ([MediaSelectorSettings.fastSelectWebKind]),
         * 不经过任何等待, 只要该数据源查询成功并且满足字幕语言偏好就立即选择.
         *
         * @see MediaSelector
         */
        val InstantSelectTierThreshold = MediaSourceTier(0u)
    }
}

private const val STOP = true

// 日常没啥用, 只有出 bug 了才会用到
private val logger = /*SilentLogger*/logger<MediaSelectorAutoSelect>()
