/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.tv.ui.exploration

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.paging.compose.LazyPagingItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import me.him188.ani.app.data.models.recommend.RecommendedItemInfo
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.FollowedSubjectInfo
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.tv.ui.foundation.focus.TvFocusKey
import me.him188.ani.tv.ui.foundation.focus.TvFocusScope
import me.him188.ani.tv.ui.foundation.focus.tvFocusAnchor
import me.him188.ani.tv.ui.foundation.widgets.TvHeroDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvLandscapeCard
import me.him188.ani.tv.ui.foundation.widgets.TvLandscapeCardDefaults
import me.him188.ani.tv.ui.foundation.widgets.TvPageDefaults
import me.him188.ani.tv.ui.foundation.widgets.tvHeroContentColor

/*
 * 探索页 Prime 式行列表 (atv-architecture.md §7.1 v5): hero 常驻, 下方为纵向行列表,
 * 每行是横向锚定卡片行 —— 焦点卡始终在行首, 焦点行始终贴在 hero 下方 (焦点项恒在左上角).
 *
 * 锚定用 Compose 的 BringIntoViewSpec 实现: 焦点落到卡片 → 焦点系统发 bringIntoView →
 * 行 (横向) 把卡对齐行首, 列 (纵向) 把行头对齐顶部. 纯焦点事件驱动, 无轮询/延时 (§14.4-8).
 */

/**
 * 锚定式 BringIntoViewSpec: 目标总是滚到容器前缘 (再留 [leadingReservePx] 给行头),
 * 而不是默认的"只要露出来就不动".
 */
@OptIn(ExperimentalFoundationApi::class)
internal class TvAnchoredBringIntoViewSpec(private val leadingReservePx: Float = 0f) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - leadingReservePx
}

/** 行内卡片锚点: (行 key, 卡索引). 行间导航/左右移动都以此为送焦目标. */
internal data class TvExplorationCardKey(val rowKey: String, val index: Int) : TvFocusKey

/** 探索页一行的数据规格. [count] 随 Paging 加载增长. */
internal sealed class TvExplorationRow(val key: String, val title: String) {
    abstract val count: Int

    class ContinueWatching(val items: LazyPagingItems<FollowedSubjectInfo>) :
        TvExplorationRow("followed", "继续观看") {
        override val count: Int get() = items.itemCount
    }

    /** 为你推荐按 [TvExplorationDefaults.RecommendationRowSize] 分段成多行 (Prime 式多行浏览). */
    class Recommendations(val items: LazyPagingItems<RecommendedItemInfo>, val chunk: Int) :
        TvExplorationRow("rec-$chunk", if (chunk == 0) "为你推荐" else "为你推荐 · ${chunk + 1}") {
        val start: Int = chunk * TvExplorationDefaults.RecommendationRowSize
        override val count: Int
            get() = (items.itemCount - start).coerceIn(0, TvExplorationDefaults.RecommendationRowSize)
    }
}

/**
 * 条目媒体缓存 (页内单例): 详情 (评分/连载/简介) + TMDB 横版 backdrop + 简介兜底.
 * hero 与卡片共用: 卡片先拉的 backdrop, 聚焦时 hero 直接复用.
 */
@Stable
internal class TvSubjectMediaState(
    private val collectionRepo: SubjectCollectionRepository,
    private val tmdb: TmdbImageService,
    private val bangumiSummaryService: BangumiSummaryService,
    private val scope: CoroutineScope,
) {
    val infoCache = mutableStateMapOf<Int, SubjectCollectionInfo>()

    /** value null = TMDB 已确认无图 (卡片退化海报裁切, hero 退化海报). */
    val backdropCache = mutableStateMapOf<Int, String?>()
    val summaryFallbackCache = mutableStateMapOf<Int, String>()

    private val backdropInFlight = mutableSetOf<Int>()
    private val backdropGate = Semaphore(TvExplorationDefaults.BackdropConcurrency)

    /** 调用方已持有详情 (继续观看行自带 SubjectCollectionInfo) 时直接入缓存, 省一次拉取. */
    fun putInfo(info: SubjectCollectionInfo) {
        val id = info.subjectInfo.subjectId
        if (id !in infoCache) infoCache[id] = info
    }

    /**
     * hero 用: 加载 [target] 的详情/backdrop/简介兜底. 详情未缓存时防抖 300ms
     * (焦点快速划过时不发请求); 由 collectLatest 驱动, 换条目自动取消在途.
     */
    suspend fun loadHero(target: TvHeroSubject) {
        var info = infoCache[target.subjectId]
        if (info == null) {
            delay(TvHeroDefaults.MediaDebounceMillis)
            info = fetchInfo(target.subjectId) ?: return
        }
        if (target.subjectId !in backdropCache) fetchBackdrop(target.subjectId, info)
        if (info.subjectInfo.summary.isBlank() && target.subjectId !in summaryFallbackCache) {
            runCatching { bangumiSummaryService.getSummary(target.subjectId) }
                .onSuccess { summaryFallbackCache[target.subjectId] = it.orEmpty() }
        }
    }

    /** 卡片用: 后台确保 [subjectId] 的 backdrop 已请求 (有限并发; 已有结果/在途则跳过). */
    fun requestBackdrop(subjectId: Int) {
        if (subjectId in backdropCache || !backdropInFlight.add(subjectId)) return
        scope.launch {
            try {
                backdropGate.withPermit {
                    val info = infoCache[subjectId] ?: fetchInfo(subjectId)
                    if (info == null) {
                        backdropCache[subjectId] = null // 详情拿不到: 本页不再重试
                        return@withPermit
                    }
                    fetchBackdrop(subjectId, info)
                }
            } finally {
                backdropInFlight.remove(subjectId)
            }
        }
    }

    private suspend fun fetchInfo(subjectId: Int): SubjectCollectionInfo? = try {
        collectionRepo.subjectCollectionFlow(subjectId).first().also { infoCache[subjectId] = it }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    private suspend fun fetchBackdrop(subjectId: Int, info: SubjectCollectionInfo) {
        // 失败也写 null: 本页不再重试 (TmdbImageService 自身持久负缓存; 网络错误它下次进页会重试)
        backdropCache[subjectId] = runCatching {
            tmdb.getBackdropUrl(
                subjectId,
                info.subjectInfo.name,
                activeAsOfDate = info.episodes.newestAiredDateStringOrNull(),
            )
        }.getOrNull()
    }
}

/** TMDB w1280 backdrop 降到卡片档 (w780). */
internal fun tmdbBackdropCardUrl(url: String): String = url.replace("/t/p/w1280/", "/t/p/w780/")

/**
 * 探索页一行: 行头 + 横向锚定卡片行. 焦点接线在此 (行内左右 / 行间上下均显式送焦,
 * 因为锚定后前一张卡/上一行都已滚出组合, 空间搜索找不到):
 * - ← 前一张: 先滚行让其重新组合, 再送焦 (请求悬挂到锚点附着); 首卡按左不消费 (交给侧栏)
 * - → 后一张: 直接送焦 (后一张在视口内已组合), BringIntoView 把它对齐行首
 * - ↑/↓: [onNavigateVertical] (行 0 按上回 hero)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TvExplorationRowItem(
    row: TvExplorationRow,
    media: TvSubjectMediaState,
    focus: TvFocusScope,
    rowStates: SnapshotStateMap<String, LazyListState>,
    focusedIndexByRow: SnapshotStateMap<String, Int>,
    onCardFocused: (TvHeroSubject) -> Unit,
    onClickSubject: (TvHeroSubject) -> Unit,
    onNavigateVertical: (delta: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 行的横向位置随 route 返回/列表回收保留 (rememberSaveable): 跨 route 焦点恢复要求目标卡仍在组合中
    val rowState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    SideEffect { if (rowStates[row.key] !== rowState) rowStates[row.key] = rowState }
    val scope = rememberCoroutineScope()
    val startAligned = remember { TvAnchoredBringIntoViewSpec() }

    fun moveTo(target: Int, scrollFirst: Boolean) {
        focusedIndexByRow[row.key] = target
        if (scrollFirst) scope.launch { rowState.animateScrollToItem(target) }
        focus.request(TvExplorationCardKey(row.key, target))
    }

    Column(modifier.fillMaxWidth()) {
        Box(
            Modifier.height(TvExplorationDefaults.RowHeaderHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                row.title,
                style = MaterialTheme.typography.titleMedium,
                color = tvHeroContentColor(),
            )
        }
        CompositionLocalProvider(LocalBringIntoViewSpec provides startAligned) {
            LazyRow(
                state = rowState,
                modifier = Modifier
                    .fillMaxWidth()
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val current = focusedIndexByRow[row.key] ?: rowState.firstVisibleItemIndex
                        when (event.key) {
                            Key.DirectionUp -> {
                                onNavigateVertical(-1)
                                true
                            }

                            Key.DirectionDown -> {
                                onNavigateVertical(1)
                                true
                            }

                            Key.DirectionLeft -> if (current > 0) {
                                moveTo(current - 1, scrollFirst = true)
                                true
                            } else {
                                false
                            }

                            Key.DirectionRight -> {
                                if (current + 1 < row.count) moveTo(current + 1, scrollFirst = false)
                                true
                            }

                            else -> false
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(TvLandscapeCardDefaults.Spacing),
                contentPadding = PaddingValues(end = TvPageDefaults.EndPadding),
            ) {
                when (row) {
                    is TvExplorationRow.ContinueWatching -> items(
                        row.count,
                        key = { row.items.peek(it)?.subjectInfo?.subjectId ?: it },
                    ) { index ->
                        val info = row.items[index] ?: return@items
                        SideEffect { media.putInfo(info.subjectCollectionInfo) }
                        val subject = TvHeroSubject(
                            info.subjectInfo.subjectId,
                            info.subjectInfo.displayName,
                            info.subjectInfo.imageLarge,
                        )
                        TvExplorationCard(
                            subject = subject,
                            memoryId = "followed-${subject.subjectId}",
                            row = row,
                            index = index,
                            media = media,
                            focus = focus,
                            focusedIndexByRow = focusedIndexByRow,
                            onCardFocused = onCardFocused,
                            onClickSubject = onClickSubject,
                        )
                    }

                    is TvExplorationRow.Recommendations -> items(
                        row.count,
                        key = { (row.items.peek(row.start + it) as? RecommendedSubjectInfo)?.bangumiId ?: (row.start + it) },
                    ) { index ->
                        val rec = row.items[row.start + index] as? RecommendedSubjectInfo ?: return@items
                        val subject = TvHeroSubject(rec.bangumiId, rec.nameCn, rec.imageLarge)
                        TvExplorationCard(
                            subject = subject,
                            memoryId = "rec-${subject.subjectId}",
                            row = row,
                            index = index,
                            media = media,
                            focus = focus,
                            focusedIndexByRow = focusedIndexByRow,
                            onCardFocused = onCardFocused,
                            onClickSubject = onClickSubject,
                        )
                    }
                }
            }
        }
    }
}

/** 行内一张卡: 横图取 TMDB backdrop (卡片档), 缺图退化海报裁切; 聚焦上报行内索引 + hero 目标. */
@Composable
private fun TvExplorationCard(
    subject: TvHeroSubject,
    memoryId: String,
    row: TvExplorationRow,
    index: Int,
    media: TvSubjectMediaState,
    focus: TvFocusScope,
    focusedIndexByRow: SnapshotStateMap<String, Int>,
    onCardFocused: (TvHeroSubject) -> Unit,
    onClickSubject: (TvHeroSubject) -> Unit,
) {
    LaunchedEffect(subject.subjectId) { media.requestBackdrop(subject.subjectId) }
    val backdrop = media.backdropCache[subject.subjectId]
    TvLandscapeCard(
        imageUrl = backdrop?.let(::tmdbBackdropCardUrl) ?: subject.imageUrl,
        title = subject.title,
        onClick = { onClickSubject(subject) },
        onFocused = {
            focusedIndexByRow[row.key] = index
            onCardFocused(subject)
        },
        memoryId = memoryId,
        modifier = Modifier.tvFocusAnchor(focus, TvExplorationCardKey(row.key, index)),
    )
}
