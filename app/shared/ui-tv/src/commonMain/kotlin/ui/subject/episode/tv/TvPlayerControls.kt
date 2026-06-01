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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DisplaySettings
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FastForward
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.danmaku.DanmakuEditorState
import me.him188.ani.app.ui.episode.share.MediaShareData
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.animation.StandardAccelerateEasing
import me.him188.ani.app.ui.foundation.animation.StandardDecelerateEasing
import me.him188.ani.app.ui.foundation.theme.EasingDurations
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
import me.him188.ani.app.ui.lang.video_player_tv_cancel_skip_segment
import me.him188.ani.app.ui.lang.video_player_tv_collection
import me.him188.ani.app.ui.lang.video_player_tv_restart
import me.him188.ani.app.ui.lang.video_player_tv_skip_segment
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
import me.him188.ani.app.ui.subject.episode.video.SkipOpEdKind
import me.him188.ani.app.ui.subject.episode.video.SkipOpEdTip
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

/**
 * 底部渐变 scrim 最深处不透明度.
 *
 * 只要托住白字可读即可, 不必压到近黑: 0.95 时屏幕下半条几乎是纯黑, 画面被切掉一块
 * (进度条一出来尤其明显). 控件都是白字/白图标, 0.72 下最亮的画面也只剩三成亮度, 对比够了.
 */
private const val TV_PLAYER_BOTTOM_SCRIM_ALPHA = 0.72f

/** 顶部渐变 scrim 高度 (标题可读性). */
private val TV_PLAYER_TOP_SCRIM_HEIGHT = 180.dp

/** 顶部渐变 scrim 最深处不透明度 (同 [TV_PLAYER_BOTTOM_SCRIM_ALPHA] 的取舍, 顶部只有标题与时钟). */
private const val TV_PLAYER_TOP_SCRIM_ALPHA = 0.5f

/** 顶部信息里数据源图标的尺寸. */
private val TV_PLAYER_SOURCE_ICON_SIZE = 18.dp

/**
 * 进度条行与其上下两行 (胶囊行 / 图标行) 的间距, 上下必须用同一个值 —— 原本是上 18dp 下 6dp,
 * 进度条明显偏下, 观感是三行没对齐.
 *
 * **屏幕上看到的空白比这个值大**: 进度条控件本体高 24dp 而可见轨道只有居中的 6dp, 上下各还有
 * 9dp 是控件内部的留白 (对称, 所以本常量一致 = 看到的空白一致). 也就是说实际观感 ≈ 本值 + 9dp,
 * 这一档已经贴得相当紧了; 进度条行本身那点 `padding(vertical)` 已经去掉, 别再加回来.
 */
private val TV_PLAYER_PROGRESS_ROW_GAP = 4.dp

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
    /**
     * 控制层本体可见吗. false = 本层只是**为了托住 [pillsRowTrailing] 那颗按钮而留在场上**:
     * 除那颗按钮外一切淡出到透明, 但**布局照旧**, 于是按钮稳稳停在胶囊行原来的位置上.
     *
     * 为什么不让按钮自己浮在屏幕上按实测坐标跟随: 试过, 位置得等下一帧才到, 图标行收起那段
     * 逐帧动画里按钮总慢一拍. 当成行内一员由布局直接给出位置, 就没有"跟随"这回事了.
     */
    chromeVisible: Boolean,
    /** 胶囊行最右的插槽 (OP/ED 提示按钮): 与胶囊同排靠右, 位置由布局给出. */
    pillsRowTrailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 控制层本体的淡入淡出. 整层的显隐原本由外面的 AniAnimatedVisibility 做, 现在本层要为提示
    // 按钮多活一会儿, 淡出就得挪到里面来 —— 只作用于"除那颗按钮之外"的部分, 时长与那边的
    // fadeIn/fadeOut 取同一档 (EasingDurations), 观感不变.
    //
    // **不能用 `by` 解构**: 那是在组合里读, 淡入淡出的每一帧都会重组整个控制层.
    // 留着 State 本体, 在 graphicsLayer 的 lambda 里读 —— 每帧只失效图层 (见本文件的重组纪律)
    val chromeAlpha = animateFloatAsState(
        targetValue = if (chromeVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (chromeVisible) {
                EasingDurations.standardDecelerate
            } else {
                EasingDurations.standardAccelerate
            },
            easing = if (chromeVisible) StandardDecelerateEasing else StandardAccelerateEasing,
        ),
        label = "TvPlayerControlsChrome",
    )
    val chrome = Modifier.graphicsLayer { alpha = chromeAlpha.value }
    Box(modifier) {
        // 选集条展开态不整屏压暗: 内容全部贴底, 上下两条渐变 scrim 已足够托住可读性,
        // 画面中部保持通透
        // 底部渐变 scrim (托住控制行与面板)
        Box(
            chrome
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
            chrome
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
            chrome
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
                // 控制层不可见时无条件留着这一列: 它此刻的唯一职责是托住那颗提示按钮,
                // 而选集条的展开态在 hideAll 里并不复位
                visible = !overlay.episodeStripExpanded || !chromeVisible,
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
                            modifier = chrome,
                            overlay = overlay,
                            vm = vm,
                            page = page,
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
                        onNewComment = { openNewEpisodeComment(vm, page, overlay) },
                        trailing = pillsRowTrailing,
                        // 胶囊本体跟着控制层淡出, 末尾那颗提示按钮不跟 (它有自己的显示时长)
                        pillsModifier = chrome,
                        modifier = Modifier
                            .padding(bottom = TV_PLAYER_PROGRESS_ROW_GAP)
                            // 放在 padding 之后: 量的是胶囊本体的上缘 (可见元素边界), 不含那段间距.
                            // 这是面板的下锚点 (面板在同一 Column 里紧贴本行之上)
                            .onGloballyPositioned { pillsRowTopPx.floatValue = it.boundsInWindow().top }
                            .graphicsLayer { alpha = if (progressSliderState.isPreviewing) 0f else 1f },
                    )

                    // 进度条行.
                    // 控制层不可见时**不能从组合里摘掉**, 只淡到透明: 它撑着胶囊行到屏幕底缘的距离,
                    // 摘掉的话那颗提示按钮会当场往下掉一截 (下面的图标行同理)
                    TvPlayerProgressRow(
                        modifier = chrome,
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
                            chrome.graphicsLayer {
                                alpha = if (progressSliderState.isPreviewing) 0f else 1f
                            },
                        ) {
                            Spacer(Modifier.height(TV_PLAYER_PROGRESS_ROW_GAP))
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
                modifier = chrome.align(Alignment.BottomStart).fillMaxWidth(),
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

/** 胶囊按钮行: 相关推荐 / 制作人员 / 角色 / 评论 / 弹幕列表 (+ 弹幕发送展开框) + 靠右的 [trailing]. */
@Composable
private fun TvPlayerPillsRow(
    overlay: TvPlayerOverlayState,
    danmakuEditorState: DanmakuEditorState,
    vm: EpisodeViewModel,
    pillFocusRequesters: Map<TvPlayerPanel, FocusRequester>,
    /** 评论胶囊按下确定: 发表本集评论 (见 [openNewEpisodeComment]). */
    onNewComment: () -> Unit,
    /** 行末靠右对齐的插槽 (OP/ED 提示按钮): 不受 [pillsModifier] 影响, 有自己的显示时长. */
    trailing: @Composable () -> Unit,
    /** 只作用于胶囊本体那一组 (控制层淡出), 不含 [trailing]. */
    pillsModifier: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    // 正开着的是不是"发表评论"那个弹窗 (评论胶囊点出来的那个, 没有引用区): 关掉后焦点要还给
    // 那颗胶囊. 只在弹窗开合时变一次, 不是每帧都动的热状态
    val composingNewComment = overlay.replyingComment.let { it != null && it.quoted == null }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // 胶囊本体单独成组占满剩余宽度, 把 trailing 顶到最右.
        // 焦点区域上报只挂这一组, 不含 trailing: 那颗按钮聚焦时不该把图标行收起
        // (hideBelowProgress 判的就是 PILLS) —— 提示一出现焦点就落过去, 控制层会当场塌一档
        Row(
            pillsModifier
                .weight(1f)
                .onFocusChanged { if (it.hasFocus) overlay.focusRegion = TvPlayerFocusRegion.PILLS },
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
                // 本颗胶囊的点击另有其用: 发表本集评论.
                //
                // 默认的"把焦点送进面板"与直接按上键完全重复 (面板早在聚焦本胶囊时就浮出来了),
                // 这一下等于白按; 而发新评论此前在 TV 上没有任何入口 —— 只能回复已有评论,
                // 手机端那颗「发送评论」FAB 在遥控器形态下没有对应物
                onClick = onNewComment,
                // 弹窗关掉后焦点还给本胶囊: 弹窗抢焦点时本节点还在场 (控制层与面板都留在下面),
                // 但 Compose 不会自己还回来. 控制层已经收起时放弃 —— 那时焦点归属归根路由管
                modifier = Modifier.restoreFocusAfter(
                    composingNewComment,
                    abandon = { overlay.layer != TvPlayerLayer.CONTROLS },
                ),
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
        }
        trailing()
    }
}

/**
 * "一起看"入口 (承担遥控器上没有的悬浮气泡的作用). 弹窗本体挂在应用根部, 这里只负责开与善后.
 *
 * 原先是胶囊行末尾那颗带文字的胶囊, 现已并入进度条下面的图标行, 与其余功能按钮同为圆钮.
 *
 * 显隐由 [TvPlayerBottomRow] 判断 —— 功能被关掉时本组合整个消失, 焦点善后只能由父级做.
 */
@Composable
private fun TvWatchTogetherButton(
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

    TvBottomRowIcon(
        icon = Icons.Rounded.SyncAlt,
        contentDescription = stringResource(Lang.watch_together_title),
        onClick = { entry.open(overDarkBackground = true) },
        // 关掉后焦点还给本按钮: 弹窗是独立窗口, 关闭时主窗口未必把焦点还到原处,
        // 不还的话控制层还在但方向键全失效.
        // 控制层已经收起时放弃: 那时焦点归属由根路由的解析器负责, 再抢就是打架
        modifier = modifier.restoreFocusAfter(
            dialogVisible,
            abandon = { overlay.layer != TvPlayerLayer.CONTROLS },
        ),
    )
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
    /** 默认是把焦点送进面板 (与上键一致); 另有动作的胶囊自己传 (见评论胶囊). */
    onClick: () -> Unit = { overlay.requestPanelFocus() },
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
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

/**
 * OP/ED 提示按钮 (Infuse / Prime Video 的 Skip Intro 同一位置与形态). 两副面孔:
 *
 * - `tip.canCancel` = true: 自动跳过正在倒计时, 按钮是"取消跳过 OP/ED";
 * - false: 人已经在 OP/ED 里 (刚按过取消, 或从别处 seek 进来), 按钮是"跳过 OP/ED", 按下直接
 *   跳到本段结尾. 于是整段 OP/ED 期间屏幕上始终有一颗可按的按钮.
 *
 * 文案里的 OP/ED 是**分开写**的 (`tip.kind`), 不含糊成"OP/ED": 哪一段由它在时间轴上的位置定
 * (见 PlayerSkipOpEdState), 既然判得出来就说清楚.
 *
 * 取代原来左下角那张 M3 浅色卡片 toast: 它按 `bottom = 140dp` 摆在左下, 正好压在胶囊按钮行上,
 * 且浅底深字与整个播放器的黑白控件完全不是一套.
 *
 * 它**就是胶囊行的最后一颗**, 与"相关推荐"那些按钮同排, 位置由布局直接给出 —— 那一行会上下动
 * (焦点落胶囊时图标行收起, 进度条连胶囊行一起下移, 还是逐帧动画), 作为行内一员天然跟得上.
 * 那条线的右半边永远是空的, 挡不到内容.
 *
 * 特殊之处只有两条: **显示时长自成一套** (由 PlayerSkipOpEdState 决定, 与改版前的 toast 一致),
 * 以及**出现时主动要焦点**. 控制层该 5 秒自动隐藏还是自动隐藏 —— 那时整行连同进度条淡到透明,
 * 但**布局照旧留在场上** (见 [TvPlayerControlsOverlay] 的 `chromeVisible`), 于是屏幕上只剩这颗
 * 按钮, 位置纹丝不动.
 *
 * 配色与胶囊完全一致 (未聚焦白 0.14, 聚焦白底黑字): 它就该看着像那一行的一员. 试过用面板条目
 * 那套黑玻璃让它更抓眼, 但控制层淡出后屏上只剩它一颗, 已经足够显眼, 反倒是两套底色摆在同一行
 * 上更扎眼.
 */
@Composable
internal fun TvSkipOpEdTipButton(
    tip: SkipOpEdTip,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    Surface(
        onClick = onClick,
        modifier = modifier,
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
            Icon(
                if (tip.canCancel) Icons.Rounded.Close else Icons.Rounded.FastForward,
                null,
                Modifier.size(TV_PILL_ICON_SIZE),
            )
            Text(
                // 说清楚是 OP 还是 ED (按章节在时间轴上的位置判, 见 PlayerSkipOpEdState).
                // "OP"/"ED" 是原文照抄的行话, 各语言一样, 不进资源
                stringResource(
                    if (tip.canCancel) {
                        Lang.video_player_tv_cancel_skip_segment
                    } else {
                        Lang.video_player_tv_skip_segment
                    },
                    when (tip.kind) {
                        SkipOpEdKind.OP -> "OP"
                        SkipOpEdKind.ED -> "ED"
                    },
                ),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}

/**
 * 遥控器形态下可调的倍速范围: 固定用播放器支持的全范围 (0.25x–4x), 见 [PlaybackSpeedControllerState] 处的注释.
 *
 * 直接开到硬上限而不是默认的 0.5x–2.5x: 既然"倍速范围"这条设置在遥控器上已经去掉了, 就别再替用户
 * 收窄. 代价是能选到 3 倍以上 —— 那时弹幕会跳 (上游 #1524, 长按倍速默认 2.5 就是为此), 低端盒子的
 * 解码与音频变速也会吃力, 但这是用户自己选的档位.
 */
private val TV_PLAYBACK_SPEED_RANGE =
    VideoScaffoldConfig.MIN_SUPPORTED_PLAYBACK_SPEED..VideoScaffoldConfig.MAX_SUPPORTED_PLAYBACK_SPEED

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
            .focusable(interactionSource = interactionSource),
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
    // "一起看"按钮的显隐在本行 (而不是按钮自己) 判断: 用户可以在弹窗的 ⋮ 里关掉整个功能,
    // 那一下按钮连同它自己的焦点善后逻辑一起被移除, 只有留在场上的父级能接手 —— 把焦点送回
    // 进度条 (与从面板按返回同一个落点). 没有这一手就是按钮消失 + 焦点消失, 方向键全失效.
    val watchTogetherEnabled = LocalWatchTogetherEntry.current.enabled
    var watchTogetherWasEnabled by remember { mutableStateOf(false) }
    LaunchedEffect(watchTogetherEnabled) {
        if (watchTogetherEnabled) {
            watchTogetherWasEnabled = true
            return@LaunchedEffect
        }
        // 一开始就没开 (或本行刚组合出来) 不算"刚被关掉", 不能抢焦点
        if (!watchTogetherWasEnabled) return@LaunchedEffect
        watchTogetherWasEnabled = false
        overlay.focusProgress()
    }
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
            // 一起看: 与弹幕同属"和别人一起看"那一类, 所以并进本组末尾. 位置的取舍是 ——
            // 一次观看里最多开一次, 排不到从头开始/下一集/换源前面; 但再往右就是收藏/统计
            // 那些低频项与右半边的设置类按钮, 一个招牌功能埋在那儿要多按七八下才够得着
            if (watchTogetherEnabled) TvWatchTogetherButton(overlay)

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
            //
            // rangeProvider / onCommitSpeed 必须给 (与手机端 EpisodePage 一致): 少了它们,
            // 滑块用的是默认 0.5x–2.5x 而不是用户设的范围, 且这里调的倍速既不写回配置也不写进
            // ViewModel 的 override —— 而 PlaybackSpeedExtension 会在切集/重新起播时按配置里的
            // playbackSpeed 重新应用, 于是用户在播放器里改的倍速会莫名其妙被弹回去
            val speedController = remember(vm) {
                vm.player.features[PlaybackSpeed]?.let {
                    PlaybackSpeedControllerState(
                        playbackSpeed = it,
                        // 固定用默认范围, 不读配置里的 min/max: 遥控器形态下"倍速范围"那条设置
                        // 已经不提供了 (见 AppSettingsTab.PlaybackSpeedItems), 而配置里可能还
                        // 留着以前被改窄的值, 读它就等于永远调不回来
                        rangeProvider = { TV_PLAYBACK_SPEED_RANGE },
                        onCommitSpeed = { speed -> vm.setPlaybackSpeed(speed) },
                        scope = scope,
                    )
                }
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
        TvBottomRowIconButton({ vm.onClickSkipOpEd(vm.player.currentPositionMillis.value) }) {
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
