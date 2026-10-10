/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.player.extension

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.domain.episode.EpisodeSession
import me.him188.ani.app.domain.episode.MediaFetchSelectBundle
import me.him188.ani.app.domain.media.fetch.MediaFetchSession
import me.him188.ani.app.domain.media.fetch.MediaSourceFetchState
import me.him188.ani.app.domain.media.selector.MatchMetadata.SubjectMatchKind
import me.him188.ani.app.domain.media.selector.MaybeExcludedMedia
import me.him188.ani.app.domain.media.selector.MediaAutoSelector
import me.him188.ani.app.domain.media.selector.MediaSelector
import me.him188.ani.app.domain.media.selector.MediaSelectorSourceTiers
import me.him188.ani.app.domain.media.selector.SelectEvent
import me.him188.ani.app.domain.media.selector.SelectOrigin
import me.him188.ani.app.domain.mediasource.GetMediaSelectorSourceTiersUseCase
import me.him188.ani.datasources.api.Media
import me.him188.ani.datasources.api.isLocalCache
import me.him188.ani.datasources.api.source.MediaSourceKind
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.MediaFetchResult
import me.him188.ani.utils.analytics.IAnalytics
import org.koin.core.Koin
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * 记录本集查询和选择资源的结果 ([MediaFetchResult]), 用来区分没能起播是因为没有资源, 数据源全部失败, 还是用户没等到选择就离开了.
 *
 * 首次选中资源 (自动或手动) 时记录; 到离开本集 (切集或关闭播放页) 时仍未选中, 则在离开时记录. 每个 [EpisodeSession] 只记录一次.
 *
 * - 时长从本集拿到查询会话 ([MediaFetchSelectBundle]) 起算. 同一条目的各集共用查询会话, 切集后通常立即就有结果.
 * - 资源数是 [MediaSelector.filteredCandidates] 中未被排除, 即本集可用的资源数.
 * - 数据源计数不含本地缓存源和被禁用的源. 失败包括需要验证码和被限流; 被中途取消的源算作未完成.
 * - Web 源计数是有可用资源的 Web 源数, 其中有精确匹配资源的源数, 和有资源能在自动选择第一阶段立即被选的源数
 *   (精确匹配并且有效阶级不超过 [MediaAutoSelector.Web.DefaultInstantTier]).
 *   都按选中 (或离开) 那一刻的候选列表计算, 之后才返回资源的源不计入.
 * - 选中的阶级是资源的有效阶级, 与自动选择使用的一致. 本地缓存不记录阶级.
 */
class MediaFetchAnalyticsExtension(
    private val context: PlayerExtensionContext,
    private val getSourceTiers: GetMediaSelectorSourceTiersUseCase,
    private val analytics: IAnalytics = Analytics,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : PlayerExtension("MediaFetchAnalytics") {
    private val lock = Mutex()

    /**
     * 当前剧集的统计. 离开本集时取出并置为 `null`; 已在选中时记录过的不再记录.
     */
    private val currentStats = MutableStateFlow<MediaFetchStats?>(null)

    /**
     * 最新的数据源阶级. 在后台收集, 离开播放页时不用再读取.
     */
    private val sourceTiers = MutableStateFlow<MediaSelectorSourceTiers?>(null)

    override fun onStart(episodeSession: EpisodeSession, backgroundTaskScope: ExtensionBackgroundTaskScope) {
        val stats = MediaFetchStats(episodeSession.episodeId)
        currentStats.value = stats

        backgroundTaskScope.launch("MediaFetchAnalyticsSourceTiers") {
            getSourceTiers().collect { sourceTiers.value = it }
        }
        backgroundTaskScope.launch("MediaFetchAnalytics") {
            episodeSession.fetchSelectFlow.filterNotNull()
                .mapLatest { fetchSelect -> observeUntilSelected(fetchSelect, stats) }
                .first()
            record(stats)
        }
    }

    /**
     * 观察 [fetchSelect] 的候选资源, 直到选中一个资源.
     */
    private suspend fun observeUntilSelected(fetchSelect: MediaFetchSelectBundle, stats: MediaFetchStats) {
        lock.withLock { stats.onFetchSelect(fetchSelect.mediaFetchSession, timeSource) }
        val selector = fetchSelect.mediaSelector
        coroutineScope {
            val candidates = launch {
                selector.filteredCandidates.collect { list ->
                    lock.withLock { stats.onAvailableMedia(list.filterIsInstance<MaybeExcludedMedia.Included>()) }
                }
            }
            // 选择事件没有回放. 自动选择要等查询结果, 在它之前就会订阅上; 没收到事件时不记录选择来源.
            val selectEvents = launch {
                selector.events.onSelect.collect { event -> lock.withLock { stats.onSelectEvent(event) } }
            }
            val selected = selector.selected.filterNotNull().first()
            lock.withLock { stats.onSelect(selected) }
            withTimeoutOrNull(CANDIDATES_CATCH_UP_TIMEOUT) {
                // 事件在更新 selected 之后才发出
                stats.selectOrigin.first { it != null }
                // 候选列表经共享流异步传播, 资源一到就被自动选中时列表可能还没更新. 等它跟上, 以免资源数和首个资源耗时缺失.
                // 手动查找等现场创建的资源不在候选列表中, 等到超时后照常记录.
                stats.availableMedia.first { list -> list.any { it.result.mediaId == selected.mediaId } }
            }
            candidates.cancel()
            selectEvents.cancel()
        }
    }

    override suspend fun onBeforeSwitchEpisode(newEpisodeId: Int) {
        currentStats.getAndUpdate { null }?.let { record(it) }
    }

    override suspend fun onClose() {
        currentStats.getAndUpdate { null }?.let { record(it) }
    }

    private suspend fun record(stats: MediaFetchStats) {
        val properties = lock.withLock {
            if (stats.recorded) return
            stats.recorded = true

            val selected = stats.selected
            val availableMedia = stats.availableMedia.value
            val mediaCount = availableMedia.size
            val tiers = sourceTiers.value
            val sourceStates = stats.fetchSession?.mediaSourceResults
                ?.filter { it.kind != MediaSourceKind.LocalCache }
                ?.map { it.state.value }
                ?.filter { it !is MediaSourceFetchState.Disabled }
            val succeeded = sourceStates?.count { it is MediaSourceFetchState.Succeed } ?: 0
            val failed = sourceStates?.count { it.isFailed() } ?: 0
            val outcome = when {
                selected != null -> OUTCOME_SELECTED
                // 还没拿到查询会话 (剧集信息未加载完), 或仍有源在查询, 或已有资源但用户没有选择
                sourceStates == null || mediaCount > 0 || succeeded + failed < sourceStates.size ->
                    OUTCOME_LEFT_BEFORE_SELECT

                sourceStates.isNotEmpty() && failed == sourceStates.size -> OUTCOME_ALL_SOURCES_FAILED
                else -> OUTCOME_NO_MEDIA
            }

            buildMap<String, Any?> {
                put("subject_id", context.subjectId.toLong())
                put("episode_id", stats.episodeId.toLong())
                put("outcome", outcome)
                if (sourceStates != null) {
                    put("sources_total", sourceStates.size.toLong())
                    put("sources_succeeded", succeeded.toLong())
                    put("sources_failed", failed.toLong())
                    put("media_count", mediaCount.toLong())
                    val web = availableMedia.filter { it.result.kind == MediaSourceKind.WEB }
                    val exact = web.filter { it.metadata.subjectMatchKind == SubjectMatchKind.EXACT }
                    put("web_sources_with_media", web.countSources())
                    put("web_sources_exact", exact.countSources())
                    if (tiers != null) {
                        put(
                            "web_sources_instant",
                            exact.filter { tiers.effectiveTierOf(it.result) <= MediaAutoSelector.Web.DefaultInstantTier }
                                .countSources(),
                        )
                    }
                }
                put("first_media_ms", stats.firstMedia?.inWholeMilliseconds)
                put("select_ms", stats.selectTime?.inWholeMilliseconds)
                put("selected_by", stats.selectOrigin.value?.toAnalyticsValue())
                put("selected_source_id", selected?.mediaSourceId?.take(100))
                if (selected != null && !selected.isLocalCache()) {
                    put("selected_tier", tiers?.effectiveTierOf(selected)?.value?.toLong())
                }
                put(
                    "selected_match",
                    availableMedia.firstOrNull { it.result.mediaId == selected?.mediaId }
                        ?.metadata?.subjectMatchKind?.toAnalyticsValue(),
                )
                put("is_cached", selected?.isLocalCache()?.toAnalyticsFlag())
            }
        }
        analytics.recordEvent(MediaFetchResult, properties)
    }

    /**
     * 查询以失败告终. 被中途取消 ([MediaSourceFetchState.Abandoned]) 的源不算: 关闭播放页时进行中的查询会被取消.
     */
    private fun MediaSourceFetchState.isFailed(): Boolean = this is MediaSourceFetchState.Failed
            || this is MediaSourceFetchState.CaptchaRequired
            || this is MediaSourceFetchState.RateLimited

    private fun List<MaybeExcludedMedia.Included>.countSources(): Long =
        mapTo(HashSet()) { it.result.mediaSourceId }.size.toLong()

    private fun MediaSelectorSourceTiers.effectiveTierOf(media: Media) = get(media.mediaSourceId, media.properties.alliance)

    private fun SelectOrigin.toAnalyticsValue(): String = when (this) {
        SelectOrigin.MANUAL -> "manual"
        SelectOrigin.AUTOMATIC -> "auto"
        SelectOrigin.BROWSE_MEMORY -> "browse_memory"
    }

    private fun SubjectMatchKind.toAnalyticsValue(): String = when (this) {
        SubjectMatchKind.EXACT -> "exact"
        SubjectMatchKind.FUZZY -> "fuzzy"
    }

    companion object : EpisodePlayerExtensionFactory<MediaFetchAnalyticsExtension> {
        private val CANDIDATES_CATCH_UP_TIMEOUT = 2.seconds

        private const val OUTCOME_SELECTED = "selected"
        private const val OUTCOME_NO_MEDIA = "no_media"
        private const val OUTCOME_ALL_SOURCES_FAILED = "all_sources_failed"
        private const val OUTCOME_LEFT_BEFORE_SELECT = "left_before_select"

        override fun create(context: PlayerExtensionContext, koin: Koin): MediaFetchAnalyticsExtension {
            return MediaFetchAnalyticsExtension(context, koin.get())
        }
    }
}

/**
 * 一个 [EpisodeSession] 内的查询与选择统计. 时间都是相对首次拿到查询会话的时长.
 */
private class MediaFetchStats(
    val episodeId: Int,
) {
    private var fetchStart: TimeMark? = null

    /**
     * 最近一次拿到的查询会话. 查询条件变化时会换成新的会话.
     */
    var fetchSession: MediaFetchSession? = null
        private set

    /**
     * 本集可用的资源, 即候选列表中未被排除的资源.
     */
    val availableMedia = MutableStateFlow<List<MaybeExcludedMedia.Included>>(emptyList())
    var firstMedia: Duration? = null
        private set
    var selected: Media? = null
        private set
    var selectTime: Duration? = null
        private set

    /**
     * [selected] 的选择来源, 收到对应的选择事件后才有值.
     */
    val selectOrigin = MutableStateFlow<SelectOrigin?>(null)

    /**
     * 在 [selected] 更新之前收到的选择事件.
     */
    private val earlySelectEvents = mutableListOf<SelectEvent>()
    var recorded = false

    fun onFetchSelect(session: MediaFetchSession, timeSource: TimeSource) {
        if (fetchStart == null) fetchStart = timeSource.markNow()
        fetchSession = session
    }

    fun onAvailableMedia(media: List<MaybeExcludedMedia.Included>) {
        availableMedia.value = media
        if (media.isNotEmpty() && firstMedia == null) {
            firstMedia = fetchStart?.elapsedNow()
        }
    }

    fun onSelect(media: Media) {
        if (selected != null) return
        selected = media
        selectTime = fetchStart?.elapsedNow()
        earlySelectEvents.forEach { onSelectEvent(it) }
        earlySelectEvents.clear()
    }

    fun onSelectEvent(event: SelectEvent) {
        val selected = selected
        when {
            selected == null -> earlySelectEvents += event
            selectOrigin.value == null && event.media?.mediaId == selected.mediaId -> selectOrigin.value = event.origin
        }
    }
}
