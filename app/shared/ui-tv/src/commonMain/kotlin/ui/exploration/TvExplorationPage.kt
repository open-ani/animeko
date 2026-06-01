/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItemsWithLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import me.him188.ani.app.data.models.recommend.RecommendedSubjectInfo
import me.him188.ani.app.data.models.subject.ContinueWatchingStatus
import me.him188.ani.app.data.models.subject.SubjectCollectionInfo
import me.him188.ani.app.data.models.subject.subjectInfo
import me.him188.ani.app.data.network.BangumiSummaryService
import me.him188.ani.app.data.network.TmdbImageService
import me.him188.ani.app.data.network.matchToEpisodes
import me.him188.ani.app.data.network.newestAiredDateStringOrNull
import me.him188.ani.app.data.network.tmdbStillHeroSizeUrl
import me.him188.ani.app.data.network.toTmdbLanguage
import me.him188.ani.app.data.repository.player.EpisodePlayHistoryRepository
import me.him188.ani.app.data.repository.subject.SetSubjectCollectionTypeOrDeleteUseCase
import me.him188.ani.app.data.repository.subject.SubjectCollectionRepository
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.navigation.SubjectDetailPlaceholder
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.AsyncImage
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.stateOf
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.tv.TvHeroButton
import me.him188.ani.app.ui.foundation.tv.TvPortraitCard
import me.him188.ani.app.ui.foundation.tv.TV_BACKDROP_LEFT_FADE_END
import me.him188.ani.app.ui.foundation.tv.TV_BACKDROP_ASPECT_RATIO
import me.him188.ani.app.ui.foundation.tv.TV_BACKDROP_BOTTOM_FADE_START
import me.him188.ani.app.ui.foundation.tv.TV_BACKDROP_CROSSFADE_MILLIS
import me.him188.ani.app.ui.foundation.tv.TV_HERO_MEDIA_DEBOUNCE_MILLIS
import me.him188.ani.app.ui.foundation.tv.TV_HERO_TEXT_FADE_MILLIS
import me.him188.ani.app.ui.foundation.tv.TV_HERO_TITLE_WIDTH_FRACTION
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_BOTTOM_SCRIM_HEIGHT
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_BOTTOM_SCRIM_MAX_ALPHA
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_CARD_SPACING
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_CARD_WIDTH
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_END_PAD
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_HINT_BOTTOM_PAD
import me.him188.ani.app.ui.foundation.tv.TV_PAGE_HINT_ICON_SIZE
import me.him188.ani.app.ui.foundation.tv.TV_BACKDROP_LEFT_FADE_START
import me.him188.ani.app.ui.foundation.tv.TV_HERO_SUMMARY_WIDTH_FRACTION
import me.him188.ani.app.ui.foundation.tv.tvBackdropFadeFromBlackStops
import me.him188.ani.app.ui.foundation.tv.tvBackdropFadeToBlackStops
import me.him188.ani.app.ui.foundation.tv.tvHeroContentColor
import me.him188.ani.app.ui.foundation.tv.tvHeroMarqueeIterations
import me.him188.ani.app.ui.foundation.tv.tvPlayKeyForceRefresh
import me.him188.ani.app.ui.foundation.focus.TvScrollAnimator
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.tv.tvHeroSecondaryContentColor
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.exploration_continue_watching
import me.him188.ani.app.ui.lang.exploration_recommendations
import me.him188.ani.app.ui.lang.exploration_schedule
import me.him188.ani.app.ui.lang.exploration_tv_air_date
import me.him188.ani.app.ui.lang.exploration_tv_all_caught_up
import me.him188.ani.app.ui.lang.exploration_tv_minutes_left
import me.him188.ani.app.ui.lang.exploration_tv_watched_latest
import me.him188.ani.app.ui.lang.subject_progress_updates_on
import me.him188.ani.app.ui.lang.tv_card_remote_hint
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.datasources.api.toLocalDateOrNull
import me.him188.ani.app.ui.lang.exploration_tv_next_episode
import me.him188.ani.app.ui.lang.exploration_tv_watch_now
import me.him188.ani.app.ui.lang.playback_history_episode_label
import me.him188.ani.app.ui.subject.AiringLabel
import me.him188.ani.app.ui.subject.AiringLabelState
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.analytics.Analytics
import me.him188.ani.utils.analytics.AnalyticsEvent.Companion.SubjectEnter
import me.him188.ani.utils.analytics.recordEvent
import org.jetbrains.compose.resources.stringResource

/**
 * TV 沉浸式探索页: 全屏背景为聚焦条目的 TMDB backdrop (左/下渐隐入背景色),
 * 上半区展示聚焦条目的标题 / Bangumi 评分数字 + 连载信息 / 简介; 下半区为可滚动的卡片区 ——
 * 最高热点与继续观看横向延伸, 推荐纵向无限行. 卡片全部为竖版封面, 聚焦时主题色外圈.
 *
 * 数据加载全异步不阻塞 UI: 聚焦换卡先立即换标题, Bangumi 文字信息 (一次请求, 本地有缓存) 先到先显,
 * TMDB backdrop 慢到慢显 (crossfade). 每个条目的结果都缓存, 回焦即时显示.
 */
@Composable
fun TvExplorationPage(
    state: ExplorationPageState,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val collectionRepo = remember { GlobalKoin.get<SubjectCollectionRepository>() }
    val tmdb = remember { GlobalKoin.get<TmdbImageService>() }
    val bangumiSummaryService = remember { GlobalKoin.get<BangumiSummaryService>() }
    val setCollectionTypeUseCase = remember { GlobalKoin.get<SetSubjectCollectionTypeOrDeleteUseCase>() }
    val settingsRepository = remember { GlobalKoin.get<SettingsRepository>() }
    val playHistoryRepository = remember { GlobalKoin.get<EpisodePlayHistoryRepository>() }
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    // 聚焦条目 (卡片 onFocusChanged 上报); 标题/封面来自卡片自身数据, 立即可显示
    var heroTarget by remember { mutableStateOf<TvHeroTarget?>(null) }
    // subjectId -> Bangumi 完整条目信息 (评分/连载/简介). 在看列表的条目自带, 聚焦时直接种入.
    val infoCache = remember { mutableStateMapOf<Int, SubjectCollectionInfo>() }
    // subjectId -> TMDB backdrop URL (null = 已查过但没有, 不再重查; 请求失败不缓存)
    val backdropCache = remember { mutableStateMapOf<Int, String?>() }
    // 继续观看行: subjectId -> 下一集 TMDB 数据 (剧照 + 单集简介; 字段为 null = 查过没有).
    // 记录 episodeId 是为了看完一集后 (下一集变化) 自动换图, 服务层有持久缓存, 重查很便宜.
    val episodeStillCache = remember { mutableStateMapOf<Int, TvNextEpisodeMedia>() }
    // 播放历史 (响应式): 退出播放器回到本页时进度条 / 剩余分钟自动更新.
    // 继续观看卡的进度条与 hero 剩余分钟都从这里取"下一集"的播放位置.
    val playHistories by playHistoryRepository.flow.collectAsStateWithLifecycle(emptyList())
    // subjectId -> bgm.tv 简介兜底 (Ani 服务器部分条目 summary 为空, 直连 bgm.tv 补; "" = 也没有)
    val summaryFallbackCache = remember { mutableStateMapOf<Int, String>() }

    // 异步加载聚焦条目的 Hero 数据: 焦点换卡时 collectLatest 取消在途请求, 不会卡 UI
    LaunchedEffect(Unit) {
        snapshotFlow { heroTarget }.filterNotNull().collectLatest { target ->
            coroutineScope {
            var info = infoCache[target.subjectId]
            if (info == null) {
                // 防抖: 快速划过卡片时不发请求
                delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS)
                // 慢网络就等 (焦点换卡时 collectLatest 会取消, 等待无害): 之前这里包了
                // 10s 超时, 超时静默放弃整条流水线且无重试 —— 表现为卡片只剩标题,
                // backdrop/简介永远不出来, 换卡再回来 (结果已进缓存) 才瞬间出现.
                // 取消异常必须重抛 (runCatching 会吞掉, 让已取消的协程继续跑到 return)
                info = try {
                    collectionRepo.subjectCollectionFlow(target.subjectId).first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                } ?: return@coroutineScope // 真实异常才放弃, 下次聚焦重试
                infoCache[target.subjectId] = info
            }
            // 过期缓存自刷新: repository 的 flow 先 emit 本地缓存 (可能过期, 如收藏时"未开播"、
            // 现已完结), 过期时会拉服务器并再次 emit. 上面 .first() 拿到旧值就取消会把刷新请求
            // 一并取消, 过期状态永远留在页面 —— 这里持续收集, 后续 emission 覆盖 infoCache
            // (聚焦换卡时 collectLatest 取消; 延迟一拍避免快速划卡时空转)
            launch {
                delay(TV_HERO_MEDIA_DEBOUNCE_MILLIS)
                runCatching {
                    collectionRepo.subjectCollectionFlow(target.subjectId).collect { fresh ->
                        infoCache[target.subjectId] = fresh
                    }
                }
            }
            // 继续观看: hero 背景优先用"下一集"的单集剧照, 直观提示播放进度节点.
            // 连载番的永久缓存可能不含新播集, 传已播出最新集日期触发陈旧重取 (服务层闸门限频).
            val nextEpisodeId = if (target.fromFollowed) info.progressInfo.nextEpisodeIdToPlay else null
            if (nextEpisodeId != null && episodeStillCache[target.subjectId]?.episodeId != nextEpisodeId) {
                runCatching {
                    val language = (settingsRepository.uiSettings.flow.first().appLanguage ?: Locale.current)
                        .toTmdbLanguage()
                    val stills = tmdb.getEpisodeStills(
                        target.subjectId, info.subjectInfo.name, language,
                        newestWantedAirDate = info.episodes.newestAiredDateStringOrNull(),
                    )
                    stills.matchToEpisodes(info.episodes)[nextEpisodeId]
                }.onSuccess { media ->
                    // 存原图档 URL, 显示时才按设置降档 (见下方 backdropUrl) —— 存降档结果的话
                    // 用户开完整视觉效果得清缓存才生效
                    episodeStillCache[target.subjectId] =
                        TvNextEpisodeMedia(nextEpisodeId, media?.stillUrl, media?.overview)
                }
            }
            // 整部 backdrop: 单集剧照缺失时的兜底 (以及非继续观看行的主图), 拿到剧照就不再拉
            val hasEpisodeStill = target.fromFollowed && episodeStillCache[target.subjectId]?.stillUrl != null
            if (!hasEpisodeStill && target.subjectId !in backdropCache) {
                runCatching {
                    // 官方主背景图 (与详情页 hero 同源, 进详情零跳变); 屏保轮播才用全量列表.
                    // 传最新已播集日期: 新番刚播时 TMDB 往往还没有 backdrop, 负缓存据此限期失效
                    tmdb.getBackdropUrl(
                        target.subjectId,
                        info.subjectInfo.name,
                        activeAsOfDate = info.episodes.newestAiredDateStringOrNull(),
                    )
                }.onSuccess { url ->
                    backdropCache[target.subjectId] = url
                }
            }
            // Ani 服务器简介为空时直连 bgm.tv 补 (服务端部分条目 summary 缺失, 仅替代不合并).
            // 放在 backdrop 请求之后: 兜底请求不拖慢背景图显示.
            // 网络错误不写缓存 (getSummary 抛出): 下次聚焦该条目重试, 不把瞬时断网当"确认没有".
            if (info.subjectInfo.summary.isBlank() && target.subjectId !in summaryFallbackCache) {
                runCatching { bangumiSummaryService.getSummary(target.subjectId) }
                    .onSuccess { summaryFallbackCache[target.subjectId] = it.orEmpty() }
            }
            }
        }
    }

    // 继续观看优先展示下一集剧照, 缺失时回退整部 backdrop.
    // 剧照按设置降档: 默认 w1280 (原图偶有 4K 级, 解码 8-33MB, 是低端盒子每次换卡的重锤;
    // 铺满后经渐隐压暗在 10-foot 距离不可辨), 开了完整视觉效果才用原图. backdrop 那路
    // 服务层已是 w1280 档, 不受影响
    val fullVisualEffects = LocalThemeSettings.current.tvFullVisualEffects
    val backdropUrl = heroTarget?.let { target ->
        (if (target.fromFollowed) {
            episodeStillCache[target.subjectId]?.stillUrl?.let { tmdbStillHeroSizeUrl(it, fullVisualEffects) }
        } else null)
            ?: backdropCache[target.subjectId]
    }

    val onFocusItem: (subjectId: Int, title: String, seed: SubjectCollectionInfo?, fromFollowed: Boolean) -> Unit =
        { subjectId, title, seed, fromFollowed ->
            // 继续观看行的 seed 来自 paging flow (始终最新), 无条件覆盖 —— 看完一集回到本页时
            // 进度/下一集要跟着变 (只在缺失时写入会把 info 冻结在页面首次聚焦时的状态)
            if (seed != null && (fromFollowed || subjectId !in infoCache)) infoCache[subjectId] = seed
            heroTarget = TvHeroTarget(subjectId, title, fromFollowed)
        }
    val navigateToSubject: (subjectId: Int, name: String, cover: String, source: String) -> Unit =
        { subjectId, name, cover, source ->
            Analytics.recordEvent(SubjectEnter) {
                put("source", source)
                put("subject_id", subjectId)
            }
            navigator.navigateSubjectDetails(
                subjectId = subjectId,
                placeholder = SubjectDetailPlaceholder(id = subjectId, name = name, coverUrl = cover),
            )
        }
    // 立即观看: 直接进播放页 —— 有观看进度接着播下一集, 没有则从第一集开始;
    // 分集信息尚未加载到 (聚焦后异步拉取中) 时退化为进详情页, 保证点击总有响应
    val navigateToPlay: (subjectId: Int, name: String, cover: String, source: String) -> Unit =
        { subjectId, name, cover, source ->
            val info = infoCache[subjectId]
            val episodeId = info?.progressInfo?.nextEpisodeIdToPlay
                ?: info?.episodes?.firstOrNull()?.episodeId
            if (episodeId != null) {
                Analytics.recordEvent(SubjectEnter) {
                    put("source", source)
                    put("subject_id", subjectId)
                }
                navigator.navigateEpisodeDetails(subjectId, episodeId)
            } else {
                navigateToSubject(subjectId, name, cover, source)
            }
        }
    // 卡片长按弹出的收藏下拉 (复用详情页收藏按钮的 EditCollectionTypeDropDown). 当前收藏状态取自
    // infoCache (聚焦时异步拉取的 SubjectCollectionInfo), 未就绪则按未收藏处理.
    val collectionMenuFor: (Int) -> @Composable (expanded: Boolean, onDismiss: () -> Unit) -> Unit =
        { subjectId ->
            { expanded, onDismiss ->
                EditCollectionTypeDropDown(
                    currentType = infoCache[subjectId]?.collectionType ?: UnifiedCollectionType.NOT_COLLECTED,
                    expanded = expanded,
                    onDismissRequest = onDismiss,
                    onClick = { action ->
                        scope.launch {
                            runCatching { setCollectionTypeUseCase(subjectId, action.type) }
                                .onFailure { toaster.showLoadError(LoadError.fromException(it)) }
                        }
                    },
                    // 卡片的菜单只有长按一个入口, 恒吞掉那次长按残余的确认键
                    modifier = Modifier.consumeHeldConfirmKey(),
                )
            }
        }

    // 最高热度改为 Hero 轮播 (无卡片行): 两枚操作按钮 (立即观看 / 更多详细内容) + 右侧不可聚焦
    // 的轮播指示器. 焦点在按钮上 = TRENDING, hero 由 carouselIndex 驱动. 焦点在按钮上时左右键手动切换轮播
    // (在第一个条目按左键则不消费, 交给焦点系统去聚焦侧边栏探索按钮); 用户静止一段时间后自动轮播下一个.
    val trending = state.trendingSubjectInfoPager
    val carouselSize = minOf(trending.itemCount, TV_CAROUSEL_MAX_DOTS)
    // 这几个 UI 状态用 rememberSaveable 跨导航保存 (进详情页返回后区块/滚动/轮播位置不变)
    var carouselIndex by rememberSaveable { mutableIntStateOf(0) }
    // 每次用户手动切换 +1, 用作自动轮播 LaunchedEffect 的 key: 手动操作即重置计时 ("否则就不动")
    var carouselInteraction by remember { mutableIntStateOf(0) }
    var focusedSection by rememberSaveable { mutableStateOf(TvExplorationSection.TRENDING) }
    var recFocusedRow by rememberSaveable { mutableIntStateOf(0) }
    // 返回键分层规则用: 卡片区是否持有焦点 / 当前聚焦卡在行内的真实下标
    var cardAreaHasFocus by remember { mutableStateOf(false) }
    var focusedCardIndexInRow by remember { mutableIntStateOf(0) }
    // 统一焦点落点请求 (回 hero / 返回回行首卡 / 回推荐首行 / 进页恢复焦点共用, 见
    // [TvExplorationFocusTarget]): 解析分两段 —— 页面段把纵向列表滚到目标行让它组合
    // (hero 目标则轮询聚焦立即观看按钮), 行内段由 TvAnchoredCardRow 滚动/聚焦/到位
    // 确认后清空本请求.
    var focusTarget by remember { mutableStateOf<TvExplorationFocusTarget?>(null) }
    // 进页那一刻的恢复目标快照 (focusedSection/recFocusedRow 已跨导航保存)
    val restoreSection = remember { focusedSection }
    val restoreRow = remember { recFocusedRow }
    val carouselItem = if (carouselSize > 0) trending[carouselIndex.coerceIn(0, carouselSize - 1)] else null

    // 手动切换轮播 (delta = ±1, 循环); 同时重置自动轮播计时
    val switchCarousel: (Int) -> Unit = { delta ->
        if (carouselSize > 0) {
            carouselIndex = ((carouselIndex + delta) % carouselSize + carouselSize) % carouselSize
            carouselInteraction++
        }
    }
    // 从卡片区顶行按上键 / 行首卡按返回: 回到 hero (统一落点解析聚焦立即观看按钮)
    val navigateUpToHero: () -> Boolean = {
        focusTarget = TvExplorationFocusTarget(
            TvExplorationSection.TRENDING,
            seq = (focusTarget?.seq ?: 0) + 1,
        )
        true
    }
    // 返回键分层规则 (统一在页面级决策, 页面已有完整的焦点簿记):
    //   不在行首卡 -> 回本行首卡; 推荐区非首行的行首卡 -> 回推荐首行的首卡; 行首卡 -> 立即观看.
    BackHandler(
        enabled = cardAreaHasFocus && focusedSection != TvExplorationSection.TRENDING,
    ) {
        when {
            focusedCardIndexInRow > 0 -> focusTarget = TvExplorationFocusTarget(
                focusedSection,
                row = recFocusedRow,
                cardIndex = 0,
                seq = (focusTarget?.seq ?: 0) + 1,
            )

            focusedSection == TvExplorationSection.RECOMMENDATIONS && recFocusedRow > 0 ->
                focusTarget = TvExplorationFocusTarget(
                    TvExplorationSection.RECOMMENDATIONS,
                    row = 0,
                    cardIndex = 0,
                    seq = (focusTarget?.seq ?: 0) + 1,
                )

            else -> navigateUpToHero()
        }
    }

    // TRENDING 时轮播条目驱动 hero (标题即时, 评分/连载/简介/backdrop 异步跟上)
    LaunchedEffect(carouselIndex, focusedSection, carouselSize) {
        if (focusedSection == TvExplorationSection.TRENDING && carouselSize > 0) {
            trending[carouselIndex.coerceIn(0, carouselSize - 1)]?.let {
                onFocusItem(it.bangumiId, it.nameCn, null, false)
            }
        }
    }
    // 自动轮播: 仅在 TRENDING 时推进; carouselInteraction 变化 (手动切换) 会重启本效果, 重置计时
    LaunchedEffect(carouselSize, focusedSection, carouselInteraction) {
        if (focusedSection != TvExplorationSection.TRENDING || carouselSize <= 1) {
            return@LaunchedEffect
        }
        while (true) {
            delay(TV_CAROUSEL_AUTO_ADVANCE_MILLIS)
            carouselIndex = (carouselIndex + 1) % carouselSize
        }
    }
    // 初始/返回焦点 (统一在此处, ExplorationScreen 不再对沉浸式布局单独抢焦点; 统一走落点
    // 解析): 首次进入或曾在 hero -> hero 主按钮; 曾在某卡片行 -> 恢复到该行此前聚焦的卡片
    // (cardIndex = -1: 行自己跨导航保存的聚焦下标).
    LaunchedEffect(Unit) {
        focusTarget = TvExplorationFocusTarget(restoreSection, row = restoreRow, seq = 1)
    }
    // hero 的播放键: 短按直接播当前轮播条目 (按钮本身走确认键进详情, 同卡片的约定),
    // 长按强制刷新在看 —— 与卡片上那套完全一致, 同一页不能两种手感
    val heroPlayKeyModifier = tvPlayKeyForceRefresh(
        onRefresh = { state.refreshFollowedSubjects() },
        onPlay = {
            carouselItem?.let {
                navigateToPlay(it.bangumiId, it.nameCn, it.imageLarge, "home_trending_play")
                true
            } ?: false
        },
    )

    Box(modifier.fillMaxSize()) {
        // 背景 backdrop 层: 按原比例 (16:9) 缩放, 贴右上角, 高度为屏高的固定比例 (对齐 Prime 实测:
        // 图占屏顶约 76%). 左缘/下缘渐隐入页面背景, 保证叠在渐隐区上的文字可读.
        // 两态渐变 (Prime 两张截图实测): 焦点在 hero (轮播) 时收得晚 (下缘 58%→76% 屏高渐隐,
        // 左缘只遮一小段); 焦点落到卡片区时下缘提前收、左缘大幅加深 (44%→72% 屏高, 清晰区只剩
        // 右上角), 卡片行压在 <25% 可见度的长尾上. 两组停点间用动画插值平滑过渡.
        val backdropCardness by animateFloatAsState(
            if (focusedSection == TvExplorationSection.TRENDING) 0f else 1f,
            animationSpec = tween(TV_BACKDROP_STATE_ANIM_MILLIS),
            label = "backdropCardness",
        )
        // 渐隐 = 直接叠画页面底色渐变 (本页在主壳内, 图下即 shellBackgroundColor 纯色),
        // 与旧 DstOut 擦除逐像素等价但不再需要离屏合成 —— 两态插值期间旧实现每帧把整块
        // 3.6MP 离屏缓冲重光栅化, 是低端 GPU 的填充率大头 (2026-07-31 性能整改)
        val backdropFadeColor = AniThemeDefaults.shellBackgroundColor
        Crossfade(
            backdropUrl,
            Modifier.align(Alignment.TopEnd),
            animationSpec = tween(TV_BACKDROP_CROSSFADE_MILLIS),
        ) { url ->
            if (url != null) {
                Box(
                    Modifier
                        .fillMaxHeight(TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION)
                        .aspectRatio(TV_BACKDROP_ASPECT_RATIO, matchHeightConstraintsFirst = true)
                        .drawWithContent {
                            drawContent()
                            // 渐变带端点在两态间插值; 停点由平滑曲线采样生成 (无折点,
                            // 避免暗色端可见的马赫带分界线), 曲线形状两态共用.
                            val t = backdropCardness
                            // 左缘渐隐 (叠画页面底色, 文字叠在这段上仍可读)
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    *tvBackdropFadeFromBlackStops(
                                        // 卡片态端点用三页共享值 (轮播 hero 态维持自己的一套)
                                        start = lerp(0f, TV_BACKDROP_LEFT_FADE_START, t),
                                        end = lerp(0.46f, TV_BACKDROP_LEFT_FADE_END, t),
                                        color = backdropFadeColor,
                                    ),
                                ),
                            )
                            // 下缘渐隐: 零斜率极缓起步 + 指数级长尾渐近全遮, 一直渐变到图底,
                            // 两端都看不到分界线 (图坐标; 50% 遮盖点 hero 态 ≈ 屏高 65%, 卡片态 ≈ 55%)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    *tvBackdropFadeToBlackStops(
                                        start = lerp(
                                            TV_EXPLORATION_BOTTOM_FADE_START_HERO,
                                            TV_BACKDROP_BOTTOM_FADE_START,
                                            t,
                                        ),
                                        end = 1f,
                                        color = backdropFadeColor,
                                    ),
                                ),
                            )
                        },
                ) {
                    AsyncImage(
                        url,
                        contentDescription = null,
                        Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }

        // 内容列: 左侧再留 TV_EXPLORATION_START_PAD (外层已让开侧边栏 48dp) ——
        // 总左缘 64dp, 使侧边栏按钮中心 (32dp) 恰好在屏幕左缘与内容左缘的正中间
        Column(
            Modifier.fillMaxSize()
                .padding(start = TV_EXPLORATION_START_PAD),
        ) {
            val heroExpanded = focusedSection == TvExplorationSection.TRENDING
            // Hero 区: 信息块 (固定高度, 保证不同条目切换时卡片区不跳) + 展开态才有的操作按钮 (在其下方,
            // 短间距). 右侧居中悬浮不可聚焦的轮播指示器 (仅展开态). 焦点移到下方卡片时按钮/指示器消失,
            // 卡片区 (weight) 顺势上移贴住信息块 —— 无大片空白.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, end = TV_PAGE_END_PAD),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 顶部固定 (对齐 backdrop 顶部); 高度即"介绍块上下边界距离": 展开态用标准高度,
                    // 卡片区聚焦时用 COLLAPSED 高度并让文字底对齐 —— 块变高时文字下边界随之下移并
                    // 紧贴下方卡片, 一个变量同时控制介绍下边界与卡片行位置.
                    // 换轮播/聚焦条目时整块文字渐隐渐现 (contentKey=条目), 消除瞬时替换的闪动;
                    // 过渡期间退场内容读退场条目自己的缓存数据. 块内始终顶对齐: 标题固定在块顶,
                    // 有 info 时简介 weight(1f) 撑满至块底; 无 info (等 API) 时标题仍停在顶部,
                    // info 到达不引起位置跳动 —— 加载前后文字位置一致.
                    AnimatedContent(
                        targetState = heroTarget,
                        modifier = Modifier.fillMaxWidth()
                            .height(if (heroExpanded) TV_HERO_INFO_HEIGHT else TV_HERO_INFO_HEIGHT_COLLAPSED),
                        transitionSpec = {
                            fadeIn(tween(TV_HERO_TEXT_FADE_MILLIS)) togetherWith
                                    fadeOut(tween(TV_HERO_TEXT_FADE_MILLIS))
                        },
                        contentKey = { it?.subjectId },
                        label = "heroInfoText",
                    ) { target ->
                        val info = target?.let { infoCache[it.subjectId] }
                        Column(
                            Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (target != null) {
                                // 定高一行 + 超宽跑马灯 (宽度框不变): 长标题原本会换到第二行,
                                // 把介绍挤掉一行, 且不同条目间标题一行/两行来回跳
                                Text(
                                    target.title,
                                    Modifier.fillMaxWidth(TV_HERO_TITLE_WIDTH_FRACTION)
                                        .basicMarquee(iterations = tvHeroMarqueeIterations()),
                                    color = tvHeroContentColor(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Clip, // 跑马灯滚全文, 不要省略号
                                )
                            }
                            if (info != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    // 评分: ★ 评分数字/10
                                    val score = info.subjectInfo.ratingInfo.score
                                    if ((score.toFloatOrNull() ?: 0f) > 0f) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.Star,
                                                contentDescription = null,
                                                Modifier.size(18.dp),
                                                tint = MaterialTheme.colorScheme.primary,
                                            )
                                            Text(
                                                "$score/10",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                        }
                                    }
                                    // 连载信息, 后面跟一个空格 + 开播年月
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        AiringLabel(
                                            remember(info) {
                                                AiringLabelState(
                                                    stateOf(info.airingInfo),
                                                    stateOf(info.progressInfo),
                                                )
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            progressColor = tvHeroSecondaryContentColor(),
                                        )
                                        val airDate = info.subjectInfo.airDate
                                        if (airDate.isValid) {
                                            Text(
                                                "    " + stringResource(
                                                    Lang.exploration_tv_air_date,
                                                    airDate.year, airDate.month,
                                                ),
                                                color = tvHeroSecondaryContentColor(),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                }
                                // 继续观看: 独立一行显示下一集集号 + 集名 (Bangumi 本地数据, 即时显示)
                                val nextEp = if (target?.fromFollowed == true) {
                                    info.progressInfo.nextEpisodeIdToPlay?.let { nextId ->
                                        info.episodes.firstOrNull { it.episodeId == nextId }
                                    }
                                } else null
                                if (nextEp != null) {
                                    val epLabel = stringResource(
                                        Lang.playback_history_episode_label,
                                        nextEp.episodeInfo.sort.toString(),
                                    )
                                    val epName = nextEp.episodeInfo.nameCn.ifBlank { nextEp.episodeInfo.name }
                                    // 三态 (nextEp 语义见 SubjectProgressInfo.compute — 追平时指回已看完的最新一集):
                                    //  - 已看完最新一集/全部 (Watched/Done): "第 8 集 · 集名 · 已看完"
                                    //  - 看到一半 (有播放记录): "第 4 集 · 集名 · 剩余 23 分钟"
                                    //  - 看完上一集且有新集/还没开始: "下一集: 第 4 集 · 集名".
                                    // 集号与尾段为固定段永不截断; 集名居中段, 超长跑马灯滚动展示全文
                                    val caughtUp = info.progressInfo.continueWatchingStatus.let {
                                        it is ContinueWatchingStatus.Watched || it is ContinueWatchingStatus.Done
                                    }
                                    val remainingMinutes = if (caughtUp) null else playHistories
                                        .firstOrNull { it.episodeId == nextEp.episodeId }
                                        ?.let { history ->
                                            val duration = history.durationMillis
                                            if (duration != null && duration > 0 && history.positionMillis > 0) {
                                                // 向上取整: 剩 30 秒也显示 1 分钟
                                                (((duration - history.positionMillis).coerceAtLeast(0L) + 59_999) / 60_000)
                                                    .toInt().coerceAtLeast(1)
                                            } else null
                                        }
                                    Row(
                                        Modifier.fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION)
                                            // 定高使"本行 + 10dp 列间距"恰为简介两行行距 (2×20dp):
                                            // 有无此行时简介的换行网格对齐, 最后一行结束位置一致,
                                            // 继续观看/推荐两态下文字到下方卡片的距离才相同
                                            .height(TV_HERO_STATUS_ROW_HEIGHT),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        val epInfoColor = tvHeroSecondaryContentColor()
                                        val epInfoStyle = MaterialTheme.typography.labelLarge
                                        Text(
                                            if (caughtUp || remainingMinutes != null) epLabel
                                            else stringResource(Lang.exploration_tv_next_episode, epLabel),
                                            color = epInfoColor,
                                            style = epInfoStyle,
                                            maxLines = 1,
                                        )
                                        if (epName.isNotBlank()) {
                                            Text(
                                                " · $epName",
                                                Modifier.weight(1f, fill = false)
                                                    .basicMarquee(iterations = tvHeroMarqueeIterations()),
                                                color = epInfoColor,
                                                style = epInfoStyle,
                                                maxLines = 1,
                                                softWrap = false,
                                                overflow = TextOverflow.Clip,
                                            )
                                        }
                                        if (caughtUp) {
                                            // 连载已追平: "已看完最新一集 · 周三更新" (更新时间同详情页观看按钮,
                                            // WeekFormatter); 完结看完: "已看完"
                                            val watchedStatus = info.progressInfo.continueWatchingStatus
                                                    as? ContinueWatchingStatus.Watched
                                            val updatesOn = watchedStatus?.nextEpisodeAirDate?.toLocalDateOrNull()
                                                ?.let { date ->
                                                    stringResource(
                                                        Lang.subject_progress_updates_on,
                                                        WeekFormatter.System.format(date),
                                                    )
                                                }
                                            Text(
                                                " · " + (
                                                    if (watchedStatus != null) {
                                                        stringResource(Lang.exploration_tv_watched_latest)
                                                    } else {
                                                        stringResource(Lang.exploration_tv_all_caught_up)
                                                    }
                                                    ) + (updatesOn?.let { " · $it" } ?: ""),
                                                color = epInfoColor,
                                                style = epInfoStyle,
                                                maxLines = 1,
                                            )
                                        } else if (remainingMinutes != null) {
                                            Text(
                                                " · " + stringResource(
                                                    Lang.exploration_tv_minutes_left, remainingMinutes,
                                                ),
                                                color = epInfoColor,
                                                style = epInfoStyle,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                                // 简介占满信息块剩余高度: 行数由 TV_HERO_INFO_HEIGHT 决定; 宽度用单独的
                                // HERO_SUMMARY_WIDTH_FRACTION, 调小让右边留给 backdrop 清晰区, 文字更易读.
                                // 继续观看: 优先展示下一集的 TMDB 单集简介 (回忆剧情起点), 缺失回退整部简介
                                val nextEpOverview = target?.takeIf { it.fromFollowed }
                                    ?.let { episodeStillCache[it.subjectId]?.overview }
                                    ?.takeIf { it.isNotBlank() }
                                Text(
                                    nextEpOverview
                                        ?: info.subjectInfo.summary.trim()
                                            .ifBlank { target?.let { summaryFallbackCache[it.subjectId] }.orEmpty() },
                                    Modifier.weight(1f).fillMaxWidth(TV_HERO_SUMMARY_WIDTH_FRACTION),
                                    color = tvHeroContentColor(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    // 按钮块 (仅展开态): 一颗作用于当前轮播条目的动作钮 + 一颗页面级入口.
                    // 左右键手动切换轮播, 首个条目按左不消费, 交给焦点系统去聚焦侧边栏探索按钮.
                    // 关闭 48dp 最小可交互尺寸约束, 否则缩小后的按钮被撑到 48dp 高、内容居中,
                    // 两枚之间会出现空白.
                    if (heroExpanded) {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                            Column(
                                Modifier
                                    .padding(top = TV_HERO_INFO_TO_BUTTONS_GAP)
                                    .then(heroPlayKeyModifier)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                        when (event.key) {
                                            Key.DirectionLeft -> {
                                                if (carouselIndex > 0) {
                                                    switchCarousel(-1)
                                                    true
                                                } else {
                                                    false // 第一个条目: 交给焦点系统 -> 侧边栏探索按钮
                                                }
                                            }

                                            Key.DirectionRight -> {
                                                switchCarousel(1)
                                                true
                                            }

                                            else -> false
                                        }
                                    },
                                verticalArrangement = Arrangement.spacedBy(TV_HERO_BUTTON_GAP),
                            ) {
                                // hero 只留一颗动作钮, 与卡片同一套约定: 确认 = 进详情,
                                // 播放键 = 直接播 (见 heroPlayKeyModifier; 右下角提示已公示).
                                // 图标用播放三角与遥控器播放键呼应 —— 省下的那一行给时间表入口
                                TvHeroButton(
                                    text = stringResource(Lang.exploration_tv_watch_now),
                                    icon = Icons.Rounded.PlayArrow,
                                    filled = true,
                                    onClick = {
                                        carouselItem?.let {
                                            navigateToSubject(it.bangumiId, it.nameCn, it.imageLarge, "home_trending_detail")
                                        }
                                    },
                                    onFocused = { focusedSection = TvExplorationSection.TRENDING },
                                    // 进入主页 / 从卡片区按上返回时的聚焦目标
                                    focusRequester = state.trendingFirstItemFocusRequester,
                                    onFocusChangedExtra = { state.trendingFirstItemFocused.value = it },
                                )
                                // 新番时间表入口: 页面级目的地, 与上面那颗"对当前条目的动作"不是
                                // 同一层级 —— 用描边款区分, 间距仍与原来两颗按钮时一致
                                TvHeroButton(
                                    text = stringResource(Lang.exploration_schedule),
                                    icon = Icons.Rounded.CalendarMonth,
                                    filled = false,
                                    onClick = { navigator.navigateSchedule() },
                                    onFocused = { focusedSection = TvExplorationSection.TRENDING },
                                )
                            }
                        }
                    }
                }
            }

            // 卡片区: 持久透明区块标题 (显示当前聚焦区块) + 其下裁剪滚动区. LazyColumn 默认裁剪到自身
            // 边界, 聚焦行吸到其顶部, 上方行 (含前面区块 / 推荐前几行) 被裁掉不外露 —— 满足"聚焦某区块
            // 时上方全部挡住、推荐第二行起前面卡片看不见". 标题独立在滚动区之上, 透明浮在 backdrop 上,
            // 不随滚动、不挡背景. 每个区块一行, 均为固定锚点轮播 (焦点靠左不动, 卡片滑动).
            val followedItems = state.followedSubjectsPager.collectAsLazyPagingItemsWithLifecycle()
            val recommendations = state.recommendationPager.collectAsLazyPagingItemsWithLifecycle()
            val hasFollowed = followedItems.itemCount > 0

            // 继续观看行的播放键: 短按续播聚焦那部, 长按强制重拉本栏目 (它平时只跟着仓库里
            // 一小时一跳的定时同步走, 想立刻确认某部更没更需要一个入口).
            // 一份 modifier 给整行共用: 同一时刻只有一张卡有焦点, 按键只会送到那一张; 落点用
            // 页面记的"行内聚焦下标"取, 不捕获某张卡自己的数据.
            // 推荐行不加 —— 那里刷新没有意义 (换的是推荐结果, 不是"更没更")
            val followedPlayKeyModifier = tvPlayKeyForceRefresh(
                onRefresh = { state.refreshFollowedSubjects() },
                onPlay = {
                    val subject = focusedCardIndexInRow
                        .takeIf { focusedSection == TvExplorationSection.FOLLOWED }
                        ?.let { runCatching { followedItems.peek(it) }.getOrNull() }
                        ?.subjectInfo
                    if (subject != null) {
                        navigateToPlay(
                            subject.subjectId, subject.displayName, subject.imageLarge,
                            "home_followed_play",
                        )
                        true
                    } else {
                        false
                    }
                },
            )

            // 持久区块标题 (透明浮层, 显示当前聚焦区块名). TRENDING 时不需要标题 (hero 自带信息), 用极小
            // 间距代替那 40dp 空标题, 使继续观看紧贴按钮下方 (只留 TV_HERO_BUTTONS_TO_CONTENT_GAP).
            if (focusedSection == TvExplorationSection.TRENDING) {
                Spacer(Modifier.height(TV_HERO_BUTTONS_TO_CONTENT_GAP))
            } else {
                TvSectionHeader(
                    when (focusedSection) {
                        TvExplorationSection.TRENDING -> ""
                        TvExplorationSection.FOLLOWED -> stringResource(Lang.exploration_continue_watching)
                        TvExplorationSection.RECOMMENDATIONS -> stringResource(Lang.exploration_recommendations)
                    },
                )
                // 聚焦某区块时行内标题被吸顶滚出, 只剩这个持久标题紧贴卡片行 (行内标题原本的列间距消失,
                // 显得标题贴卡). 用此间距补回标题到卡片行的距离 (与未聚焦时同一参数, 保证两种情形一致).
                Spacer(Modifier.height(TV_SECTION_HEADER_TO_ROW_GAP))
            }

            val noBringIntoView = remember {
                object : BringIntoViewSpec {
                    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
                }
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoView) {
                val listState = rememberLazyListState()
                // 列表 item (标题与卡片行拆开, 已无热点行): 继续观看标题=0 行=1 (若有); 之后推荐标题 + 各行.
                // 吸顶滚动到"卡片行"(而非标题): 聚焦区块的行内标题被裁到视口上方, 由顶部持久标题代替 (避免重复).
                // TRENDING 时滚到 0, 让继续观看标题+首行在 hero 下方露出.
                val recRowBase = if (hasFollowed) 3 else 1
                // 动画器提到 effect 外: 该 effect 每次换行整个重启, 器在外面速度才能跨段继承
                // (连按下键时行间滚动连续流动而非逐行重启)
                val rowScrollAnimator = remember { TvScrollAnimator() }
                LaunchedEffect(focusedSection, recFocusedRow, hasFollowed) {
                    val target = when (focusedSection) {
                        TvExplorationSection.TRENDING -> 0
                        TvExplorationSection.FOLLOWED -> if (hasFollowed) 1 else 0
                        TvExplorationSection.RECOMMENDATIONS -> recRowBase + recFocusedRow
                    }
                    rowScrollAnimator.animateScrollToItem(listState, target)
                }
                // 统一落点解析的页面段: hero 目标 -> 置 TRENDING 组合出按钮后轮询聚焦
                // (requestFocus 未附着时静默失败, 用聚焦标志判成功); 行目标 -> 先对齐吸顶
                // 簿记 (吸顶滚动与本目标一致, 不互相拉扯), 再滚动纵向列表让目标行组合
                // (行内段由 TvAnchoredCardRow 接手, 到位即清空 focusTarget), 超时放弃.
                // 行号→列表项换算每轮重算: 恢复期间"继续观看"分页到达会使推荐行整体下移.
                LaunchedEffect(focusTarget) {
                    val target = focusTarget ?: return@LaunchedEffect
                    if (target.section == TvExplorationSection.TRENDING) {
                        focusedSection = TvExplorationSection.TRENDING
                        resolveFocusRepeatedly(arrived = { state.trendingFirstItemFocused.value }) {
                            runCatching { state.trendingFirstItemFocusRequester.requestFocus() }
                        }
                        focusTarget = null
                    } else {
                        if (target.section == TvExplorationSection.RECOMMENDATIONS) {
                            recFocusedRow = target.row
                        }
                        // attempts 80: 等"继续观看"分页数据到达比纯组合时序慢
                        resolveFocusRepeatedly(
                            attempts = 80,
                            arrived = { focusTarget != target }, // 行内段已确认到位
                        ) {
                            val followedNow = followedItems.itemCount > 0
                            val columnItem = when {
                                target.section == TvExplorationSection.FOLLOWED ->
                                    if (followedNow) 1 else null // 分页未到: 等数据

                                else -> (if (followedNow) 3 else 1) + target.row
                            }
                            if (columnItem != null) runCatching { listState.scrollToItem(columnItem) }
                        }
                        if (focusTarget == target) focusTarget = null
                    }
                }

                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth().clipToBounds()
                        .onFocusChanged { cardAreaHasFocus = it.hasFocus },
                    state = listState,
                    contentPadding = PaddingValues(end = TV_PAGE_END_PAD, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(TV_SECTION_HEADER_TO_ROW_GAP),
                ) {
                    // 行都带稳定 key: "继续观看"分页迟到使 hasFollowed 翻转时, 无 key 的行按
                    // 位置错位继承状态 (rememberSaveable 的行内聚焦下标串行), 且全部行重建
                    if (hasFollowed) {
                        item(key = "followed-header") {
                            TvSectionHeader(
                                stringResource(Lang.exploration_continue_watching),
                                transparent = focusedSection == TvExplorationSection.FOLLOWED,
                            )
                        }
                        item(key = "followed-row") {
                            // 继续观看是最顶行: 按上键回到 hero
                            TvAnchoredCardRow(
                                itemCount = followedItems.itemCount,
                                onNavigateUp = navigateUpToHero,
                                focusTarget = focusTarget?.takeIf {
                                    it.section == TvExplorationSection.FOLLOWED
                                },
                                onFocusTargetArrived = { focusTarget = null },
                            ) { index, reportFocus ->
                                val item = followedItems[index]
                                val subject = item?.subjectInfo
                                TvPortraitCard(
                                    imageUrl = subject?.imageLarge,
                                    contentDescription = subject?.displayName,
                                    onClick = {
                                        subject?.let {
                                            navigateToSubject(it.subjectId, it.displayName, it.imageLarge, "home_followed")
                                        }
                                    },
                                    onFocused = {
                                        item?.let {
                                            // 在看条目自带完整信息, 种入缓存立即显示评分/连载/简介
                                            onFocusItem(
                                                it.subjectInfo.subjectId,
                                                it.subjectInfo.displayName,
                                                it.subjectCollectionInfo,
                                                true,
                                            )
                                        }
                                        focusedSection = TvExplorationSection.FOLLOWED
                                        focusedCardIndexInRow = index
                                        reportFocus()
                                    },
                                    modifier = Modifier.width(TV_PAGE_CARD_WIDTH)
                                        // 播放键: 短按直接进播放器续播 (分集信息未加载时退化为进详情),
                                        // 长按强制重拉本栏目 —— 它平时只跟着一小时一跳的定时同步走
                                        .then(followedPlayKeyModifier),
                                    menu = subject?.let { collectionMenuFor(it.subjectId) },
                                    // 下一集的播放进度 (语义同详情页选集卡): 看到一半按播放位置;
                                    // 已看完最新一集在等更新 (Watched) / 看完全部 (Done) 显示满条;
                                    // 看完上一集且有新集 / 还没开始看 (无播放记录) 不显示
                                    progress = item?.subjectCollectionInfo?.progressInfo?.let { progressInfo ->
                                        val caughtUp = progressInfo.continueWatchingStatus.let {
                                            it is ContinueWatchingStatus.Watched || it is ContinueWatchingStatus.Done
                                        }
                                        if (caughtUp) 1f
                                        else progressInfo.nextEpisodeIdToPlay
                                            ?.let { nextId -> playHistories.firstOrNull { it.episodeId == nextId } }
                                            ?.let { history ->
                                                val duration = history.durationMillis
                                                if (duration != null && duration > 0) {
                                                    (history.positionMillis.toFloat() / duration).coerceIn(0f, 1f)
                                                } else null
                                            }
                                    },
                                )
                            }
                        }
                    }

                    item(key = "rec-header") {
                        TvSectionHeader(
                            stringResource(Lang.exploration_recommendations),
                            transparent = focusedSection == TvExplorationSection.RECOMMENDATIONS,
                        )
                    }
                    // 推荐: 每行也是固定锚点轮播, 按固定行容量分块, 行数随分页无限增长 (纵向无限行)
                    val recRowCount =
                        (recommendations.itemCount + TV_EXPLORATION_REC_ROW_SIZE - 1) / TV_EXPLORATION_REC_ROW_SIZE
                    items(recRowCount, key = { rowIndex -> "rec-$rowIndex" }) { rowIndex ->
                        val rowStart = rowIndex * TV_EXPLORATION_REC_ROW_SIZE
                        val rowItemCount = minOf(TV_EXPLORATION_REC_ROW_SIZE, recommendations.itemCount - rowStart)
                        TvAnchoredCardRow(
                            itemCount = rowItemCount,
                            // 无继续观看时推荐首行是最顶行, 按上键回到 hero
                            onNavigateUp = if (!hasFollowed && rowIndex == 0) navigateUpToHero else null,
                            // 推荐行横向循环: 末卡右侧即首卡
                            loop = true,
                            focusTarget = focusTarget?.takeIf {
                                it.section == TvExplorationSection.RECOMMENDATIONS && it.row == rowIndex
                            },
                            onFocusTargetArrived = { focusTarget = null },
                        ) { localIndex, reportFocus ->
                            val item = recommendations[rowStart + localIndex] as? RecommendedSubjectInfo
                            TvPortraitCard(
                                imageUrl = item?.imageLarge,
                                contentDescription = item?.nameCn,
                                onClick = {
                                    item?.let {
                                        navigateToSubject(it.bangumiId, it.nameCn, it.imageLarge, "home_recommendation")
                                    }
                                },
                                onFocused = {
                                    item?.let { onFocusItem(it.bangumiId, it.nameCn, null, false) }
                                    focusedSection = TvExplorationSection.RECOMMENDATIONS
                                    recFocusedRow = rowIndex
                                    focusedCardIndexInRow = localIndex
                                    reportFocus()
                                },
                                modifier = Modifier.width(TV_PAGE_CARD_WIDTH)
                                    // 播放/暂停键: 直接进播放器 (无进度从第一集; 信息未加载退化为详情)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown &&
                                            (event.key == Key.MediaPlayPause || event.key == Key.MediaPlay)
                                        ) {
                                            item?.let {
                                                navigateToPlay(
                                                    it.bangumiId, it.nameCn, it.imageLarge,
                                                    "home_recommendation_play",
                                                )
                                                true
                                            } ?: false
                                        } else {
                                            false
                                        }
                                    },
                                menu = item?.let { collectionMenuFor(it.bangumiId) },
                            )
                        }
                    }
                }
            }
        }

        // 轮播指示器 (不可聚焦): 垂直位置钉在 hero backdrop 下边界 (底边压线, 可用
        // TV_CAROUSEL_INDICATOR_EDGE_RAISE 上抬微调), 内容区水平居中; 仅展开态显示.
        if (focusedSection == TvExplorationSection.TRENDING && carouselSize > 1) {
            Box(
                Modifier.fillMaxWidth()
                    .fillMaxHeight(TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION),
                contentAlignment = Alignment.BottomCenter,
            ) {
                TvCarouselIndicator(
                    count = carouselSize,
                    selectedIndex = carouselIndex.coerceIn(0, carouselSize - 1),
                    modifier = Modifier.padding(bottom = TV_CAROUSEL_INDICATOR_EDGE_RAISE),
                )
            }
        }

        // 底缘弱渐变遮罩 (页面背景色, smoothstep 采样无折点): 轻压被视口截断的下一行卡片,
        // 保证右下角提示在滚动的海报上仍可读. 只绘制, 不参与点击/焦点.
        run {
            val bg = MaterialTheme.colorScheme.background
            Box(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(TV_PAGE_BOTTOM_SCRIM_HEIGHT)
                    .background(
                        Brush.verticalGradient(
                            *Array(11) { i ->
                                val f = i / 10f
                                val ease = f * f * (3f - 2f * f)
                                f to bg.copy(alpha = ease * TV_PAGE_BOTTOM_SCRIM_MAX_ALPHA)
                            },
                        ),
                    ),
            )
        }

        // 右下角遥控键提示 (参考 Prime 的同位置提示): 次要色低调常显
        Row(
            Modifier.align(Alignment.BottomEnd)
                .padding(end = TV_PAGE_END_PAD, bottom = TV_PAGE_HINT_BOTTOM_PAD),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                Modifier.size(TV_PAGE_HINT_ICON_SIZE),
                tint = tvHeroSecondaryContentColor(),
            )
            Text(
                stringResource(Lang.tv_card_remote_hint),
                color = tvHeroSecondaryContentColor(),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/** 探索页卡片区的三个区块 (纵向吸顶滚动按此定位). */
private enum class TvExplorationSection {
    TRENDING,
    FOLLOWED,
    RECOMMENDATIONS,
}

/**
 * 固定锚点横向卡片行 (同选集轮播): 聚焦卡片始终吸附在行首, 按左右键时
 * 焦点视觉位置不动, 卡片列表整体滑过. 需在禁用 BringIntoView 的环境内使用 (滚动由本组件
 * 按聚焦下标显式驱动); 行尾留出整行空白让末卡也能吸附到行首.
 */
@Composable
private fun TvAnchoredCardRow(
    itemCount: Int,
    modifier: Modifier = Modifier,
    onNavigateUp: (() -> Boolean)? = null,
    loop: Boolean = false,
    /**
     * 页面级统一落点 (本行是目标行时非 null, 见 [TvExplorationFocusTarget]): 行内段解析 ——
     * 滚动让目标卡组合并聚焦, 卡片 reportFocus 确认到位后调 [onFocusTargetArrived]
     * (页面据此清空落点). cardIndex < 0 表示"本行自己跨导航保存的上次聚焦卡" (进页恢复用).
     */
    focusTarget: TvExplorationFocusTarget? = null,
    onFocusTargetArrived: () -> Unit = {},
    card: @Composable (index: Int, reportFocus: () -> Unit) -> Unit,
) {
    // focusedIndex 用 rememberSaveable 跨导航保存: 返回时据此恢复本行横向滚动与恢复焦点目标卡
    var focusedIndex by rememberSaveable { mutableIntStateOf(-1) }
    val listState = rememberLazyListState()
    // 行内落点解析 (统一落点的第二段, 第一段是页面滚动纵向列表让本行组合出来): 解析出目标卡
    // 下标 (该卡挂请求器) → 请求聚焦 → reportFocus 确认到位, 不到位滚动让目标组合出来再试.
    // 行滚出视口再滚回重组时, 落点仍挂着 (页面级单一状态) 则本效应自然重跑继续解析, 无需水位线.
    var resolvedTarget by remember { mutableIntStateOf(-1) }
    var targetArrived by remember { mutableStateOf(false) }
    // 实时聚焦的虚拟下标 (卡片包装 Box 的 onFocusChanged 双向维护). 到位判据不能只靠
    // targetArrived 事件闩: 目标卡在解析启动前已聚焦时, requestFocus 不产生任何焦点
    // 事件, 闩永远不置位 —— 轮询烧满 60 次, 期间用户移开的焦点每帧被抢回
    var liveFocusedIndex by remember { mutableIntStateOf(-1) }
    val targetRequester = remember { FocusRequester() }
    // 效应内经这两个引用读最新值: 分页数据 (itemCount) 在解析期间可能继续到达
    val currentItemCount by rememberUpdatedState(itemCount)
    val currentOnArrived by rememberUpdatedState(onFocusTargetArrived)
    LaunchedEffect(focusTarget) {
        if (focusTarget == null) {
            resolvedTarget = -1
            return@LaunchedEffect
        }
        targetArrived = false
        // 起点快照: 解析开始时焦点通常正停在"要离开的那张卡"上, 不能算用户介入
        val startFocusedIndex = liveFocusedIndex
        // attempts 60: 目标卡可能要等分页数据到达 + 滚动组合
        resolveFocusRepeatedly(
            attempts = 60,
            arrived = { targetArrived || (resolvedTarget >= 0 && liveFocusedIndex == resolvedTarget) },
            // 焦点跑到既非起点也非目标的卡上 = 用户自己按键移开了, 立即让路 (理由见 resolveFocusRepeatedly)
            abandon = {
                liveFocusedIndex >= 0 &&
                    liveFocusedIndex != startFocusedIndex &&
                    liveFocusedIndex != resolvedTarget
            },
        ) {
            val count = currentItemCount
            if (count > 0) {
                // 循环行的下标在虚拟空间 (可超出条目数); 非循环行夹到末卡
                val idx = when {
                    focusTarget.cardIndex >= 0 -> focusTarget.cardIndex
                    focusedIndex >= 0 -> focusedIndex
                    else -> 0
                }.let { if (loop && count > 1) it else it.coerceAtMost(count - 1) }
                resolvedTarget = idx
                runCatching { targetRequester.requestFocus() }
                if (!targetArrived) {
                    // 目标卡未组合 (聚焦失败): 滚过去让它组合出来再试
                    runCatching { listState.scrollToItem(idx) }
                }
            }
        }
        resolvedTarget = -1
        currentOnArrived() // 到位或放弃都通知页面清落点, 不留悬挂请求
    }
    // 动画器提到 effect 外: 速度跨段继承, 连按左右键行内滚动连续流动 (同页面级行滚动)
    val cardScrollAnimator = remember { TvScrollAnimator() }
    LaunchedEffect(focusedIndex) {
        if (focusedIndex >= 0) cardScrollAnimator.animateScrollToItem(listState, focusedIndex)
    }
    // 横向循环: 用虚拟"无限"列表, 卡片按 index % itemCount 取 —— 右移到末卡再右即回到首卡.
    // 起点在 index 0 (首卡在最左), 左移到首卡再左则离开本行 (交给焦点系统 -> 侧边栏).
    val loopEnabled = loop && itemCount > 1
    val virtualCount = if (loopEnabled) Int.MAX_VALUE else itemCount
    BoxWithConstraints(
        modifier.then(
            if (onNavigateUp != null) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                        onNavigateUp()
                    } else {
                        false
                    }
                }
            } else Modifier,
        ),
    ) {
        LazyRow(
            state = listState,
            horizontalArrangement = Arrangement.spacedBy(TV_PAGE_CARD_SPACING),
            contentPadding = PaddingValues(
                end = (this.maxWidth - TV_PAGE_CARD_WIDTH).coerceAtLeast(0.dp),
            ),
        ) {
            items(virtualCount) { index ->
                // derivedStateOf 收窄成布尔: 落点解析期间 resolvedTarget 变化只让目标卡
                // 自己 (挂/摘请求器) 重组
                val isTarget by remember(index) { derivedStateOf { resolvedTarget == index } }
                // 请求器挂容器上, requestFocus 委托给内部第一个焦点目标 (卡片)
                Box(
                    Modifier
                        .onFocusChanged {
                            if (it.hasFocus) {
                                liveFocusedIndex = index
                            } else if (liveFocusedIndex == index) {
                                liveFocusedIndex = -1
                            }
                        }
                        .ifThen(isTarget) { focusRequester(targetRequester) },
                ) {
                    card(
                        if (loopEnabled) index % itemCount else index,
                        {
                            focusedIndex = index
                            if (index == resolvedTarget) targetArrived = true
                        },
                    )
                }
            }
        }
    }
}

/**
 * 统一焦点落点请求 (回 hero / 返回回行首卡 / 回推荐首行 / 进页恢复焦点只是目标参数不同):
 * [section] = TRENDING 时聚焦 hero 立即观看按钮 (忽略其余字段); 否则聚焦 [section] 区块
 * 第 [row] 行 (仅推荐区有多行) 的第 [cardIndex] 张卡, cardIndex < 0 表示该行自己跨导航
 * 保存的上次聚焦卡. [seq] 使连续发出的同参请求也能重新触发解析.
 *
 * 解析分两段: 页面段把纵向列表滚到目标行让它组合 (hero 目标则轮询聚焦按钮 + 到位确认);
 * 行内段 (TvAnchoredCardRow) 滚动行内列表让目标卡组合并聚焦, reportFocus 确认到位后
 * 清空请求. 两段共享这一个页面级请求状态, 行重组/滚出滚回都不影响解析继续.
 */
private data class TvExplorationFocusTarget(
    val section: TvExplorationSection,
    val row: Int = 0,
    val cardIndex: Int = -1,
    val seq: Int = 0,
)

/** 聚焦卡片 → Hero 展示目标 (标题从卡片数据即时取得, 其余异步). */
private data class TvHeroTarget(
    val subjectId: Int,
    val title: String,
    /** 是否来自"继续观看"行: hero 背景优先展示下一集的 TMDB 单集剧照 (而非整部 backdrop). */
    val fromFollowed: Boolean = false,
)

/** 继续观看 hero 的下一集 TMDB 数据; [episodeId] 用于看完一集后 (下一集变化) 失效重查. */
private data class TvNextEpisodeMedia(
    val episodeId: Int,
    val stillUrl: String?,
    val overview: String?,
)

/**
 * 区块标题行. 只占文字自身高度 (标题到卡片行的距离统一由外部 [TV_SECTION_HEADER_TO_ROW_GAP] 控制,
 * 不再由固定盒高引入额外空白).
 * [transparent] 时文字不可见但仍占位: 聚焦区块自己的行内标题设为透明, 由顶部持久标题代替显示,
 * 避免"内容撑不满一屏无法滚动裁掉"时行内标题与持久标题重复; 透明保留高度, 切焦点无跳动.
 */
@Composable
private fun TvSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    transparent: Boolean = false,
) {
    Box(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            title,
            color = if (transparent) Color.Transparent else Color.Unspecified,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

/**
 * 区块标题到卡片行的间距. 同时控制两种情形:
 * - 聚焦该区块时: 持久标题下方到吸顶卡片行的间距 (Spacer);
 * - 未聚焦时: 行内标题与卡片行 (及卡片行之间) 的 LazyColumn 竖向间距.
 * 一个参数保证两种情形距离一致.
 */
private val TV_SECTION_HEADER_TO_ROW_GAP = 12.dp

/**
 * 轮播指示器 (横排小圆点, 不可聚焦, 纯展示). 当前项为拉长胶囊, 其余为小圆点. 由外部左右键驱动
 * ([selectedIndex]), 自身不处理焦点与按键. 少于 2 项时不显示. 在给定宽度内水平居中.
 */
@Composable
private fun TvCarouselIndicator(
    count: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { i ->
            val active = i == selectedIndex
            Box(
                Modifier
                    .height(6.dp)
                    .width(if (active) 20.dp else 6.dp)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        },
                        RoundedCornerShape(50),
                    ),
            )
        }
    }
}

/**
 * Hero 信息块固定高度 (标题 + 评分/连载行 + 简介). 简介用 weight 填满其下剩余空间 —— 调大此值
 * = 简介显示更多行; 固定高度保证不同条目切换 (简介长短不同) 时下方卡片区不跳动. 展开态 (焦点在 hero)
 * 时其下再叠加操作按钮块; 焦点移到卡片时按钮块消失, 卡片区顺势上移贴住信息块 (无大片空白).
 *
 * 标题已由至多两行改成定高一行 (长标题跑马灯滚动), 省下的一行自动归简介.
 */
private val TV_HERO_INFO_HEIGHT = 200.dp

/** Hero 信息块与操作按钮块之间的间距 (较短, 让按钮贴近简介). */
private val TV_HERO_INFO_TO_BUTTONS_GAP = 6.dp

/** 两枚操作按钮之间的间距 (很短). */
private val TV_HERO_BUTTON_GAP = 4.dp

/** 按钮块下方到继续观看栏的间距 (取代原 40dp 空标题, 让继续观看紧贴按钮). */
private val TV_HERO_BUTTONS_TO_CONTENT_GAP = 12.dp

/**
 * 卡片区聚焦时介绍块的高度 (= 介绍块上下边界距离; 顶部固定). 比展开态 [TV_HERO_INFO_HEIGHT] 高一些,
 * 文字底对齐使其下边界随之下移并紧贴卡片行, 从而露出更多 backdrop、卡片不再上移遮挡. 调大则更靠下.
 * 与展开态保持同样的 40dp 差值.
 */
private val TV_HERO_INFO_HEIGHT_COLLAPSED = 240.dp

/**
 * Hero "下一集"状态行的固定高度. 加上列间距 10dp 后恰为简介两行行距 (bodyMedium
 * lineHeight 20dp × 2), 使有无此行时简介行网格对齐 (见使用处).
 */
private val TV_HERO_STATUS_ROW_HEIGHT = 30.dp

/** 轮播指示器最多显示的圆点数 (同时也是自动轮播覆盖的条目数). */
private const val TV_CAROUSEL_MAX_DOTS = 20

/** 自动轮播切换间隔. */
private const val TV_CAROUSEL_AUTO_ADVANCE_MILLIS = 6000L

/** 轮播指示器相对 hero backdrop 下边界的上抬量 (0 = 指示器底边正好压在下边界上). */
private val TV_CAROUSEL_INDICATOR_EDGE_RAISE = 18.dp

/**
 * backdrop 下缘渐隐起点的轮播 (hero) 态档位 (图片高度坐标 0..1); 卡片聚焦态用三页
 * 共享的 [TV_BACKDROP_BOTTOM_FADE_START], 焦点移动时在两者间插值.
 */
private const val TV_EXPLORATION_BOTTOM_FADE_START_HERO = 0.88f

/**
 * 内容左侧额外留白 (外层 MainScreen 已让开侧边栏收起宽度 48dp, 总左缘 = 48 + 此值).
 * 默认 16 使总左缘 64, 侧边栏按钮中心 (32) 恰在屏幕左缘与内容左缘的正中间.
 */
private val TV_EXPLORATION_START_PAD = 16.dp

/** backdrop 高度占屏高比例 (Prime 实测: 图占屏顶约 76%, 渐变尾正好压在卡片区上缘之下). */
private const val TV_EXPLORATION_BACKDROP_HEIGHT_FRACTION = 0.66f

/** backdrop 两态渐变 (hero 态 <-> 卡片态) 切换动画时长. */
private const val TV_BACKDROP_STATE_ANIM_MILLIS = 400

/** 推荐区每行的条目数 (每行是一条固定锚点轮播, 行数随分页无限增长). */
private const val TV_EXPLORATION_REC_ROW_SIZE = 12


