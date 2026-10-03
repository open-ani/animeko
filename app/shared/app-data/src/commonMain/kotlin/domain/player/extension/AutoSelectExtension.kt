/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.data.models.episode.EpisodeInfo
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.awaitCompletedResults
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorAutoSelectUseCase
import me.him188.ani.app.domain.media.selector.ReplayBrowseMemoryUseCase
import me.him188.ani.app.domain.media.selector.UnsafeOriginalMediaAccess
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.logging.logger
import me.him188.ani.utils.logging.warn
import org.koin.core.Koin
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

/**
 * 自动选择数据源. 切集时按「本地缓存 → 浏览记忆回放 → 自动选择」的顺序在同一协程内串行决定,
 * 回放未命中才启动自动选择, 因此两者不会互相覆盖.
 *
 * 本任务挂在 session 的 sessionScope 下, 未捕获的异常会包成 ExtensionException 并终止任务, 之后本集及每次 bundle 重建都失去自动选择;
 * 所以缓存检查与回放都自行捕获非取消异常, 失败只是退回自动选择.
 *
 * @see MediaSelector
 * @see ReplayBrowseMemoryUseCase
 */
class AutoSelectExtension(
    private val context: PlayerExtensionContext,
    koin: Koin
) : PlayerExtension("AutoSelect") {
    private val mediaSelectorAutoSelectUseCase: MediaSelectorAutoSelectUseCase by koin.inject()
    private val replayBrowseMemory: ReplayBrowseMemoryUseCase by koin.inject()

    override fun onStart(
        episodeSession: EpisodeSession,
        backgroundTaskScope: ExtensionBackgroundTaskScope
    ) {
        backgroundTaskScope.launch("AutoSelect") {
            // 任务挂在本 session 的 sessionScope 下, switchEpisode 先取消旧 scope 再换 session, 任务内不可能观察到别的 session;
            // 直接用 onStart 的 episodeSession, 不经 context.sessionFlow.
            episodeSession.fetchSelectFlow.collectLatest { fetchSelect ->
                if (fetchSelect == null) return@collectLatest
                // fetchSelect 由 infoBundleFlow 派生, 拿到非空 bundle 时 replayCache 已有对应的 infoBundle.
                // infoBundleFlow 首值 null, 网络错误后也会回到 null; 用 replayCache 而不是 filterNotNull().first(), 否则会连自动选择一起挂住.
                val episodeInfo = episodeSession.infoBundleFlow.replayCache.lastOrNull()?.episodeInfo
                val replayed = episodeInfo != null
                        && !hasLocalCacheForEpisode(fetchSelect.mediaFetchSession, fetchSelect.mediaSelector)
                        && replayBrowseMemoryOrFalse(episodeInfo, fetchSelect.mediaSelector)
                if (!replayed) {
                    mediaSelectorAutoSelectUseCase(fetchSelect.mediaFetchSession, fetchSelect.mediaSelector)
                }
            }
        }
    }

    private suspend fun replayBrowseMemoryOrFalse(episodeInfo: EpisodeInfo, mediaSelector: MediaSelector): Boolean {
        return try {
            replayBrowseMemory(context.subjectId, episodeInfo, mediaSelector).isHit
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Replaying browse memory failed, falling back to auto select" }
            false
        }
    }

    /**
     * 本地缓存的优先级高于浏览记忆 (`MediaAutoSelector.decide` 第一步就是 selectCache, 回放不能反转它).
     * 本地源是毫秒级完成的: 等它们完成, 再等 [MediaSelector.filteredCandidates] 反映它们的结果
     * (候选列表经共享流异步传播, 刚完成时可能仍是本地源完成前的快照), 然后看其中有没有本集的缓存候选.
     * 超时 ([CACHE_CHECK_TIMEOUT]) 或异常 (非 Cancellation) → false (照常回放).
     */
    @OptIn(UnsafeOriginalMediaAccess::class)
    private suspend fun hasLocalCacheForEpisode(session: MediaFetchSession, mediaSelector: MediaSelector): Boolean {
        val cacheResults = session.mediaSourceResults.filter { it.kind == MediaSourceKind.LocalCache }
        if (cacheResults.isEmpty()) return false
        return try {
            withTimeoutOrNull(CACHE_CHECK_TIMEOUT) {
                val cachedMediaIds = cacheResults.flatMapTo(HashSet()) { result ->
                    result.awaitCompletedResults().map { it.mediaId }
                }
                if (cachedMediaIds.isEmpty()) return@withTimeoutOrNull false
                mediaSelector.filteredCandidates
                    .first { candidates -> candidates.any { it.original.mediaId in cachedMediaIds } }
                    .any { it is MaybeExcludedMedia.Included && it.result.kind == MediaSourceKind.LocalCache }
            } == true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "Checking local cache failed, replaying browse memory as usual" }
            false
        }
    }

    companion object : EpisodePlayerExtensionFactory<AutoSelectExtension> {
        private val CACHE_CHECK_TIMEOUT = 2.seconds
        private val logger = logger<AutoSelectExtension>()

        override fun create(context: PlayerExtensionContext, koin: Koin): AutoSelectExtension {
            return AutoSelectExtension(context, koin)
        }
    }
}
