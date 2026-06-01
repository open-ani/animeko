/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SubtitlesOff
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.danmaku.DanmakuEditorState
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.focus.restoreFocusAfter
import me.him188.ani.app.ui.foundation.icons.AniIcons
import me.him188.ani.app.ui.foundation.PlayerFrameHolder
import me.him188.ani.app.ui.foundation.icons.Forward80
import me.him188.ani.app.ui.foundation.icons.Forward85
import me.him188.ani.app.ui.foundation.icons.Forward90
import me.him188.ani.app.ui.foundation.icons.SubtitleGear
import me.him188.ani.app.ui.foundation.watchtogether.LocalWatchTogetherEntry
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.episode_comments
import me.him188.ani.app.ui.lang.subject_details_characters
import me.him188.ani.app.ui.lang.subject_details_staff
import me.him188.ani.app.ui.lang.subject_episode_cache
import me.him188.ani.app.ui.lang.subject_episode_danmaku_list_title
import me.him188.ani.app.ui.lang.subject_episode_danmaku_settings_title
import me.him188.ani.app.ui.lang.subject_episode_external_links
import me.him188.ani.app.ui.lang.subject_episode_fast_forward_seconds
import me.him188.ani.app.ui.lang.subject_episode_related_recommendations
import me.him188.ani.app.ui.lang.subject_episode_select_media_source
import me.him188.ani.app.ui.lang.video_player_disable_danmaku
import me.him188.ani.app.ui.lang.video_player_enable_danmaku
import me.him188.ani.app.ui.lang.video_player_next_episode
import me.him188.ani.app.ui.lang.video_player_stats_title_hide
import me.him188.ani.app.ui.lang.video_player_stats_title_show
import me.him188.ani.app.ui.lang.video_player_tv_collection
import me.him188.ani.app.ui.lang.video_player_tv_restart
import me.him188.ani.app.ui.lang.watch_together_title
import me.him188.ani.app.ui.mediaselect.common.SourceIcon
import me.him188.ani.app.ui.mediaselect.summary.MediaSelectorSummary
import me.him188.ani.app.ui.subject.collection.components.EditCollectionTypeDropDown
import me.him188.ani.app.ui.subject.collection.components.EditableSubjectCollectionTypeDialogsHost
import me.him188.ani.app.ui.subject.person.PeoplePreviewHost
import me.him188.ani.app.ui.subject.episode.EpisodePageState
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.app.ui.subject.episode.details.components.ShareEpisodeDropdown
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.videoplayer.ui.PlaybackSpeedControllerState
import me.him188.ani.app.videoplayer.ui.VideoAspectRatioControllerState
import me.him188.ani.app.videoplayer.ui.VideoSideSheetsController
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressFramePreviewState
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressSlider
import me.him188.ani.app.videoplayer.ui.progress.MediaProgressSliderDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.SpeedSwitcher
import me.him188.ani.app.videoplayer.ui.progress.PlayerControllerDefaults.VideoAspectRatioSelector
import me.him188.ani.app.videoplayer.ui.progress.PlayerProgressSliderState
import me.him188.ani.app.videoplayer.ui.progress.ProgressSliderPreviewStyle
import me.him188.ani.app.videoplayer.ui.progress.SubtitleSwitcher
import me.him188.ani.app.videoplayer.ui.top.SystemTime
import org.jetbrains.compose.resources.stringResource
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.features.VideoAspectRatio
import org.openani.mediamp.features.subtitleTracks
import kotlin.time.Duration

// ---- 布局调参 ----

/** 内容水平留白 (Prime 风格大留白). */
internal val TV_PLAYER_HORIZONTAL_PAD = 48.dp

/** 底部渐变 scrim 高度. */
private val TV_PLAYER_BOTTOM_SCRIM_HEIGHT = 380.dp

/** 底部渐变 scrim 最深处不透明度. */
private const val TV_PLAYER_BOTTOM_SCRIM_ALPHA = 0.95f

/** 顶部渐变 scrim 高度 (标题可读性). */
private val TV_PLAYER_TOP_SCRIM_HEIGHT = 180.dp

/** 顶部渐变 scrim 最深处不透明度. */
private const val TV_PLAYER_TOP_SCRIM_ALPHA = 0.7f

/** 顶部信息里数据源图标的尺寸. */
private val TV_PLAYER_SOURCE_ICON_SIZE = 18.dp

/**
 * 控制层 (L1) + 浮出面板 (L2).
 *
 * 布局 (自下而上): 图标行 / 进度条行 / 胶囊按钮行 / (聚焦胶囊时) 浮出面板.
 * 顶部: 左上标题 (Prime 风格) + 右上系统时钟.
 */
@Composable
internal fun TvPlayerControlsOverlay(
    overlay: TvPlayerOverlayState,
    vm: EpisodeViewModel,
    page: EpisodePageState,
    danmakuEditorState: DanmakuEditorState,
    progressSliderState: PlayerProgressSliderState,
    /** 拖拽预览的帧源 (小圆点上方缩略图); null = 设置里关掉了帧预览, 浮窗只剩时间. */
    framePreview: MediaProgressFramePreviewState?,
    progressRowFocusRequester: FocusRequester,
    bottomRowFocusRequester: FocusRequester,
    episodeStripFocusRequester: FocusRequester,
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage>,
    pauseOnPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        // 选集条展开态不整屏压暗: 内容全部贴底, 上下两条渐变 scrim 已足够托住可读性,
        // 画面中部保持通透
        // 底部渐变 scrim (托住控制行与面板)
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(TV_PLAYER_BOTTOM_SCRIM_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = TV_PLAYER_BOTTOM_SCRIM_ALPHA),
                    ),
                ),
        )
        // 顶部渐变 scrim (标题/时钟可读性)
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(TV_PLAYER_TOP_SCRIM_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = TV_PLAYER_TOP_SCRIM_ALPHA),
                        1f to Color.Transparent,
                    ),
                ),
        )

        // 面板向上生长的边界实测: 顶部信息 (标题/时钟) 的下缘与胶囊行的上缘.
        // 面板最大高度 = 两者间距 (见 TvPlayerPanelHost), 4K 一类大逻辑分辨率下不再锁死小窗.
        // 写的是 window 坐标原始 px; 只在面板的测量阶段读, 位置变化不触发本层重组
        val topInfoBottomPx = remember { mutableFloatStateOf(Float.NaN) }
        val pillsRowTopPx = remember { mutableFloatStateOf(Float.NaN) }

        // 顶部信息: 左上标题 + 右上时钟
        TvPlayerTopInfo(
            page,
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = TV_PLAYER_HORIZONTAL_PAD, vertical = 28.dp)
                // 放在 padding 之后: 量的是标题块本体的下缘 (可见元素边界), 不含留白
                .onGloballyPositioned { topInfoBottomPx.floatValue = it.boundsInWindow().bottom },
        )

        // 底部区域: 控制行 (胶囊/进度条/图标行) 与选集条二选一, 在 Box 里底对齐重叠
        // 淡切 —— 不能上下堆叠在 Column 里: 切换瞬间选集条先占位会把控制行整体上顶,
        // 观感是进度条向上闪动一下再消失.
        // 水平留白只加在控制行上, 选集条以全宽放置 (卡片行要出血画到屏幕右缘,
        // 停靠留边由轮播内部 contentPadding 提供).
        // 焦点在胶囊行/浮出面板时只露到进度条为止 (Prime 行为): 进度条以下的
        // 图标行暂隐, 焦点回到进度条/图标行再出现.
        // derivedStateOf: focusRegion 每次方向键都在变, 直接读会让整个覆盖层
        // (scrim/标题/胶囊/面板) 随每步导航重组; 收窄成布尔翻转才失效
        val hideBelowProgress by remember {
            derivedStateOf {
                overlay.focusRegion == TvPlayerFocusRegion.PILLS ||
                        overlay.focusRegion == TvPlayerFocusRegion.PANEL
            }
        }
        // 每个胶囊按钮的焦点请求器: 面板最底项按下键显式回到"打开它的那个胶囊" ——
        // 靠空间搜索会落到面板正下方的任意按钮, 落错后该按钮又把面板切成自己的 (卡片跳变)
        val pillFocusRequesters = remember { TvPlayerPanel.entries.associateWith { FocusRequester() } }
        // 进度条按下键的显式落点: 图标行最左按钮 (全宽进度条交给空间搜索会落到中间按钮)
        val bottomRowFirstFocus = remember { FocusRequester() }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(vertical = 20.dp),
        ) {
            AniAnimatedVisibility(
                visible = !overlay.episodeStripExpanded,
                modifier = Modifier.align(Alignment.BottomStart),
            ) {
                Column(Modifier.padding(horizontal = TV_PLAYER_HORIZONTAL_PAD)) {
                    // L2 浮出面板 (聚焦胶囊按钮时出现, 条目吸底, 从下往上导航).
                    // PeoplePreviewHost: 角色/制作人员面板点击卡片弹居中人物预览
                    // (与详情页同一弹窗), 开着期间抑制控制层自动隐藏
                    PeoplePreviewHost(
                        onPreviewOpenChanged = { open ->
                            overlay.onPopupExpandedChanged(open)
                            // 预览关掉后焦点还给刚点开的那张卡片: 弹窗节点被移除时 Compose 会清掉
                            // 整棵树的焦点 (不交给祖先), 不还的话面板还在但方向键全失效
                            if (!open) overlay.requestPanelItemFocus()
                        },
                    ) {
                        TvPlayerPanelHost(
                            overlay = overlay,
                            vm = vm,
                            page = page,
                            pauseOnPlaying = pauseOnPlaying,
                            pillFocusRequesters = pillFocusRequesters,
                            // NaN (尚未测得) 由面板侧兜底成固定高度
                            availableHeightPx = { pillsRowTopPx.floatValue - topInfoBottomPx.floatValue },
                        )
                    }

                    // 胶囊按钮行 (+ 弹幕发送展开框).
                    //
                    // 拖拽预览中按成隐形: 缩略图浮窗就浮在圆点上方, 正好压在这一行上,
                    // 两层叠着看不清. 只按 alpha 不移出组合 —— 节点还在, 上键的落点请求器
                    // 保持附着, 而按上键会让焦点离开进度条, 预览随即提交, 这一行同帧就回来了.
                    // 状态读在 graphicsLayer 的 lambda 里, 圆点每走一步只失效图层不重组整层
                    TvPlayerPillsRow(
                        overlay = overlay,
                        danmakuEditorState = danmakuEditorState,
                        vm = vm,
                        pillFocusRequesters = pillFocusRequesters,
                        modifier = Modifier
                            // 上缘位置 = 面板的下锚点 (面板在同一 Column 里紧贴本行之上)
                            .onGloballyPositioned { pillsRowTopPx.floatValue = it.boundsInWindow().top }
                            .padding(bottom = 18.dp)
                            .graphicsLayer { alpha = if (progressSliderState.isPreviewing) 0f else 1f },
                    )

                    // 进度条行
                    TvPlayerProgressRow(
                        vm = vm,
                        progressSliderState = progressSliderState,
                        framePreview = framePreview,
                        overlay = overlay,
                        focusRequester = progressRowFocusRequester,
                        // 图标行暂隐期间不能指向它 (节点已移出组合, 未附着的请求器会抛)
                        downFocus = bottomRowFirstFocus.takeIf { !hideBelowProgress },
                        upFocus = pillFocusRequesters.getValue(TV_PILL_VISUAL_ORDER.first()),
                    )

                    // 图标行 (原顶栏按钮并入; 再往下 = 选集条, 由根路由处理).
                    // 拖拽预览中同样按成隐形 (同上: 那会儿唯一该看的是圆点和缩略图)
                    AniAnimatedVisibility(visible = !hideBelowProgress) {
                        Column(
                            Modifier.graphicsLayer {
                                alpha = if (progressSliderState.isPreviewing) 0f else 1f
                            },
                        ) {
                            Spacer(Modifier.height(6.dp))
                            TvPlayerBottomRow(
                                overlay = overlay,
                                vm = vm,
                                page = page,
                                sheetsController = sheetsController,
                                focusRequester = bottomRowFocusRequester,
                                firstButtonFocus = bottomRowFirstFocus,
                                upFocus = progressRowFocusRequester,
                            )
                        }
                    }
                }
            }

            // 选集条: 仅展开态渲染 (无 peek), 图标行按下键唤出, 与控制行同位淡切
            TvPlayerEpisodeStrip(
                vm = vm,
                overlay = overlay,
                stripFocusRequester = episodeStripFocusRequester,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
            )
        }
    }
}

/** 顶部信息: 左上大标题 + 集号副标题 (Prime 风格), 右上系统时钟. */
@Composable
private fun TvPlayerTopInfo(
    page: EpisodePageState,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Top) {
        val episode = page.episodePresentation
        val subject = page.subjectPresentation
        Column(
            Modifier
                .weight(1f)
                .placeholder(episode.isPlaceholder || subject.isPlaceholder),
        ) {
            Text(
                subject.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "第 ${episode.ep} 集  ${episode.title}",
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // 当前数据源 (纯展示, 不进焦点树): 只为看清正在用哪个源, 换源入口仍是
            // 图标行的"选择数据源". 未选出结果时 (自动选择中/需手动) 整行不显示
            val summary = page.mediaSelectorSummary
            if (summary is MediaSelectorSummary.Selected) {
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (summary.source.sourceIconUrl.isNotEmpty()) {
                        SourceIcon(
                            iconUrl = summary.source.sourceIconUrl,
                            sourceName = summary.source.sourceName,
                            Modifier.size(TV_PLAYER_SOURCE_ICON_SIZE),
                        )
                    }
                    Text(
                        summary.source.sourceName,
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.size(24.dp))
        SystemTime()
    }
}

/**
 * 胶囊按钮行的视觉顺序 (左→右), 与 [TvPlayerPillsRow] 的排列保持一致:
 * 面板条目上按左/右键路由到相邻胶囊 (见 [TvPlayerPanelHost]) 的依据.
 */
internal val TV_PILL_VISUAL_ORDER = listOf(
    TvPlayerPanel.RECOMMENDATIONS,
    TvPlayerPanel.STAFF,
    TvPlayerPanel.CHARACTERS,
    TvPlayerPanel.COMMENTS,
    TvPlayerPanel.DANMAKU_LIST,
)

/** 胶囊按钮行: 相关推荐 / 制作人员 / 角色 / 评论 / 弹幕列表 (+ 弹幕发送展开框). */
@Composable
private fun TvPlayerPillsRow(
    overlay: TvPlayerOverlayState,
    danmakuEditorState: DanmakuEditorState,
    vm: EpisodeViewModel,
    pillFocusRequesters: Map<TvPlayerPanel, FocusRequester>,
    modifier: Modifier = Modifier,
) {
    // "一起看"胶囊的显隐在本行 (而不是胶囊自己) 判断: 用户可以在弹窗的 ⋮ 里关掉整个功能,
    // 那一下按钮连同它自己的焦点善后逻辑一起被移除, 只有留在场上的父级能接手 —— 把焦点送回
    // 进度条 (与从面板按返回同一个落点). 没有这一手就是按钮消失 + 焦点消失, 方向键全失效.
    val watchTogetherEnabled = LocalWatchTogetherEntry.current.enabled
    var watchTogetherWasEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(watchTogetherEnabled) {
        if (watchTogetherEnabled) {
            watchTogetherWasEnabled = true
            return@LaunchedEffect
        }
        // 一开始就没开 (或控制层刚组合出来) 不算"刚被关掉", 不能抢焦点
        if (!watchTogetherWasEnabled) return@LaunchedEffect
        watchTogetherWasEnabled = false
        overlay.focusProgress()
    }

    Row(
        modifier.onFocusChanged { if (it.hasFocus) overlay.focusRegion = TvPlayerFocusRegion.PILLS },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPlayerPill(
            icon = { Icon(Icons.Rounded.VideoLibrary, null, Modifier.size(TV_PILL_ICON_SIZE)) },
            label = stringResource(Lang.subject_episode_related_recommendations),
            panel = TvPlayerPanel.RECOMMENDATIONS,
            overlay = overlay,
            focusRequester = pillFocusRequesters.getValue(TvPlayerPanel.RECOMMENDATIONS),
        )
        TvPlayerPill(
            icon = { Icon(Icons.Rounded.Groups, null, Modifier.size(TV_PILL_ICON_SIZE)) },
            label = stringResource(Lang.subject_details_staff),
            panel = TvPlayerPanel.STAFF,
            overlay = overlay,
            focusRequester = pillFocusRequesters.getValue(TvPlayerPanel.STAFF),
        )
        TvPlayerPill(
            icon = { Icon(Icons.Rounded.Face, null, Modifier.size(TV_PILL_ICON_SIZE)) },
            label = stringResource(Lang.subject_details_characters),
            panel = TvPlayerPanel.CHARACTERS,
            overlay = overlay,
            focusRequester = pillFocusRequesters.getValue(TvPlayerPanel.CHARACTERS),
        )
        TvPlayerPill(
            icon = { Icon(Icons.AutoMirrored.Rounded.Comment, null, Modifier.size(TV_PILL_ICON_SIZE)) },
            label = stringResource(Lang.episode_comments),
            panel = TvPlayerPanel.COMMENTS,
            overlay = overlay,
            focusRequester = pillFocusRequesters.getValue(TvPlayerPanel.COMMENTS),
        )
        TvPlayerPill(
            icon = { Icon(Icons.AutoMirrored.Rounded.FormatListBulleted, null, Modifier.size(TV_PILL_ICON_SIZE)) },
            label = stringResource(Lang.subject_episode_danmaku_list_title),
            panel = TvPlayerPanel.DANMAKU_LIST,
            overlay = overlay,
            focusRequester = pillFocusRequesters.getValue(TvPlayerPanel.DANMAKU_LIST),
        )
        TvDanmakuSendEntry(
            overlay = overlay,
            danmakuEditorState = danmakuEditorState,
            vm = vm,
        )
        if (watchTogetherEnabled) TvWatchTogetherPill(overlay)
    }
}

/**
 * 胶囊行末尾的"一起看"入口 (承担遥控器上没有的悬浮气泡的作用). 弹窗本体挂在应用根部,
 * 这里只负责开与善后.
 *
 * 显隐由 [TvPlayerPillsRow] 判断 —— 功能被关掉时本组合整个消失, 焦点善后只能由父级做.
 */
@Composable
private fun TvWatchTogetherPill(
    overlay: TvPlayerOverlayState,
    modifier: Modifier = Modifier,
) {
    val entry = LocalWatchTogetherEntry.current
    val dialogVisible = entry.dialogVisible
    // 弹窗是独立窗口, 根部那个唯一按键路由收不到它的按键 -> interactionTick 不再自增,
    // 五秒后控制层连同本按钮一起被自动隐藏吃掉. 按下拉弹层同一套引用计数上报住.
    DisposableEffect(dialogVisible) {
        if (dialogVisible) overlay.onPopupExpandedChanged(true)
        onDispose { if (dialogVisible) overlay.onPopupExpandedChanged(false) }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = { entry.open(overDarkBackground = true) },
        modifier = modifier
            // 关掉后焦点还给本按钮: 弹窗是独立窗口, 关闭时主窗口未必把焦点还到原处,
            // 不还的话控制层还在但方向键全失效.
            // 控制层已经收起时放弃: 那时焦点归属由根路由的解析器负责, 再抢就是打架
            .restoreFocusAfter(
                dialogVisible,
                abandon = { overlay.layer != TvPlayerLayer.CONTROLS },
            )
            .onFocusChanged {
                // 与弹幕发送圆钮同理: 本按钮不是面板触发器, 聚焦到它时收起浮出的面板
                if (it.hasFocus) overlay.activePanel = null
            },
        shape = CircleShape,
        color = if (focused) Color.White else Color.White.copy(alpha = 0.14f),
        contentColor = if (focused) Color.Black else Color.White,
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.padding(horizontal = TV_PILL_PADDING_H, vertical = TV_PILL_PADDING_V),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.SyncAlt, null, Modifier.size(TV_PILL_ICON_SIZE))
            Text(
                stringResource(Lang.watch_together_title),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/** 单个胶囊按钮: 聚焦白底黑字 (Prime 样式), 同时浮出对应面板. */
@Composable
private fun TvPlayerPill(
    icon: @Composable () -> Unit,
    label: String,
    panel: TvPlayerPanel,
    overlay: TvPlayerOverlayState,
    /** 面板最底项按下键经此回到本按钮 (空间搜索会落错按钮导致面板跳变). */
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        // 点击 = 把焦点送进面板 (与上键一致); 由面板挂载后的入口请求器解析
        onClick = { overlay.requestPanelFocus() },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) overlay.activePanel = panel },
        shape = CircleShape,
        color = if (focused) Color.White else Color.White.copy(alpha = 0.14f),
        contentColor = if (focused) Color.Black else Color.White,
        interactionSource = interactionSource,
    ) {
        Row(
            Modifier.padding(horizontal = TV_PILL_PADDING_H, vertical = TV_PILL_PADDING_V),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon()
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

// ---- 控件尺寸 (Prime 密度: 初版的 80%) ----
internal val TV_PILL_ICON_SIZE = 14.dp
internal val TV_PILL_PADDING_H = 14.dp
internal val TV_PILL_PADDING_V = 8.dp
private val TV_ICON_BUTTON_SIZE = 38.dp
private val TV_ICON_SIZE = 20.dp

/** 图形几乎占满视口的图标 (如 Replay) 的补偿尺寸: 与留白多的图标视觉等大. */
private val TV_ICON_SIZE_VISUAL_COMPENSATED = 18.dp

/**
 * 进度条行: 左当前时间 + 中间进度条 + 右总时长; 整行是一个焦点节点 (左右键由根路由处理),
 * 示焦即进度圆点本身 —— 未聚焦时圆点隐藏, 聚焦时出现 (无光环, 不给整行加底色).
 *
 * 拖拽预览态 (根路由的 scrubStep) 下圆点脱离播放位置, 上方浮出缩略图 + 目标时间:
 * 那个浮窗是 [MediaProgressSlider] 里 `showPreviewTimeTextOnThumb` 那条分支画的,
 * 它锚在圆点上, 本来就是给"程序驱动的 detached slider"准备的, TV 这边只要把开关打开.
 */
@Composable
private fun TvPlayerProgressRow(
    vm: EpisodeViewModel,
    progressSliderState: PlayerProgressSliderState,
    framePreview: MediaProgressFramePreviewState?,
    overlay: TvPlayerOverlayState,
    focusRequester: FocusRequester,
    /** 下键的显式落点 (图标行最左按钮): 整行全宽, 交给空间搜索会落到行中间的按钮. */
    downFocus: FocusRequester?,
    /** 上键的显式落点 (胶囊行最左按钮): 同 [downFocus], 空间搜索会落到行中间的胶囊上. */
    upFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Row(
        modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties {
                if (downFocus != null) down = downFocus
                up = upFocus
            }
            .onFocusChanged { if (it.isFocused) overlay.focusRegion = TvPlayerFocusRegion.PROGRESS }
            .focusable(interactionSource = interactionSource)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // 拖拽预览中这里仍是**播放位置** (Prime 实测): 目标时间由圆点上方的浮窗给出,
            // 两处都显示目标就没人告诉用户"原来播到哪儿了", 返回取消后也失去了参照
            renderTvPlayerTime(progressSliderState.currentPositionMillis),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            val cacheProgressInfo by vm.cacheProgressInfoFlow.collectAsStateWithLifecycle(null)
            MediaProgressSlider(
                progressSliderState,
                { cacheProgressInfo },
                colors = MediaProgressSliderDefaults.colors(
                    trackProgressColor = Color.White,
                    // 圆点即示焦: 未聚焦隐藏, 聚焦出现
                    thumbColor = if (focused) Color.White else Color.Transparent,
                    trackBackgroundColor = Color.White.copy(alpha = 0.3f),
                ),
                enabled = false, // 展示用; 快进退走遥控器左右键 (根路由)
                // 拖拽预览态时在圆点上方浮出"缩略图 + 目标时间"
                showPreviewTimeTextOnThumb = true,
                framePreview = framePreview,
                // 只显示画面, 时间叠在画面底部居中 (Prime 风格): 卡片样式那一圈底色 + 帧下方
                // 另占一行的文字, 在电视上比画面本身还显眼
                previewStyle = ProgressSliderPreviewStyle.FrameOnly,
            )
        }
        Text(
            renderTvPlayerTime(progressSliderState.totalDurationMillis),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private fun renderTvPlayerTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
}

/**
 * 图标行: 播放组 (从头开始/下一集/跳OP) | 数据源 | 弹幕组 (开关/设置) ... 右侧文字选项组与低频组.
 * 再往下键 = 详情页 (根路由). 播放/暂停走遥控器确认键, 不再放按钮 (Prime 布局);
 * 选集走详情页覆盖层. 内容统一纯白高对比 (默认主题色在视频上看不清).
 */
@Composable
private fun TvPlayerBottomRow(
    overlay: TvPlayerOverlayState,
    vm: EpisodeViewModel,
    page: EpisodePageState,
    sheetsController: VideoSideSheetsController<EpisodeVideoSideSheetPage>,
    /** 选集条收起后把焦点还给本行 (focusRestorer 恢复到离开前聚焦的按钮). */
    focusRequester: FocusRequester,
    /** 最左按钮 (从头开始) 的请求器: 进度条按下键的固定落点. */
    firstButtonFocus: FocusRequester,
    /** 行内所有按钮按上键的显式落点 (进度条行): 空间搜索会越过细进度条落到胶囊按钮上. */
    upFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        LocalTextStyle provides MaterialTheme.typography.labelMedium,
    ) {
        Row(
            modifier
                .onFocusChanged { if (it.hasFocus) overlay.focusRegion = TvPlayerFocusRegion.BOTTOM_ROW }
                .focusRequester(focusRequester)
                .focusRestorer()
                .focusGroup()
                // 行内全部按钮的上键落点. 必须挂在 focusGroup 之后 (内侧):
                // 子节点向上收集焦点属性时遇到第一个焦点目标 (组节点) 即停,
                // 挂在组外侧只会作用于组自身, 按钮读不到
                .focusProperties { up = upFocus },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 从头开始 (Prime 同款). Replay 的图形几乎占满 24dp 视口 (环形箭头画到边),
            // 而 SkipNext 等图形留白多, 同尺寸下视觉显大 —— 缩一档做视觉等大
            TvBottomRowIcon(
                icon = Icons.Rounded.Replay,
                contentDescription = stringResource(Lang.video_player_tv_restart),
                onClick = { vm.player.seekTo(0) },
                modifier = Modifier.focusRequester(firstButtonFocus),
                iconSize = TV_ICON_SIZE_VISUAL_COMPENSATED,
            )
            if (vm.episodeSelectorState.hasNextEpisode) {
                TvBottomRowIcon(
                    icon = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(Lang.video_player_next_episode),
                    onClick = { vm.episodeSelectorState.selectNext() },
                )
            }
            // 跳过 OP/ED (快进配置的时长)
            TvSkipOpEdButton(vm)

            TvBottomRowDivider()

            // ---- 数据源 ---- (紧跟播放组: 卡顿/字幕不对时换源是看片途中最常走的一步,
            // 排在弹幕组之后要多按两下)
            TvBottomRowIcon(
                icon = Icons.Rounded.DisplaySettings,
                contentDescription = stringResource(Lang.subject_episode_select_media_source),
                onClick = { sheetsController.navigateTo(EpisodeVideoSideSheetPage.MEDIA_SELECTOR) },
            )

            TvBottomRowDivider()

            // ---- 弹幕组 ----
            TvBottomRowIcon(
                icon = if (page.danmakuEnabled) Icons.Rounded.Subtitles else Icons.Rounded.SubtitlesOff,
                contentDescription = stringResource(
                    if (page.danmakuEnabled) Lang.video_player_disable_danmaku else Lang.video_player_enable_danmaku,
                ),
                onClick = { vm.setDanmakuEnabled(!page.danmakuEnabled) },
            )
            TvBottomRowIcon(
                icon = AniIcons.SubtitleGear,
                contentDescription = stringResource(Lang.subject_episode_danmaku_settings_title),
                onClick = { sheetsController.navigateTo(EpisodeVideoSideSheetPage.PLAYER_SETTINGS) },
            )

            // 左右两块之间的弹性留白 (常用组靠左, 其余靠右)
            Spacer(Modifier.weight(1f))

            // ---- 文字选项组 (字幕轨/倍速/画面比例, 自描述文字按钮, 标签槽位仅为行内对齐) ----
            vm.player.subtitleTracks?.let {
                TvBottomRowLabeled(label = null) {
                    TvTextButtonInverse {
                        PlayerControllerDefaults.SubtitleSwitcher(
                            it,
                            modifier = Modifier.height(TV_ICON_BUTTON_SIZE),
                            onExpandedChanged = { open -> overlay.onPopupExpandedChanged(open) },
                        )
                    }
                }
            }
            // 倍速 / 画面比例 (下拉展开时上报, 抑制自动隐藏)
            val speedController = remember(vm) {
                vm.player.features[PlaybackSpeed]?.let { PlaybackSpeedControllerState(it, scope = scope) }
            }
            speedController?.let {
                TvBottomRowLabeled(label = null) {
                    TvTextButtonInverse {
                        SpeedSwitcher(
                            it,
                            modifier = Modifier.height(TV_ICON_BUTTON_SIZE),
                            onExpandedChanged = { open -> overlay.onPopupExpandedChanged(open) },
                        )
                    }
                }
            }
            val aspectController = remember(vm) {
                vm.player.features[VideoAspectRatio]?.let { VideoAspectRatioControllerState(it, scope = scope) }
            }
            aspectController?.let {
                TvBottomRowLabeled(label = null) {
                    TvTextButtonInverse {
                        VideoAspectRatioSelector(
                            it,
                            modifier = Modifier.height(TV_ICON_BUTTON_SIZE),
                            onExpandedChanged = { open -> overlay.onPopupExpandedChanged(open) },
                        )
                    }
                }
            }

            TvBottomRowDivider()

            // ---- 低频操作组 (收藏 + 原三个点菜单的三项) ----
            TvPlayerCollectionButton(vm, overlay)
            // 播放器统计开关
            TvBottomRowIcon(
                icon = Icons.Outlined.Analytics,
                contentDescription = stringResource(
                    if (overlay.showPlayerStats) Lang.video_player_stats_title_hide
                    else Lang.video_player_stats_title_show,
                ),
                onClick = { overlay.showPlayerStats = !overlay.showPlayerStats },
            )
            // 外部链接 (点击弹分享下拉)
            TvPlayerShareButton(overlay, page.shareData)
            // 缓存
            TvBottomRowIcon(
                icon = Icons.Rounded.Download,
                contentDescription = stringResource(Lang.subject_episode_cache),
                onClick = {
                    // 先捕获当前画面再跳转 (离开播放器时播放自动暂停, 该帧即缓存页的暂停画面背景)
                    scope.launch {
                        PlayerFrameHolder.put(captureTvPlayerFrame(vm.player))
                        navigator.navigateSubjectCaches(vm.subjectId)
                    }
                },
            )
        }
    }
}

/** 图标行分组隔栏: 竖细线, 高度与图标视觉对齐 (含底部标签槽位占位, 与按钮列同构). */
@Composable
private fun TvBottomRowDivider(modifier: Modifier = Modifier) {
    TvBottomRowLabeled(label = null, modifier.padding(horizontal = 6.dp)) {
        Box(
            Modifier.height(TV_ICON_BUTTON_SIZE).width(1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(TV_BOTTOM_ROW_DIVIDER_HEIGHT)
                    .background(Color.White.copy(alpha = TV_BOTTOM_ROW_DIVIDER_ALPHA)),
            )
        }
    }
}

/** 图标行分组隔栏的可见高度. */
private val TV_BOTTOM_ROW_DIVIDER_HEIGHT = 16.dp

/** 图标行分组隔栏的不透明度. */
private const val TV_BOTTOM_ROW_DIVIDER_ALPHA = 0.35f

/**
 * 收藏按钮: 图标反映当前收藏状态 (实心/空心), 点击弹收藏状态下拉
 * ([EditCollectionTypeDropDown] 的 state 重载自带开合与错误 toast);
 * 设为"看过"且有未看剧集时的确认对话框由 [EditableSubjectCollectionTypeDialogsHost] 承担.
 */
@Composable
private fun TvPlayerCollectionButton(
    vm: EpisodeViewModel,
    overlay: TvPlayerOverlayState,
    modifier: Modifier = Modifier,
) {
    val state = vm.editableSubjectCollectionTypeState
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    // 下拉/"看过"确认对话框打开期间上报弹窗计数, 抑制自动隐藏 —— 两者都是独立窗口,
    // 按键不重置计时, 不上报的话 5 秒后控制层连同对话框被 hideAll 连根卸载.
    // DisposableEffect: 本按钮意外随控制层卸载时计数如数归还 (LaunchedEffect 水位
    // 写法在打开状态下被取消会漏一次 -1, 自动隐藏从此永久失效)
    if (state.showDropdown || presentation.showSetAllEpisodesDoneDialog) {
        DisposableEffect(Unit) {
            overlay.onPopupExpandedChanged(true)
            onDispose { overlay.onPopupExpandedChanged(false) }
        }
    }
    EditableSubjectCollectionTypeDialogsHost(state)
    Box(modifier) {
        TvBottomRowIcon(
            icon = if (presentation.selfCollectionType == UnifiedCollectionType.NOT_COLLECTED) {
                Icons.Rounded.FavoriteBorder
            } else {
                Icons.Rounded.Favorite
            },
            contentDescription = stringResource(Lang.video_player_tv_collection),
            onClick = { state.showDropdown = true },
        )
        EditCollectionTypeDropDown(state)
    }
}

/** 图标行按钮下方的聚焦标签槽位高度 (常驻预留, 聚焦才显示文字 —— 布局不随聚焦跳动). */
private val TV_BOTTOM_ROW_LABEL_HEIGHT = 18.dp

/**
 * 图标行条目的聚焦标签: 按钮下方固定高度的槽位, 子树聚焦时浮现功能文字.
 * 文字按无界宽度测量并居中 (可比按钮宽, 向两侧出画), 不改变行内布局;
 * [label] 传 null 只预留槽位不显示文字 (字幕/倍速等自描述的文字按钮, 仅为行内对齐).
 */
@Composable
private fun TvBottomRowLabeled(
    label: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier.onFocusChanged { focused = it.hasFocus },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        content()
        // 槽位零宽 (fillMaxWidth 会在 Row 里撑满整行, 把其余按钮挤出屏幕):
        // 列宽 = 按钮宽; 文字按无界宽度测量, 围绕零宽槽位居中, 向两侧出画
        Box(
            Modifier.height(TV_BOTTOM_ROW_LABEL_HEIGHT).width(0.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (focused && label != null) {
                Text(
                    label,
                    Modifier.wrapContentWidth(align = Alignment.CenterHorizontally, unbounded = true),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/**
 * 文字按钮 (字幕/倍速/画面比例) 的聚焦反色容器: 子按钮聚焦时白底黑字
 * (与胶囊/图标按钮同款示焦; TextButton 文字色取 LocalContentColor, 直接换供给即可).
 */
@Composable
private fun TvTextButtonInverse(content: @Composable () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { focused = it.hasFocus }
            .background(if (focused) Color.White else Color.Transparent, CircleShape),
    ) {
        CompositionLocalProvider(
            LocalContentColor provides if (focused) Color.Black else Color.White,
        ) {
            content()
        }
    }
}

/**
 * 图标行圆钮容器: 聚焦时白底黑图标 (与胶囊按钮同款反色示焦), 未聚焦透明白图标.
 */
@Composable
private fun TvBottomRowIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier.size(TV_ICON_BUTTON_SIZE),
        shape = CircleShape,
        color = if (focused) Color.White else Color.Transparent,
        contentColor = if (focused) Color.Black else Color.White,
        interactionSource = interactionSource,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            content()
        }
    }
}

/** 图标行标准按钮 (80% 密度: 38dp 按钮 / 20dp 图标); 聚焦时反色 + 按钮下方浮现功能文字. */
@Composable
private fun TvBottomRowIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 图形占满视口的图标传 [TV_ICON_SIZE_VISUAL_COMPENSATED] 做视觉等大. */
    iconSize: Dp = TV_ICON_SIZE,
) {
    TvBottomRowLabeled(label = contentDescription, modifier) {
        TvBottomRowIconButton(onClick) {
            Icon(icon, contentDescription, Modifier.size(iconSize))
        }
    }
}

@Composable
private fun TvSkipOpEdButton(vm: EpisodeViewModel, modifier: Modifier = Modifier) {
    val duration: Duration = vm.videoScaffoldConfig.opEdSkipDuration
    val seconds = duration.inWholeSeconds
    val label = stringResource(Lang.subject_episode_fast_forward_seconds, seconds)
    TvBottomRowLabeled(label = label, modifier) {
        TvBottomRowIconButton({ vm.onClickSkipOpEd(vm.player.getCurrentPositionMillis()) }) {
            val icon = when (seconds) {
                85L -> AniIcons.Forward85
                90L -> AniIcons.Forward90
                else -> AniIcons.Forward80
            }
            Icon(icon, label, Modifier.size(TV_ICON_SIZE))
        }
    }
}

/** 外部链接按钮 (原三个点菜单项): 点击在按钮下方弹分享下拉 (数据源原链接等). */
@Composable
private fun TvPlayerShareButton(
    overlay: TvPlayerOverlayState,
    shareData: MediaShareData,
    modifier: Modifier = Modifier,
) {
    var showShareDropdown by rememberSaveable { mutableStateOf(false) }
    // 弹层打开时上报, 抑制自动隐藏 (弹层是独立窗口, 按键不会重置计时)
    if (showShareDropdown) {
        DisposableEffect(Unit) {
            overlay.onPopupExpandedChanged(true)
            onDispose { overlay.onPopupExpandedChanged(false) }
        }
    }
    Box(modifier) {
        TvBottomRowIcon(
            icon = Icons.AutoMirrored.Rounded.OpenInNew,
            contentDescription = stringResource(Lang.subject_episode_external_links),
            onClick = { showShareDropdown = true },
        )
        ShareEpisodeDropdown(
            shareData,
            showShareDropdown,
            onDismissRequest = { showShareDropdown = false },
        )
    }
}
