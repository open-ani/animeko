/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.tv

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.LocalPlatformContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.him188.ani.app.utils.formatSpeedValue
import me.him188.ani.app.ui.foundation.LocalImageLoader
import me.him188.ani.app.ui.subject.details.sections.episodeStillImageRequest
import me.him188.ani.app.ui.subject.details.SubjectDetailsUIState
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.domain.player.VideoLoadingState
import me.him188.ani.app.ui.danmaku.DanmakuEditorState
import me.him188.ani.app.ui.danmaku.PlayerDanmakuHost
import me.him188.ani.app.ui.foundation.LocalImageViewerHandler
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.subject.episode.EpisodePageState
import me.him188.ani.app.ui.subject.episode.EpisodeViewModel
import me.him188.ani.app.ui.subject.episode.video.SkipOpEdTip
import me.him188.ani.app.ui.subject.episode.video.components.EpisodeVideoSideSheetPage
import me.him188.ani.app.ui.subject.episode.video.loading.EpisodeVideoLoadingIndicator
import me.him188.ani.app.videoplayer.ui.PlayerStatsOverlay
import me.him188.ani.app.videoplayer.ui.VideoPlayer
import me.him188.ani.app.videoplayer.ui.hasPageAsState
import me.him188.ani.app.videoplayer.ui.progress.rememberMediaProgressSliderState
import me.him188.ani.app.videoplayer.ui.rememberPlayerStatsState
import me.him188.ani.app.videoplayer.ui.rememberVideoSideSheetsController
import me.him188.ani.danmaku.ui.DanmakuHostState
import me.him188.ani.utils.logging.info
import me.him188.ani.utils.logging.logger
import org.openani.mediamp.MediampPlayer
import org.openani.mediamp.features.PlaybackSpeed
import org.openani.mediamp.togglePlayWhenReady

/** 遥控器左右键单次快进/快退步长. 也是长按拖拽预览的起步步长, 见 [TV_SCRUB_MAX_SPEEDUP]. */
internal const val TV_PLAYER_SEEK_STEP_MILLIS = 5_000L

/**
 * 长按左右键挪圆点时步长的最大倍率 (相对 [TV_PLAYER_SEEK_STEP_MILLIS]).
 *
 * 按住越久走得越快 (与常见流媒体一致), 松手即归零回到起步步长 —— 恒速的话找远处的位置要按很久,
 * 而一上来就很快又没法微调.
 */
private const val TV_SCRUB_MAX_SPEEDUP = 12f

/**
 * 每多少发连发把倍率加 1. 遥控器按住约 50ms 一发, 所以 8 发 ≈ 0.4 秒加一档,
 * 到顶 ([TV_SCRUB_MAX_SPEEDUP]) 约 4.4 秒.
 */
private const val TV_SCRUB_RAMP_REPEATS = 8

/**
 * 加速到顶时, 至少也要这么多发连发才走得完整条时间轴.
 *
 * 倍率是相对固定步长的, 而节目长度差很远: 12 倍 (60 秒一发) 对两小时的电影正合适, 对 3 分钟的
 * PV 却是一发就冲到头. 按总时长再压一道上限, 短片才留得住可控性 (60 发 ≈ 3 秒走完全程).
 */
private const val TV_SCRUB_MIN_REPEATS_ACROSS = 60

/**
 * 长按挪圆点时这一发该走多少毫秒.
 *
 * [repeats] = 本次按住已收到的第几发 (1 = 刚按下, 松手归零). 第一发恒为
 * [TV_PLAYER_SEEK_STEP_MILLIS] —— 单按与长按起手的手感要和中央那个"5 秒"反馈图标对得上.
 */
private fun scrubStepMillis(totalDurationMillis: Long, repeats: Int): Long {
    val speedup = (1f + (repeats - 1).coerceAtLeast(0) / TV_SCRUB_RAMP_REPEATS.toFloat())
        .coerceAtMost(TV_SCRUB_MAX_SPEEDUP)
    val ramped = TV_PLAYER_SEEK_STEP_MILLIS * speedup
    // 按总时长压的那道上限不能反过来把起步步长压小 (短到 5 分钟以内的片子)
    val cap = (totalDurationMillis.toFloat() / TV_SCRUB_MIN_REPEATS_ACROSS)
        .coerceAtLeast(TV_PLAYER_SEEK_STEP_MILLIS.toFloat())
    return ramped.coerceAtMost(cap).toLong()
}

/** 控制层自动隐藏延时 (播放中且无面板/弹层/输入时, Prime 行为). */
internal const val TV_PLAYER_AUTO_HIDE_MILLIS = 5_000L

/** 剧照预取的起始延迟: 让首帧起播先用完带宽. */
private const val TV_STILL_PREFETCH_DELAY_MILLIS = 2_000L

/** 等待 TMDB 剧照索引到达的上限: 无图条目 (未匹配到 TMDB) 永远等不到, 到点放弃. */
private const val TV_STILL_PREFETCH_WAIT_MILLIS = 30_000L

/** 预取当前集之后的集数 (往后是主要浏览方向; 选集条一屏 4 张, 多备几张够翻一屏). */
private const val TV_STILL_PREFETCH_AHEAD = 6

/**
 * 拖拽预览缩略图的解码尺寸上界.
 *
 * 对齐浮窗里帧区域的实际尺寸: 那个 Box 在 `PreviewFrameAndTimeText` 里是**写死的 160x90dp**,
 * 请求更大只是解出用不上的像素 (TV 的 640dpi 下 160dp 已经是 640px). 数值恰好也和 Prime 实测
 * 一致 (1920x1080 布局下缩略图约占屏宽 16.5%, 即 158dp).
 */
private val TV_SCRUB_PREVIEW_MAX_WIDTH = 160.dp
private val TV_SCRUB_PREVIEW_MAX_HEIGHT = 90.dp

/** 详情层淡入时长 (毫秒). */
private const val TV_DETAILS_FADE_IN_MS = 300

/** 详情层淡出时长 (毫秒): 放慢一档, 瞬时/快速移除观感像闪切. */
private const val TV_DETAILS_FADE_OUT_MS = 500

/**
 * TV 播放器界面 (Prime Video 风格):
 *
 * - 纯视频态 (HIDDEN): 只有画面和弹幕. 确认/暂停键切换播放并唤出控制层, 上下键仅唤出,
 *   左右键快进退但**不唤出** (中央浮现快进退图标作反馈).
 * - 控制层 (CONTROLS): 顶部标题/时钟, 底部 [胶囊按钮行 + 进度条 + 图标行];
 *   聚焦胶囊按钮时其上方浮出对应面板 (弹幕列表/相关推荐/本集评论), 面板条目吸附底部,
 *   从下往上导航.
 * - 详情页 (DETAILS): 图标行按下键唤出, 隐藏全部播放器组件, 视频画面作为详情页背景.
 *
 * 所有按键语义集中在本文件的唯一路由 (根 onPreviewKeyEvent), 层级切换集中在
 * [TvPlayerOverlayState]; 各行容器只通过 onFocusChanged 上报焦点区域, 不各自处理按键.
 */
@Composable
fun TvEpisodeScreenContent(
    vm: EpisodeViewModel,
    page: EpisodePageState,
    danmakuHostState: DanmakuHostState,
    danmakuEditorState: DanmakuEditorState,
    /**
     * TV 上不用: 回复评论走自己那个大弹窗 ([TvCommentReplyDialog]), 不是手机端的底部 sheet.
     * 参数保留是因为 EpisodeScreenVariant 接口要求.
     */
    @Suppress("UNUSED_PARAMETER")
    setShowEditCommentSheet: (Boolean) -> Unit,
    /**
     * TV 上不用: 各个浮出层 (弹幕列表、推荐、评论及其完整评论弹窗) 一律不动播放状态, 边看边翻.
     * 参数保留是因为 EpisodeScreenVariant 接口要求.
     */
    @Suppress("UNUSED_PARAMETER")
    pauseOnPlaying: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val overlay = remember { TvPlayerOverlayState() }
    val seekFlash = remember { TvSeekFlashState() }
    val sheetsController = rememberVideoSideSheetsController<EpisodeVideoSideSheetPage>()
    val anySheetVisible by sheetsController.hasPageAsState()
    val imageViewer = LocalImageViewerHandler.current

    SideEffect { vm.onUIReady() }

    // 预载条目详情 (TMDB 剧照/时长/简介, 详情层内容): 详情层与选集条的增量信息共用同一 loader
    // (有"已加载"守卫, 不会重复请求). 分集列表本身不等它, 见 EpisodeViewModel.episodeListUiStateFlow.
    LaunchedEffect(Unit) {
        val detailsState = vm.episodeDetailsState
        detailsState.subjectDetailsStateLoader.load(detailsState.subjectId, detailsState.subjectInfo.value)
    }

    // 预热 presentation: 它是 WhileSubscribed(5000) 的惰性流, 而读它的详情层只在被唤出时才组合 ——
    // 在那之前没有任何收集者, 上游根本没启动. 实测控制层隔 6.4 秒才唤出的那次, loader 早在
    // +0.8s 就 Ok 了, presentation 却一直等到 +6.49s 才脱离占位, 白等 5.7 秒.
    // 挂一个空收集者让它跟起播一起预热: 数据仍由各处 UI 自己读, 这里只负责把上游拉起来.
    LaunchedEffect(Unit) {
        vm.episodeDetailsState.subjectDetailsStateLoader.state
            .filterIsInstance<SubjectDetailsUIState.Ok>().first()
            .value.presentation.collect { }
    }

    // 预取选集条卡片的剧照.
    //
    // 卡片行只在展开态组合 (见 TvPlayerEpisodeStrip), 不预取的话按下键那一刻才发请求, 首屏是
    // 一排空卡; 反向也一样 —— 从播放器退回详情页往下翻, 图同样是冷的. 两处的请求由
    // episodeStillImageRequest 钉成同一条缓存, 所以这里预取的位图两边都能直接命中.
    //
    // 放在进屏 (而不是控制层出现时): 用户可能一直看片、从没展开过选集条就退出去了, 那时按
    // 控制层触发就还是冷的. 延迟一下避开首帧起播的带宽争抢 —— 一屏几张 w780 约几十 KB 一张,
    // 对能流视频的连接微不足道, 但没必要跟起播抢.
    val imageLoader = LocalImageLoader.current
    val platformContext = LocalPlatformContext.current
    LaunchedEffect(Unit) {
        delay(TV_STILL_PREFETCH_DELAY_MILLIS)
        val detailsState = vm.episodeDetailsState
        val ok = detailsState.subjectDetailsStateLoader.state
            .filterIsInstance<SubjectDetailsUIState.Ok>().first().value
        // 无图条目 (TMDB 未匹配到) 会一直等不到非空, 由外层超时收场
        val stills = withTimeoutOrNull(TV_STILL_PREFETCH_WAIT_MILLIS) {
            ok.tmdbEpisodeStillsFlow.first { it.isNotEmpty() }
        } ?: return@LaunchedEffect
        // 分集列表与选集条同源 (播放器自己那条): 详情状态的 presentation 此刻可能还是占位值,
        // 读它会拿到空列表, 于是一张都不预取
        val episodes = vm.episodeListUiStateFlow.filterNotNull().first().mainEpisodes
        val current = episodes.indexOfFirst { it.episodeId == vm.episodeSelectorState.current?.episodeId }
            .coerceAtLeast(0)
        // 以当前集为中心的一小段: 选集条初始滚到当前集, 往后是主要浏览方向, 往前留一张
        val urls = ((current - 1).coerceAtLeast(0)..(current + TV_STILL_PREFETCH_AHEAD))
            .mapNotNull { i -> stills[episodes.getOrNull(i)?.episodeId ?: return@mapNotNull null] }
        for (url in urls) {
            // 选集条一露面就停手: 卡片自己会请求同一批图, 而 Coil **不合并**并发的同 key 请求 ——
            // 不停手就是同一张图同时下两遍, 抢的还是同一份带宽 (实测 12 个并发 fetch 时单张
            // 40KB 的图要 700~900ms, 比不预取还慢). 已下完的留在缓存里, 卡片直接命中.
            if (overlay.episodeStripExpanded) break
            // 串行而非并发 enqueue: 预取是背景工作, 一次占一个连接就够, 不跟正在播的视频抢
            imageLoader.execute(episodeStillImageRequest(platformContext, url))
        }
    }

    val progressSliderState = rememberMediaProgressSliderState(
        vm.player,
        vm.progressChaptersFlow,
        onPreview = {},
        onPreviewFinished = { vm.player.seekTo(it) },
        // 拖拽预览时白色高亮段留在播放位置, 只有圆点跟着遥控器走 (Prime 行为):
        // 遥控器是一格一格挪的, 高亮段不动才看得出相对原位置走了多远, 而返回键取消后
        // 也不需要把高亮段倒回去
        trackFollowsPreview = false,
    )

    // 拖拽预览的帧源 (小圆点上方的缩略图).
    //
    // 用 TV 自己那份而不是 rememberMediaProgressFramePreviewState: 后者的 Android 实现打不开
    // HLS, 而在线源基本都是 m3u8 —— 详见 TvFramePreviewSource 文件头.
    //
    // 建在这里而不是控制层内部: 控制层隐藏时整棵子树被移除, 建在里面每次唤出都重建 ——
    // 预热好的取帧会话 (建 ExoPlayer + 解析播放列表, 秒级) 和帧缓存全丢, 每次进拖拽态
    // 第一张缩略图都要重等.
    //
    // 尊重"显示视频帧预览"设置项 (与手机端同一个开关): 关掉后浮窗只剩时间文本.
    val framePreview = if (vm.videoScaffoldConfig.enableFramePreview) {
        rememberTvFramePreviewState(
            vm.player,
            maxWidth = TV_SCRUB_PREVIEW_MAX_WIDTH,
            maxHeight = TV_SCRUB_PREVIEW_MAX_HEIGHT,
        )
    } else {
        null
    }

    // ---- 拖拽预览态 (Prime 行为) ----
    //
    // "态"没有新字段: 它就是 `progressSliderState.isPreviewing` —— 小圆点脱离播放位置,
    // 画面停着不动, 圆点上方浮缩略图. 进入方式两种, 语义完全一致:
    //   - 纯视频态连按两次左右键 (第二次落在中央反馈还没消失的窗口里)
    //   - 控制层里焦点已在进度条, 直接按左右键
    // 出口只有两个, 与 Prime 一致:
    //   - 播放/确认键: 提交 (seek 到圆点) + 继续播放 + 收 UI
    //   - 返回键: **不提交**, 画面留在原位置 (取消), 只收 UI 并保持暂停
    // 上下键在这个态里一律吞掉不做事: 拖拽时能做的只有挪圆点和决定去不去, 换焦点区域只会
    // 让圆点位置和界面对不上 (而且高亮段/缩略图都是围绕进度条的, 换了区域就没意义了).

    /** 把小圆点移一步 (不 seek). 首次调用时以当前播放位置为锚. */
    /**
     * 挪一次圆点. [repeats] 是本次按住已经收到的第几发 (1 = 刚按下), 用来加速, 见 [scrubStepMillis].
     */
    fun scrubStep(forward: Boolean, repeats: Int) {
        val total = progressSliderState.totalDurationMillis
        if (total <= 0L) return // 时长未知 (刚起播/直播) 时进度条本身就没有意义
        val from = if (progressSliderState.isPreviewing) {
            (progressSliderState.displayPositionRatio * total).toLong()
        } else {
            progressSliderState.currentPositionMillis
        }
        val magnitude = scrubStepMillis(total, repeats)
        val step = if (forward) magnitude else -magnitude
        progressSliderState.previewPositionRatio((from + step).coerceIn(0L, total).toFloat() / total)
    }

    /** 纯视频态连按第二次: 升级成拖拽预览态. */
    fun enterScrub(forward: Boolean, repeats: Int) {
        // 中央箭头让位: 接下来的反馈是暂停图标 + 进度条, 三个叠在一起没法看
        seekFlash.cancel()
        // 暂停反馈不用手动触发, TvPauseFlash 监听状态流自己会闪
        vm.player.pause()
        overlay.showControls() // 焦点落进度条
        scrubStep(forward, repeats)
    }

    /**
     * 退出拖拽预览.
     *
     * [commit] = true 时跳到圆点并继续播放 (播放/确认键), **控制层留着** —— 落地之后正是要看
     * 一眼跳到哪儿了, 之后按 5 秒自动隐藏照常收. false 时丢弃圆点位置, 画面留在原处且保持暂停,
     * 并收起全部组件 (返回键 = 取消, 那就一并退出去).
     *
     * 提交路径原本也 hideAll, 但紧接着的确认键 KeyUp 落在已经变成 HIDDEN 的层上, 又被那边的
     * 分支 showControls() 唤了回来 —— 净效果本来就是"留着", 中间那趟往返却看得见: 焦点会先被
     * 甩到 OP/ED 提示按钮上 (纯视频态屏上只剩它) 再弹回进度条. 索性不收.
     *
     * 提交路径用 `play()` 而不是 `togglePlayWhenReady()`, **无条件**变成播放态: 进入拖拽必然先暂停
     * (见 [enterScrub]), 所以"确认"在这个态里只可能是"从圆点这儿开始播" —— 与进入之前是播放
     * 还是暂停无关. 换成 toggle 的话从暂停进来的那次会把播放器又切回暂停.
     */
    fun exitScrub(commit: Boolean) {
        if (commit) {
            // mediamp 0.3.0 之前这里的顺序是硬约束: 旧 `resume()` 只在 READY/PAUSED 两个状态下才
            // 真的动手, 而 seekTo 会把状态推到 PAUSED_BUFFERING (ExoPlayer 在 seekTo 内部就同步派发
            // STATE_BUFFERING), 于是 seek 之后再 resume 必然被静默丢弃, 表现为"按确认键后还是暂停".
            //
            // v2 的 `play()` 只是置播放意图 (playWhenReady), 不再被状态门控, 顺序上已经不敏感;
            // 这里保留"先置意图再 seek"是因为它语义更直白: 落地即续播, 中间不会出现一帧暂停态.
            vm.player.play()
            progressSliderState.finishPreview() // 内部走 onPreviewFinished -> player.seekTo
        } else {
            progressSliderState.cancelPreview()
            overlay.hideAll()
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val progressRowFocusRequester = remember { FocusRequester() }
    val bottomRowFocusRequester = remember { FocusRequester() }
    val episodeStripFocusRequester = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    // 同一次物理按下已经换过一层: 按住下键时遥控器连发 KeyDown (约 50ms 一次), 而下键在控制层里
    // 每一档都换一层 (图标行 -> 选集条 -> 详情层), 连发会一路跳到底 —— 观感是选集条刚滑出来就
    // 闪进了详情页. 松手 (KeyUp) 才解锁. 只锁"换层"的那几档, 面板内按住下键滚列表不受影响
    var downKeyLatched by remember { mutableStateOf(false) }
    // 左右键按住期间的连发计数 + 当前按住的是哪一边: 挪圆点的步长据此加速 (见 scrubStepMillis),
    // 松手归零. 维护放在路由最前面而不是各分支里 —— 下面每一层都有 `if (!isKeyDown) return false`,
    // KeyUp 到不了分支; 换方向 (右按住中改按左) 也要重新起步, 所以连方向一起记.
    // 只在事件回调里读写, 不在组合里读, 不会引起重组
    var scrubHoldKey by remember { mutableStateOf<Key?>(null) }
    var scrubHoldRepeats by remember { mutableIntStateOf(0) }
    // 确认键**在任何层级下**是否已经按住: 连发的 KeyDown 不算新手势的开始.
    // 与下面那个 confirmKeyHeld 不同 —— 那个只在纯视频态记账 (长按倍速用)
    var confirmHeldAnywhere by remember { mutableStateOf(false) }
    // 确认键当前是否按住 + 每次按下自增的计时锚 (长按倍速用, 见下方长按协程).
    // 用 tick 而不是布尔的上升沿: 快速连按两次时 collectLatest 才能重启计时
    var confirmKeyHeld by remember { mutableStateOf(false) }
    var confirmKeyHoldTick by remember { mutableIntStateOf(0) }
    // 长按倍速生效中: 抬起时据此判断"这是长按, 不要切换播放"
    var fastForwarding by remember { mutableStateOf(false) }
    // 长按结束后待补发的原速 (null = 无待办), 见下方"倍速还原补发"协程
    var pendingSpeedRestore by remember { mutableStateOf<Float?>(null) }
    // 确认键的这一次**按下**是落在 OP/ED 提示按钮上的吗. 按下与抬起必须成对, 否则会吃到残余:
    // 提交拖拽预览就是这样 —— 按下那一下在进度条上 (收起控制层 + 把焦点交给提示按钮), 抬起时
    // 焦点已经在按钮上了, 不记账的话这一下抬起会把按钮也按掉, 顺带还让控制层再没机会回来
    var confirmDownOnSkipTip by remember { mutableStateOf(false) }

    // ---- OP/ED 提示按钮 (看上去是胶囊行最右一颗, 见 TvSkipOpEdTipButton) ----
    // 遥控器上这颗"取消"原本够不着: 没有任何东西把焦点送给它, 根路由也不认识它.
    // 现在提示一出现就把焦点送过去, 走完 (按了取消 / 时间到自动跳) 再把焦点还回原处.
    val skipTipFocusRequester = remember { FocusRequester() }
    var skipTipFocused by remember { mutableStateOf(false) }
    // 按钮的存亡**只由 PlayerSkipOpEdState 决定** (按了取消 / 时间到自动跳过), 没有任何按键能把它
    // 按没: 提示在场期间遥控器上每个键的意义都该和平时一模一样, 多出来的只是"确认键现在按的是
    // 这颗按钮"这一条. 早先返回键收起本次提示的做法已删 —— 那让返回键在这几秒里换了个意思.
    //
    // 两副面孔 (见 TvSkipOpEdTipButton): 自动跳过倒计时中 = "取消跳过"; 人已经在 OP/ED 里
    // (刚按过取消, 或从别处 seek 进来) = "跳过". 于是整段 OP/ED 期间屏上始终有一颗可按的
    val skipTip = vm.playerSkipOpEdState.currentTip
    val skipTipCancelling = skipTip?.canCancel == true
    val skipTipVisible = skipTip != null
    // 渐隐期间提示已经没了, 按钮却还在淡出: 留住最后一次的内容, 免得文字/图标在渐隐途中变脸
    var lastSkipTip by remember { mutableStateOf<SkipOpEdTip?>(null) }
    LaunchedEffect(skipTip) { if (skipTip != null) lastSkipTip = skipTip }

    // 按钮当前是否持有焦点 (据本页所知). 不直接用 skipTipFocused 做善后判据: 节点被移除时
    // onFocusChanged 会先报一次失焦, 到善后那一步读到的永远是 false
    var skipTipHoldsFocus by remember { mutableStateOf(false) }
    LaunchedEffect(skipTipFocused) {
        if (skipTipFocused) {
            skipTipHoldsFocus = true
        } else if (skipTipVisible) {
            // 按钮还在场却失焦 = 焦点被正常地领走了 (用户按方向键走开 / showControls 把它送去
            // 进度条), 之后不必再还. 按钮已经不在场的那一次失焦不算 —— 那正是要善后的情形
            skipTipHoldsFocus = false
        }
    }

    // 该不该主动把焦点送给它:
    //   - "取消跳过"那一档一出现就送 —— 它只活几秒, 够不着等于没有;
    //   - 纯视频态下无论哪副面孔都送 —— 那时屏上除了它没有第二个能聚焦的东西 (控制层整个淡到
    //     透明了, 见 TvPlayerControlsOverlay 的 chromeVisible).
    // 控制层在场时的"跳过"不送: 用户刚说了不跳 (或是自己 seek 进来的), 再把焦点抢过去等于跟他
    // 对着干; 想按的话从胶囊行向右一步就到.
    // 走 hideAll 的那些路 (返回 / 自动隐藏) 由落点解析器对 ROOT 的改派兜住, 见下方解析器
    val skipTipWantsFocus = skipTipVisible &&
            (skipTipCancelling || overlay.layer == TvPlayerLayer.HIDDEN)
    LaunchedEffect(skipTipWantsFocus) {
        if (!skipTipWantsFocus) return@LaunchedEffect
        // 别的东西正管着焦点时不抢: 这些形态 (详情层 / 回复弹窗 / 弹幕输入 / 下拉与独立窗口 /
        // 侧边 sheet / 大图 / 选集条展开) 各有各的焦点归属, 抢了就把它们弄坏.
        // 判断放在 effect 里而不是组合里: 这几个字段变化很频繁, 在组合里读会让整个播放器界面
        // 跟着重组 (见 TvPlayerOverlayState 的性能约定)
        if (overlay.layer == TvPlayerLayer.DETAILS || overlay.replyingComment != null ||
            overlay.danmakuInputExpanded || overlay.openPopupCount > 0 ||
            anySheetVisible || imageViewer.viewing.value || overlay.episodeStripExpanded
        ) {
            return@LaunchedEffect
        }
        val arrived = resolveFocusRepeatedly(attempts = 20, arrived = { skipTipFocused }) {
            runCatching { skipTipFocusRequester.requestFocus() }
        }
        // 控制层开着的话重置一次它的自动隐藏计时: 提示的存活时间与计时同量级, 够撑到它自己走.
        // 不做更强的抑制 —— 控制层的显隐与本提示无关, 该收还是收
        if (arrived) overlay.markInteraction()
    }

    // 善后: **按钮真的消失了**而焦点还在它身上 —— 节点一移除焦点就没了, 得还回去.
    // 控制层开着就回进度条, 纯视频态回根节点 (后者不能也送进度条, 否则按个返回反而把控制层唤出来).
    //
    // 判据只能是"按钮没了", 不能是"焦点不再归它": 纯视频态下按下键会 showControls() —— 那既把
    // 焦点正常送去了进度条, 又让上面那个 skipTipWantsFocus 翻假. 若在那里也善后一次, 落点解析器
    // 会连发 40 帧 requestFocus 抢进度条, 用户紧接着按下键走到图标行会被当场拽回来 (实测复现)
    LaunchedEffect(skipTipVisible) {
        if (skipTipVisible || !skipTipHoldsFocus) return@LaunchedEffect
        skipTipHoldsFocus = false
        if (overlay.layer != TvPlayerLayer.CONTROLS) {
            overlay.requestRootFocus()
            return@LaunchedEffect
        }
        // 面板是"被抢焦点之前那颗胶囊"开的, 而本按钮不是面板触发器 —— 不主动收的话它会一直挂着,
        // 自动隐藏看门狗见 activePanel != null 就**永久停摆** (实测: 控制层再也不收起来)
        overlay.activePanel = null
        // 区域先清成 NONE: 它同时是下面那个到位判据, 而本按钮不上报任何区域, 此刻它还停在按钮
        // 抢焦点之前的那一档 (可能正是 PROGRESS). 不清的话一进门就认定"已经到位", 一次
        // requestFocus 都不发, 焦点跟着按钮的节点一起消失 (焦点解析器的"假成功"陷阱)
        overlay.focusRegion = TvPlayerFocusRegion.NONE
        // 直接自己重试到位, 不走 overlay.focusProgress(): 那条路要经过 pendingFocus, 而落点解析器
        // 是 collectLatest —— 期间任何一个新的焦点请求都会把我们这次解析整个取消掉, 焦点就此悬空.
        // 这一步的起点是"焦点刚随按钮一起没了", 没人跟我们抢, 自己发请求最稳
        resolveFocusRepeatedly(
            arrived = { overlay.focusRegion == TvPlayerFocusRegion.PROGRESS },
            // 同上: 焦点落到别的行上了就别再抢 (起点是 NONE, 刚在上一行清过)
            abandon = {
                overlay.focusRegion != TvPlayerFocusRegion.NONE &&
                        overlay.focusRegion != TvPlayerFocusRegion.PROGRESS
            },
        ) {
            runCatching { progressRowFocusRequester.requestFocus() }
        }
    }

    // 本页是否在前台. 从面板里点开别的页面 (相关推荐 -> 条目详情页) 期间为 false:
    //   - 那时不自动隐藏控制层 (子树被移除的话, 回来时焦点无处可还);
    //   - 重新回到前台时补发一次焦点落点 —— 离开期间聚焦的那个节点被销毁, Compose 会清掉
    //     整棵树的焦点且不交给祖先, 回来后面板还在屏上却没有任何焦点 (方向键全失效).
    // 生命周期信号与组合是否存活无关, 正好覆盖"快速返回时整棵子树一直没被销毁"的情形
    // (与追番页的返回落点同一套路, 见 TvCollectionPage).
    var pageResumed by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        var resumedBefore = false
        lifecycle.currentStateFlow.collect { state ->
            val resumed = state.isAtLeast(Lifecycle.State.RESUMED)
            pageResumed = resumed
            if (!resumed) return@collect
            // 首次 RESUMED 是进屏, 落点由 overlay 的初始 pendingFocus (ROOT) 负责
            if (!resumedBefore) {
                resumedBefore = true
                return@collect
            }
            when {
                overlay.layer == TvPlayerLayer.HIDDEN -> overlay.requestRootFocus()
                overlay.layer != TvPlayerLayer.CONTROLS -> {} // 详情层内部自己有落点
                overlay.activePanel != null -> overlay.requestPanelItemFocus()
                else -> overlay.focusProgress()
            }
        }
    }

    // ---- 唯一按键路由: 所有 Back 语义与层级切换都在这里, 状态读取只发生在事件回调内 ----
    val onRootKeyEvent: (KeyEvent) -> Boolean = router@{ event ->
        val key = event.key
        val isKeyDown = event.type == KeyEventType.KeyDown
        val isKeyUp = event.type == KeyEventType.KeyUp
        val isBack = key == Key.Back || key == Key.Escape

        if (key == Key.DirectionDown) {
            if (isKeyUp) {
                downKeyLatched = false
            } else if (isKeyDown && downKeyLatched) {
                return@router true
            }
        }

        val isConfirm = key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter
        // 这一发**之前**确认键是不是已经按住了. 先取值再记账: 取到的才是"上一发的状态"
        val confirmWasHeld = confirmHeldAnywhere
        if (isConfirm) {
            if (isKeyDown) {
                // 全新的一次按下: **当场定归属**, 整次手势 (连发 + 抬起) 都按这个走.
                // 归属只在这里改 —— 按住途中焦点跑到提示按钮上 (它一出现就抢焦点) 不能让这次
                // 手势改姓, 否则长按倍速松手那记 KeyUp 就成了"按下取消跳过".
                // 也不在抬起时清: 清了的话本次手势的 KeyUp 放行给按钮之后, 按钮自己那道闸
                // (见下方 pillsRowTrailing) 读到的已经是"不归我", 反而把真正的点击吞掉
                if (!confirmWasHeld) confirmDownOnSkipTip = skipTipVisible && skipTipFocused
                confirmHeldAnywhere = true
            }
            if (isKeyUp) confirmHeldAnywhere = false
        }
        // 抬起那一刻的连发次数: 下面判"这一下是单击还是长按"要用, 而记账在这里就把它清零了.
        // 1 = 只按下过一次, 没有连发
        val repeatsAtRelease = scrubHoldRepeats
        // 左右键按住计数 (挪圆点的加速依据, 见 scrubHoldRepeats 的声明处).
        // 不消费事件, 只记账 —— 左右键在别处 (面板、选集条) 另有语义
        if (key == Key.DirectionLeft || key == Key.DirectionRight) {
            when {
                isKeyUp -> {
                    scrubHoldKey = null
                    scrubHoldRepeats = 0
                }

                isKeyDown -> {
                    if (scrubHoldKey != key) {
                        scrubHoldKey = key
                        scrubHoldRepeats = 0
                    }
                    scrubHoldRepeats++
                }
            }
        }

        // 图片查看器 (详情页评论区打开的大图) 优先: 返回关闭
        if (imageViewer.viewing.value) {
            if (isBack) {
                if (isKeyUp) imageViewer.clear()
                return@router true
            }
            return@router false
        }
        // 弹幕输入态: 只拦返回收起, 其余全部交给输入框/IME
        if (overlay.danmakuInputExpanded) {
            if (isBack) {
                if (isKeyUp) overlay.danmakuInputExpanded = false
                return@router true
            }
            overlay.markInteraction()
            return@router false
        }
        // 评论弹窗 (回复 / 发表): 返回关闭并把焦点还给刚点开的那条评论 (见 overlay.dismissReply),
        // 其余按键全部交给弹窗内部 (引用区翻页 / 输入框 / IME)
        if (overlay.replyingComment != null) {
            overlay.markInteraction()
            if (isBack) {
                if (isKeyUp) {
                    // 表情选择器盖在弹窗之上: 返回先关它, 再按一下才关整个评论弹窗.
                    // 它不是独立窗口, 按键还是走这条唯一路由, 得在这儿分一档
                    if (vm.commentEditorState.showStickerPanel) {
                        vm.commentEditorState.toggleStickerPanelState(false)
                    } else {
                        overlay.dismissReply()
                    }
                }
                return@router true
            }
            return@router false
        }
        // 跳过 OP/ED 按钮持焦中. 本块**只做两件事**, 其余一概不碰 —— 提示在场的这几秒里,
        // 遥控器上每个键的意义都必须和没有这颗按钮时一模一样 (返回还是返回, 左右还是快进退),
        // 唯一的变化是"确认键现在按的是这颗按钮". 按钮也不会被任何键按没 (见 skipTipVisible).
        //
        //   1. 确认 = 放行给按钮自己的 onClick (不落到分层路由的"切换播放").
        //   2. 控制层开着时的返回 = 照常收起控制层 (纯视频态的返回不在此列: 那是退出播放器,
        //      照常交给分层路由). 收起之后焦点仍留在按钮上 —— 见落点解析器对 ROOT 的改派.
        //   3. 控制层开着时的方向键 = 放行给空间焦点搜索, 让用户能把焦点挪去胶囊行/进度条.
        //      这一档**不能落到下面的分层路由**: 焦点区域只在获焦时上报而本按钮不上报, 此刻
        //      focusRegion 还停在抢焦点之前的那一档, 按它路由会拿旧区域做事 (比如当成进度条
        //      持焦, 左右键直接进拖拽预览态).
        //      纯视频态不在此列: 那里本来就没有第二个可聚焦节点, 方向键交给 HIDDEN 路由照常
        //      快进退 / 唤控制层 —— 后者会把焦点落到进度条上, 焦点自然就离开按钮了.
        if (skipTipVisible && skipTipFocused) {
            when {
                isConfirm -> {
                    // 归属在路由开头就定死了 (见 confirmDownOnSkipTip): 只有"焦点已经在按钮上时
                    // 全新按下的那一次"才归按钮, 归了就一路归到抬起; 不归它的整次手势从头到尾
                    // 落到分层路由, 与没有这颗按钮时一模一样.
                    //
                    // 按单个事件判是不行的 —— 一次按住会连发几十个 KeyDown, 中途焦点落到本按钮上
                    // 的话后半段会改姓: 纯视频态长按确认键倍速, 按住期间按钮出现并抢焦点, 抬起
                    // 就把按钮点掉了 (而且纯视频态那边收不到抬起, 倍速也停不下来)
                    if (confirmDownOnSkipTip) {
                        // 按下/连发/抬起都放行给按钮自己 (clickable 在抬起时才触发 onClick)
                        return@router false
                    }
                    // 不归按钮: 什么都不 return, 落到下面的分层路由
                }

                isBack && overlay.layer == TvPlayerLayer.CONTROLS -> {
                    overlay.markInteraction()
                    // 与分层路由里那条返回键完全同一个动作 (拖拽预览就地取消 + 收起全部组件).
                    // 单列一条只是为了绕开那边的 focusRegion 分支 —— 焦点在按钮上时那个字段是旧值,
                    // 恰好停在 PANEL 的话返回会变成"回进度条", 控制层反而收不起来.
                    // 收起之后焦点仍归这颗按钮, 由落点解析器对 ROOT 的改派保证 (见下)
                    if (isKeyUp) exitScrub(commit = false)
                    return@router true
                }

                // 方向键交给空间焦点搜索, 让用户能把焦点挪去胶囊行/进度条 —— 但**拖拽预览
                // 进行中不算导航**: 纯视频态长按左右键会先唤出控制层再连发, 那几发到达时焦点
                // 还没从本按钮挪到进度条, 抢过来就把焦点甩到左边的胶囊上了 (实测: 长按变成
                // "进度条闪一下然后焦点跑到左边"). 那几发该继续推圆点, 交给下面的分层路由
                overlay.layer == TvPlayerLayer.CONTROLS && !progressSliderState.isPreviewing &&
                        (key == Key.DirectionUp || key == Key.DirectionDown ||
                                key == Key.DirectionLeft || key == Key.DirectionRight) ->
                    return@router false

                else -> {} // 返回 / 播放暂停 / 上下集 …… 全部照常, 交给下面的分层路由
            }
        }
        // 侧边 sheet (数据源/选集/弹幕设置) 打开: 返回关闭, 其余交给 sheet 内部导航
        if (anySheetVisible) {
            overlay.markInteraction()
            if (isBack) {
                if (isKeyUp) sheetsController.close()
                return@router true
            }
            return@router false
        }

        when (overlay.layer) {
            TvPlayerLayer.HIDDEN -> {
                // 确认键: 短按切换播放, **长按倍速** (与手机端长按画面同一功能).
                //
                // 因此这一档必须等抬起才知道是哪一种, 不能像别的键那样在 KeyDown 上就动手 ——
                // 在 KeyDown 上暂停的话, 长按会先闪一下暂停再变速. 专用的播放/暂停键不在此列
                // (它语义单一, 保持按下即响应).
                if (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter) {
                    if (isKeyDown) {
                        // 连发 KeyDown (按住时约 50ms 一次) 只算同一次按下
                        if (!confirmKeyHeld) {
                            confirmKeyHeld = true
                            confirmKeyHoldTick++
                        }
                    } else if (isKeyUp) {
                        confirmKeyHeld = false
                        // 倍速已生效 = 这是长按, 抬起只负责还原 (由下面的长按协程做), 不切换播放.
                        // 此刻 fastForwarding 一定还是 true: 协程挂在"等松手"上, 要到下一次
                        // 调度才会走到还原, 而这里是同一次事件回调内同步读的
                        if (!fastForwarding) {
                            // 暂停态下恢复播放不唤出控制层 (画面动起来即反馈); 播放态下暂停仍唤出.
                            // 按播放意图判断而非严格 isPlaying: 缓冲中按一下应当是"暂停", 不是"恢复"
                            val resuming = !vm.player.state.value.playWhenReady
                            vm.player.togglePlayWhenReady()
                            if (!resuming) overlay.showControls()
                        }
                    }
                    return@router true
                }
                // 单次快进退在**抬起**时才做: 按下那一刻分不出这一下是"单击跳 5 秒"还是
                // "长按找位置", 先跳的话长按必然白跳一格再进拖拽态 (实测就是这个观感).
                // 连发过 (长按) 或者已经升级成拖拽预览的, 抬起什么都不做
                if (isKeyUp && (key == Key.DirectionLeft || key == Key.DirectionRight)) {
                    if (repeatsAtRelease <= 1 && !progressSliderState.isPreviewing) {
                        val forward = key == Key.DirectionRight
                        vm.player.skip(
                            if (forward) TV_PLAYER_SEEK_STEP_MILLIS else -TV_PLAYER_SEEK_STEP_MILLIS,
                        )
                        seekFlash.flash(forward)
                    }
                    return@router true
                }
                if (!isKeyDown) return@router false
                when (key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        // 暂停态下恢复播放不唤出控制层 (画面动起来即反馈); 播放态下暂停仍唤出
                        val resuming = !vm.player.state.value.playWhenReady
                        vm.player.togglePlayWhenReady()
                        if (!resuming) overlay.showControls()
                        true
                    }

                    Key.DirectionUp, Key.DirectionDown -> {
                        overlay.showControls()
                        true
                    }

                    // 单按: 快进退不唤出控制层 (调时间轴不该把画面下半压掉一半再等 5 秒自动
                    // 隐藏), 反馈走中央快进退图标, 见 [TvSeekFlash]. **动作在抬起时才做**,
                    // 见上面那段 isKeyUp.
                    //
                    // 连按: 第二次按键落在"中央反馈还没消失"的窗口里 (约 0.6 秒) 就升级成拖拽
                    // 预览态 —— 连按说明用户不是想微调 5 秒, 而是要找位置, 这时给进度条 +
                    // 缩略图才有用. 判据直接用 seekFlash.visible, 与用户看到的东西严格一致,
                    // 不另外开一个跟视觉无关的计时器.
                    //
                    // 长按: 第二发连发 KeyDown 就进拖拽态, **一次单跳都不做** —— 长按的意思
                    // 从来就是"找位置", 先跳一格再进预览是白跳
                    Key.DirectionLeft, Key.DirectionRight -> {
                        val forward = key == Key.DirectionRight
                        if (seekFlash.visible || scrubHoldRepeats > 1) {
                            enterScrub(forward, scrubHoldRepeats)
                        }
                        true
                    }

                    Key.MediaFastForward -> {
                        if (vm.episodeSelectorState.hasNextEpisode) vm.episodeSelectorState.selectNext()
                        true
                    }

                    Key.MediaRewind -> {
                        if (vm.episodeSelectorState.hasPrevEpisode) vm.episodeSelectorState.selectPrev()
                        true
                    }

                    // Back 不消费: 交给系统返回, 退出播放器
                    else -> false
                }
            }

            TvPlayerLayer.CONTROLS -> {
                overlay.markInteraction()
                if (isBack) {
                    if (isKeyUp) {
                        // 面板条目上: 返回回进度条 (面板随焦点区域变化收起); 其余: 全部隐藏.
                        // 拖拽预览中: 丢弃圆点位置, 画面留在原处并保持暂停 (返回 = 取消)
                        if (overlay.focusRegion == TvPlayerFocusRegion.PANEL) {
                            overlay.focusProgress()
                        } else {
                            exitScrub(commit = false)
                        }
                    }
                    return@router true
                }
                // 拖拽预览中: 除左右 (挪圆点) / 确认与播放 (去) / 返回 (不去) 之外一律吞掉.
                // 上下键换焦点区域会让界面和圆点位置对不上, 换集则直接把圆点所指的时间轴换掉.
                // KeyUp 也吞: 空间焦点导航只吃 KeyDown, 但放行 KeyUp 没有意义
                if (progressSliderState.isPreviewing) {
                    when (key) {
                        Key.DirectionUp, Key.DirectionDown, Key.MediaFastForward, Key.MediaRewind ->
                            return@router true

                        else -> {}
                    }
                }
                if (!isKeyDown) return@router false
                when (key) {
                    // 图标行再往下: 展开选集条 (Prime 形态, 焦点落当前集卡片);
                    // 确认无分集 (未开播/加载失败) 才直通详情页, 数据未到则等就绪后自动展开.
                    // 选集条内再往下: 详情页 (第三层)
                    Key.DirectionDown -> when (overlay.focusRegion) {
                        TvPlayerFocusRegion.BOTTOM_ROW -> {
                            downKeyLatched = true
                            when (overlay.episodeStrip) {
                                TvEpisodeStripState.AVAILABLE -> overlay.expandEpisodeStrip()
                                // 还在加载: 记下意图等就绪 (跳详情页是"确认无分集"才该做的)
                                TvEpisodeStripState.LOADING -> overlay.expandEpisodeStripWhenReady()
                                TvEpisodeStripState.EMPTY -> overlay.openDetails()
                            }
                            true
                        }

                        TvPlayerFocusRegion.EPISODES -> {
                            downKeyLatched = true
                            overlay.openDetails()
                            true
                        }

                        else -> false // 其余交给空间焦点导航
                    }

                    // 选集条内按上键: 收起选集条, 控制行回来, 焦点还给图标行
                    Key.DirectionUp ->
                        if (overlay.focusRegion == TvPlayerFocusRegion.EPISODES) {
                            overlay.collapseEpisodeStrip()
                            true
                        } else {
                            false
                        }

                    // 进度条行的左右键 = 拖拽预览 (圆点走, 画面不走), 与纯视频态连按两次进来的
                    // 是同一个态: 焦点已经在进度条上, 就不必再要求"连按"作为意图确认了.
                    // 首次进入顺手暂停 —— 画面继续跑而圆点停在别处, 两个位置对不上
                    // 拖拽预览中不看 focusRegion: 那会儿屏上唯一有意义的就是圆点, 焦点在哪儿
                    // 不重要 —— 而纯视频态长按进来的那一瞬, 焦点还没从别处 (OP/ED 提示按钮)
                    // 挪到进度条上, 卡着判据的话这几发会被当成焦点导航
                    Key.DirectionLeft, Key.DirectionRight ->
                        if (progressSliderState.isPreviewing ||
                            overlay.focusRegion == TvPlayerFocusRegion.PROGRESS
                        ) {
                            if (!progressSliderState.isPreviewing) vm.player.pause()
                            scrubStep(forward = key == Key.DirectionRight, repeats = scrubHoldRepeats)
                            true
                        } else {
                            false
                        }

                    // 拖拽预览中: 确认 = 跳到圆点并继续播放 (控制层留着, 见 exitScrub)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                        if (overlay.focusRegion == TvPlayerFocusRegion.PROGRESS) {
                            if (progressSliderState.isPreviewing) exitScrub(commit = true)
                            else vm.player.togglePlayWhenReady()
                            true
                        } else {
                            false
                        }

                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> {
                        // 播放键在拖拽预览中与确认键同义 (遥控器上播放/暂停通常是同一个物理键,
                        // 拖拽态本来就是暂停的, 按它的意思只可能是"从这儿开始播")
                        if (progressSliderState.isPreviewing) exitScrub(commit = true)
                        else vm.player.togglePlayWhenReady()
                        true
                    }

                    Key.MediaFastForward -> {
                        if (vm.episodeSelectorState.hasNextEpisode) vm.episodeSelectorState.selectNext()
                        true
                    }

                    Key.MediaRewind -> {
                        if (vm.episodeSelectorState.hasPrevEpisode) vm.episodeSelectorState.selectPrev()
                        true
                    }

                    else -> false
                }
            }

            TvPlayerLayer.DETAILS -> when {
                // 详情页内按返回: 隐藏整个覆盖层回纯视频 (方案约定, 不走详情页内部返回分层)
                isBack -> {
                    if (isKeyUp) overlay.hideAll()
                    true
                }

                key == Key.MediaPlayPause || key == Key.MediaPlay || key == Key.MediaPause -> {
                    if (isKeyDown) vm.player.togglePlayWhenReady()
                    true
                }

                else -> false
            }
        }
    }

    // ---- 焦点落点解析 (到位确认 + 重试, 不裸 requestFocus) ----
    // 单一解析器消化 overlay.pendingFocus (PANEL 除外, 由面板宿主消化): collectLatest
    // 保证新请求一到旧解析立即取消 —— 过去四个目标各挂一个循环, 快速交替 (选集条
    // 展开→收起→展开) 时新旧循环并发 requestFocus 互抢焦点
    // ROOT 的落点会被改派: 纯视频态下 OP/ED 提示按钮若还在场, 屏上唯一能聚焦的就是它,
    // 焦点就该在它身上 (返回收起控制层 / 自动隐藏 / 进页面就赶上 OP …… 所有走 ROOT 的路一视同仁).
    // 每次尝试都重新判一遍: 按钮可能在解析途中出现或消失.
    // 收在解析器这一个口子上, 而不是让"抢按钮"和"抢根节点"两个循环并发 —— 那是互抢焦点的老坑
    fun skipTipOwnsRootFocus() = vm.playerSkipOpEdState.run { showSkipTips || canSkipNow }
    LaunchedEffect(Unit) {
        snapshotFlow { overlay.pendingFocus }.collectLatest { (target, _) ->
            val (expectedLayer, defaultRequester) = when (target) {
                TvPlayerFocusTarget.ROOT -> TvPlayerLayer.HIDDEN to rootFocusRequester
                TvPlayerFocusTarget.PROGRESS -> TvPlayerLayer.CONTROLS to progressRowFocusRequester
                TvPlayerFocusTarget.EPISODE_STRIP -> TvPlayerLayer.CONTROLS to episodeStripFocusRequester
                TvPlayerFocusTarget.BOTTOM_ROW -> TvPlayerLayer.CONTROLS to bottomRowFocusRequester
                TvPlayerFocusTarget.PANEL -> return@collectLatest
            }
            val redirectedToSkipTip = { target == TvPlayerFocusTarget.ROOT && skipTipOwnsRootFocus() }
            val requester = { if (redirectedToSkipTip()) skipTipFocusRequester else defaultRequester }
            // 目标对应的焦点区域, 以及解析开始那一刻的区域快照 (见下面的 abandon).
            // ROOT 没有对应区域 -> null -> 不启用 abandon
            val targetRegion = when (target) {
                TvPlayerFocusTarget.PROGRESS -> TvPlayerFocusRegion.PROGRESS
                TvPlayerFocusTarget.EPISODE_STRIP -> TvPlayerFocusRegion.EPISODES
                TvPlayerFocusTarget.BOTTOM_ROW -> TvPlayerFocusRegion.BOTTOM_ROW
                else -> null
            }
            val startRegion = overlay.focusRegion
            resolveFocusRepeatedly(
                // 用户自己把焦点挪到了第三个地方就别再跟他抢 (resolveFocusRepeatedly 的 KDoc 要求
                // 凡是能观察到焦点落在别处的调用点都要给 abandon). 首次 requestFocus 常常落空 ——
                // 控制层刚出现, 目标节点还没附着 —— 之后这循环会连烧 40 帧, 每帧再发一次; 用户
                // 这期间按上键走到胶囊行, 下一次尝试就把焦点拽回进度条 (实测复现过).
                // 判据: 区域变成了既不是起点也不是目标的第三档. 区域只在获焦时上报, 所以这确实是
                // "焦点落到别的行上了"; NONE 是交接瞬时值, 也是 showControls 写下的初值, 不算
                abandon = {
                    targetRegion != null &&
                            overlay.focusRegion != startRegion &&
                            overlay.focusRegion != targetRegion &&
                            overlay.focusRegion != TvPlayerFocusRegion.NONE
                },
                arrived = {
                    // 层已切走 = 放弃解析 (新层的落点由后续请求负责)
                    overlay.layer != expectedLayer || when (target) {
                        TvPlayerFocusTarget.ROOT -> if (redirectedToSkipTip()) skipTipFocused else rootFocused
                        TvPlayerFocusTarget.PROGRESS -> overlay.focusRegion == TvPlayerFocusRegion.PROGRESS
                        TvPlayerFocusTarget.EPISODE_STRIP -> overlay.focusRegion == TvPlayerFocusRegion.EPISODES
                        TvPlayerFocusTarget.BOTTOM_ROW -> overlay.focusRegion == TvPlayerFocusRegion.BOTTOM_ROW
                        TvPlayerFocusTarget.PANEL -> true
                    }
                },
            ) {
                if (overlay.layer == expectedLayer) {
                    runCatching { requester().requestFocus() }
                }
            }
        }
    }
    // 焦点移到进度条/图标行时收起浮出面板 (聚焦交接的瞬时 NONE 不清除)
    LaunchedEffect(Unit) {
        snapshotFlow { overlay.focusRegion }.collectLatest { region ->
            if (region == TvPlayerFocusRegion.PROGRESS || region == TvPlayerFocusRegion.BOTTOM_ROW) {
                overlay.activePanel = null
            }
            // 兜底: 焦点若离开了进度条, 拖拽预览就地取消 (不 seek). 按键路由已经把拖拽态里的
            // 上下键吞掉了, 正常走不到这里 —— 但焦点也可能被非按键路径挪走 (自动连播换集、
            // 侧边 sheet 被别处打开), 那时圆点脱离播放位置留在屏上, 后续显示全是错的.
            //
            // NONE 不算离开: 它是焦点交接的瞬时值, 也是 showControls() 写下的初值 ——
            // 连按进拖拽态正是"先置 NONE 再设预览", 按 NONE 取消会当场把刚进的态撤销掉
            if (region != TvPlayerFocusRegion.PROGRESS && region != TvPlayerFocusRegion.NONE) {
                progressSliderState.cancelPreview()
            }
        }
    }
    // 长按确认键倍速播放 (与手机端长按画面同一功能, 见 PlayerFastSkipState).
    //
    // 倍数复用设置里的"长按倍速倍率" (默认 2.5x —— 3 倍弹幕会跳, 见上游 #1524), 不另开一个开关.
    //
    // collectLatest: 松手前又按一次会重启计时, 而旧一轮的 finally 负责把倍速还原回去,
    // 不会把"倍速中的倍速"记成原速
    LaunchedEffect(Unit) {
        snapshotFlow { confirmKeyHoldTick }.collectLatest { tick ->
            if (tick == 0) return@collectLatest // 初值, 还没按过
            delay(TV_FAST_FORWARD_HOLD_MILLIS)
            if (!confirmKeyHeld) return@collectLatest // 短按, 已经在抬起时当切换播放处理了
            val playbackSpeed = vm.player.features[PlaybackSpeed] ?: return@collectLatest
            val originalSpeed = playbackSpeed.value
            fastForwarding = true
            try {
                playbackSpeed.set(vm.videoScaffoldConfig.fastForwardSpeed)
                // 挂到松手为止. 也监视层级: 松手事件有可能压根收不到 (期间焦点被别处抢走,
                // 比如自动连播换集), 那样倍速会一直挂着不还原
                snapshotFlow { confirmKeyHeld && overlay.layer == TvPlayerLayer.HIDDEN }.first { !it }
            } finally {
                // 取消 (离屏/重启一轮) 也要还原, 所以放在 finally 而不是挂起之后
                playbackSpeed.set(originalSpeed)
                fastForwarding = false
                // 这一发有可能没落到播放器上 (见下方补发协程), 挂个待办.
                // isPlaying 一并记下来: false 就是那条会丢还原的路, 与下面那条补发日志的时间差
                // 即"丢了多久才被补上" —— 判断有没有真的复现到那条路, 只能靠这一对日志
                logger.info {
                    "Fast forward ended, restoring speed to $originalSpeed " +
                            "(isPlaying=${vm.player.state.value.isPlaying})"
                }
                pendingSpeedRestore = originalSpeed
            }
        }
    }
    // 倍速还原补发: 等播放器真的在播了, 再把原速发一遍.
    //
    // **mediamp 0.3.0 的 `PlaybackSpeed.set` 只在 `isPlaying` 为真时才下发速率**
    // (AbstractMediampPlayer.machinePlaybackSpeed): `flow.value` 与 `desiredRate` 无条件更新,
    // 但 `setRateImpl` 外面套着 `if (_state.value.isPlaying)`. 而两处"恢复播放时补发速率"的分支
    // 又都写着 `desiredRate != 1f` 才补 —— **1f 被当成了"不用管"**.
    //
    // 于是"长按倍速 → 撞上缓冲 → 松手"这条路上还原会整个丢掉: 松手那一刻 isPlaying 是 false
    // (正在缓冲), set(1f) 只改了显示与 desiredRate; 缓冲结束恢复播放时, 补发又因为 desiredRate
    // 正好是 1f 而跳过. 结果是播放器一直停在长按时的 2.5x, 而界面上倍速按钮显示的是原速
    // (它只在 speed == 1.0f 时不带数字) —— 用户手动去滑块上改一次才能解开.
    //
    // 补发放在独立协程而不是上面的 finally 里: finally 不能挂起, 而那一轮随时可能被取消.
    // 等 `!fastForwarding` 是防止补发跑去覆盖用户新起的一轮长按.
    LaunchedEffect(Unit) {
        snapshotFlow { pendingSpeedRestore }.collectLatest { target ->
            if (target == null) return@collectLatest
            val playbackSpeed = vm.player.features[PlaybackSpeed] ?: return@collectLatest
            combine(vm.player.state, snapshotFlow { fastForwarding }) { state, forwarding ->
                state.isPlaying && !forwarding
            }.first { it }
            logger.info { "Re-applying playback speed $target after fast forward" }
            playbackSpeed.set(target)
            pendingSpeedRestore = null
        }
    }
    // 组合销毁时把还没补发出去的那一次交接给会话级 scope.
    //
    // 上面那个补发协程与 pendingSpeedRestore 都只活到本页组合为止, 而播放会话由
    // RetainedPlaybackSessionHolder 持有, 活得比播放页久 (退出播放页只是暂停, 下次从侧边栏
    // 回来接着播). 于是"松手时正在缓冲 → 缓冲还没结束就按返回退出"这条路上待办会随组合一起丢掉,
    // 且再没有人会纠正它: desiredRate 此刻正好已经被写成 1f, 而 mediamp 两处"恢复播放时补发"
    // 的分支都要求 desiredRate != 1f. 结果是回到播放页继续看时画面声音仍是长按时的倍速.
    //
    // 交接后不再看 fastForwarding: 页面都没了, 不可能再有新一轮长按来跟它抢.
    DisposableEffect(vm) {
        onDispose {
            val target = pendingSpeedRestore ?: return@onDispose
            val playbackSpeed = vm.player.features[PlaybackSpeed] ?: return@onDispose
            // Dispatchers.Main 不能省: backgroundScope 跑在 Default 上, 而 PlaybackSpeed.set
            // 里有 checkMainThread (mediamp 是先写 valueFlow 再碰播放器, 抛了就变成假倍速)
            vm.backgroundScope.launch(Dispatchers.Main) {
                vm.player.state.first { it.isPlaying }
                logger.info { "Re-applying playback speed $target after leaving player page" }
                playbackSpeed.set(target)
            }
        }
    }
    // 自动隐藏 (Prime 行为): 播放中且无面板/侧 sheet/下拉/输入态, 5 秒无按键收起.
    // 用 snapshotFlow 而非 LaunchedEffect key, 避免每次按键使本组合作用域失效
    //
    // 本页不在前台 (从面板点开了别的页面, 如相关推荐的条目详情页) 期间一律不隐藏:
    // 隐藏会把控制层整棵子树移除, 回来时焦点已经无处可还 —— 组件留着, 焦点才回得去
    // (回来后由下面的焦点看门狗送回面板条目/进度条).
    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(
                overlay.layer, overlay.interactionTick, overlay.activePanel,
                overlay.openPopupCount, overlay.danmakuInputExpanded, anySheetVisible,
                overlay.replyingComment != null, pageResumed,
            )
        }.collectLatest {
            if (overlay.layer != TvPlayerLayer.CONTROLS) return@collectLatest
            if (overlay.activePanel != null || overlay.openPopupCount > 0 ||
                overlay.danmakuInputExpanded || anySheetVisible ||
                overlay.replyingComment != null || !pageResumed
            ) {
                return@collectLatest
            }
            delay(TV_PLAYER_AUTO_HIDE_MILLIS)
            // 暂停时不自动隐藏 (Prime 行为); 到点时再查一次播放意图.
            // 用 playWhenReady 而非严格 isPlaying: 卡在缓冲上不该把控制层永久钉住
            if (vm.player.state.value.playWhenReady) {
                overlay.hideAll()
            }
        }
    }

    AniTheme(darkModeOverride = DarkMode.DARK) {
        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .onPreviewKeyEvent(onRootKeyEvent)
                .focusRequester(rootFocusRequester)
                .onFocusChanged { rootFocused = it.isFocused }
                .focusable(), // 根节点可聚焦: 纯视频态持焦收按键
        ) {
            // 视频面: 独立稳定槽位, 覆盖层任何变化不触碰
            VideoPlayer(
                vm.player,
                Modifier.matchParentSize(),
            )

            // 弹幕层
            AniAnimatedVisibility(page.danmakuEnabled, Modifier.matchParentSize()) {
                Box(Modifier.matchParentSize()) {
                    PlayerDanmakuHost(vm.player, danmakuHostState, vm.uiDanmakuEventFlow)
                }
            }

            // 缓冲/加载指示 (居中悬浮).
            //
            // 快进退反馈也在画面正中, 而快进必然引发一次重新缓冲 —— 不让路的话每次按左右键
            // 圆弧箭头都和"正在缓冲"叠在一起. 快进反馈优先: 它在场期间把缓冲指示按成隐形,
            // 走完 (约 0.6 秒) 之后若还在缓冲自然露出来.
            //
            // 隐形而不是从组合里摘掉: TvPlayerLoadingLayer 的 collectAsStateWithLifecycle
            // 初值是 VideoLoadingState.Initial, 重新挂载会有一帧非 Succeed 状态, 闪一下
            // "正在自动选择"; 里面"缓冲太久"的 15 秒计时也会跟着重置.
            //
            // 状态读在 graphicsLayer 的 lambda 里: 直接读会让整个播放器界面随反馈的出现/消失重组
            TvPlayerLoadingLayer(
                vm,
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = if (seekFlash.visible) 0f else 1f },
            )

            // 播放/暂停切换反馈: 画面中央浮现对应图标并渐隐 (监听播放器状态流,
            // 无论切换来自确认键/控制按钮/面板操作都有反馈)
            TvPauseFlash(vm.player, Modifier.align(Alignment.Center))

            // 快进退反馈: 纯视频态左右键不唤出控制层, 这是唯一的反馈
            TvSeekFlash(seekFlash, Modifier.align(Alignment.Center))

            // 长按倍速指示: 按住期间常显 (不是闪一下就走的反馈, 用户需要知道"现在还在倍速").
            // 状态读在 graphicsLayer 的 lambda 里: 直接读会让整个播放器界面随它出现/消失重组
            TvFastForwardIndicator(
                speed = vm.videoScaffoldConfig.fastForwardSpeed,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer { alpha = if (fastForwarding) 1f else 0f },
            )

            // 控制层 (L1 + 浮出面板 L2).
            //
            // 提示按钮在场时本层也留着: 它是胶囊行的一员, 位置由那一行的布局直接给出 (不是浮在
            // 屏幕上按实测坐标跟随 —— 那样在图标行收起的逐帧动画里总慢一拍). 控制层自己的显隐
            // 照旧, 只是"收起"变成本层内部把除按钮之外的一切淡到透明 (chromeVisible), 布局仍在,
            // 于是屏幕上只剩那颗按钮, 且纹丝不动
            AniAnimatedVisibility(
                visible = overlay.layer == TvPlayerLayer.CONTROLS || skipTipVisible,
                modifier = Modifier.matchParentSize(),
            ) {
                TvPlayerControlsOverlay(
                    chromeVisible = overlay.layer == TvPlayerLayer.CONTROLS,
                    overlay = overlay,
                    vm = vm,
                    page = page,
                    danmakuEditorState = danmakuEditorState,
                    progressSliderState = progressSliderState,
                    framePreview = framePreview,
                    progressRowFocusRequester = progressRowFocusRequester,
                    bottomRowFocusRequester = bottomRowFocusRequester,
                    episodeStripFocusRequester = episodeStripFocusRequester,
                    sheetsController = sheetsController,
                    // OP/ED 提示按钮: 胶囊行最右的一颗 (取舍见 TvSkipOpEdTipButton 的 KDoc)
                    pillsRowTrailing = {
                        val shownTip = skipTip ?: lastSkipTip
                        AniAnimatedVisibility(visible = skipTipVisible) {
                            if (shownTip == null) return@AniAnimatedVisibility
                            TvSkipOpEdTipButton(
                                tip = shownTip,
                                onClick = {
                                    if (shownTip.canCancel) {
                                        vm.playerSkipOpEdState.cancelSkipOpEd()
                                    } else {
                                        vm.playerSkipOpEdState.skipOpEd()
                                    }
                                },
                                modifier = Modifier
                                    // 吞掉"按钮出现时手上那次按住"的余波, 直到看见新的一次按下为止 ——
                                    // 与详情页"长按开始观看 -> 跳到选集卡片"是同一个局面: 本按钮一出现
                                    // 就抢焦点, 用户手还按着 (长按倍速), 那次按住剩下的连发与 KeyUp 就
                                    // 全落在它身上, 松手即"取消跳过". 判据与原生控件同一条 (repeatCount
                                    // == 0 才算新手势), 不依赖根路由的记账: 根路由并非每条分支都消费
                                    // 确认键 (控制层那条 `if (!isKeyDown) return false` 会把所有 KeyUp
                                    // 放行给持焦控件), 少一条就漏
                                    .consumeHeldConfirmKey()
                                    .focusRequester(skipTipFocusRequester)
                                    .onFocusChanged { skipTipFocused = it.hasFocus },
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // 播放器统计悬浮层 (三个点菜单开关)
            if (overlay.showPlayerStats) {
                val playerStats by rememberPlayerStatsState(vm.player)
                PlayerStatsOverlay(playerStats)
            }

            // 详情页覆盖层 (L3): 视频画面作背景. 淡入淡出 —— 尤其顶部按上键回选集条时,
            // 瞬时移除会闪一下; 淡出放慢一档 (默认过渡太快, 观感仍像闪切)
            AniAnimatedVisibility(
                visible = overlay.layer == TvPlayerLayer.DETAILS,
                modifier = Modifier.matchParentSize(),
                enter = fadeIn(tween(TV_DETAILS_FADE_IN_MS)),
                exit = fadeOut(tween(TV_DETAILS_FADE_OUT_MS)),
            ) {
                TvPlayerDetailsOverlay(
                    vm = vm,
                    page = page,
                    onClose = { overlay.hideAll() },
                    onExitUpToStrip = { overlay.returnToEpisodeStrip() },
                    modifier = Modifier.matchParentSize(),
                )
            }

            // 右侧侧边 sheets (数据源/选集/弹幕设置), 复用现有实现
            Box(Modifier.matchParentSize()) {
                TvPlayerSideSheets(vm, sheetsController)
            }

            // 评论回复弹窗 (TV 专用大弹窗): 同窗口内的全屏层, 不用真 Dialog ——
            // 那是独立窗口, 上面那个唯一按键路由收不到它的按键. 控制层与面板留在下面不动,
            // 关掉后焦点直接还给刚点开的那条评论
            overlay.replyingComment?.let { target ->
                TvCommentReplyDialog(
                    target = target,
                    editorState = vm.commentEditorState,
                    onSent = { overlay.dismissReply() },
                    // 左右键翻相邻评论: 由评论面板消费 (行列表在它手上), 见 overlay.replyNavRequest
                    onNavigate = { overlay.navigateReply(it) },
                    modifier = Modifier.matchParentSize(),
                )
            }
        }
    }
}

/** 缓冲/加载指示: 状态收集限制在本组合内, 不牵连整屏. */
@Composable
private fun TvPlayerLoadingLayer(
    vm: EpisodeViewModel,
    modifier: Modifier = Modifier,
) {
    val videoLoadingStateFlow = remember(vm) { vm.videoStatisticsFlow.map { it.videoLoadingState } }
    val videoLoadingState by videoLoadingStateFlow.collectAsStateWithLifecycle(VideoLoadingState.Initial)
    Box(modifier) {
        EpisodeVideoLoadingIndicator(
            vm.player,
            videoLoadingState,
            optimizeForFullscreen = true,
        )
    }
}

/**
 * 中央反馈的浮现渐隐容器 (暂停/快进退共用): [content] 画两遍 —— 先黑色偏移一档作投影,
 * 再白色本体; 无底衬, 亮画面上靠投影保证可见.
 *
 * [flashKey] 每次自增都重启动画: 快速连按时从满透明度重新开始, 而不是接着上一次淡下去.
 */
@Composable
private fun TvCenterFlash(
    flashKey: Int,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (color: Color, modifier: Modifier) -> Unit,
) {
    key(flashKey) {
        val alpha = remember { Animatable(1f) }
        LaunchedEffect(Unit) {
            alpha.animateTo(
                0f,
                tween(TV_PAUSE_FLASH_DURATION_MS, delayMillis = TV_PAUSE_FLASH_HOLD_MS),
            )
            onFinished()
        }
        Box(modifier.graphicsLayer { this.alpha = alpha.value }) {
            content(Color.Black.copy(alpha = TV_PAUSE_FLASH_SHADOW_ALPHA), Modifier.offset(x = 1.dp, y = 1.5.dp))
            content(Color.White, Modifier)
        }
    }
}

/**
 * 暂停反馈: 每次 播放->暂停 切换, 中央浮现暂停图标后渐隐. 恢复播放无反馈
 * (画面动起来本身即反馈).
 *
 * 直接监听播放器状态流而非按键: 确认键/图标行按钮/自动暂停等任何触发方式都有反馈.
 * 缓冲等中间态不算切换 (播放中卡缓冲再恢复不闪); 进屏的首个状态不闪.
 */
@Composable
private fun TvPauseFlash(
    player: MediampPlayer,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    var flashKey by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) {
        var prev: Boolean? = null
        // 只看播放意图的翻转. v1 时这里必须显式排掉 PAUSED_BUFFERING 等中间态 (否则缓冲一下就闪一次
        // 暂停图标); v2 的 playWhenReady 本身就只在用户/程序真的暂停时才变 false, 天然没有这个噪声.
        player.state.collect { state ->
            val playing = state.playWhenReady
            if (prev == true && !playing) {
                visible = true
                flashKey++
            }
            prev = playing
        }
    }
    if (visible) {
        TvCenterFlash(flashKey, onFinished = { visible = false }, modifier) { color, mod ->
            TvPauseBars(color, mod)
        }
    }
}

/**
 * 遥控器左右键快进退的中央反馈状态.
 *
 * 纯视频态下左右键**不唤出控制层** (快进退不该把画面压掉一半), 于是反馈只剩这一个居中图标,
 * 必须由按键路由显式触发 —— 快进退不改变播放器状态流, 没法像 [TvPauseFlash] 那样自己监听.
 */
@Stable
private class TvSeekFlashState {
    /** 每次按键自增, 用作 [TvCenterFlash] 的重启键. */
    var tick: Int by mutableIntStateOf(0)
        private set

    var forward: Boolean by mutableStateOf(true)
        private set

    var visible: Boolean by mutableStateOf(false)
        private set

    fun flash(forward: Boolean) {
        this.forward = forward
        visible = true
        tick++
    }

    fun onFinished() {
        visible = false
    }

    /** 立即收起 (连按升级成拖拽预览态时: 中央箭头不该和暂停提示叠在一起). */
    fun cancel() {
        visible = false
    }
}

/**
 * 快进退反馈: 中央浮现"圆弧箭头 + 秒数"后渐隐, 与 [TvPauseFlash] 同款 (同投影/同时长).
 *
 * 图形与图标行的"跳过 OP/ED" (AniIcons.Forward85 等) 同族, 只是秒数不同 —— 图标里的 5
 * 对应 [TV_PLAYER_SEEK_STEP_MILLIS], 改步长时记得一起换 (Material 现成的只有 5/10/30 三档).
 */
@Composable
private fun TvSeekFlash(
    state: TvSeekFlashState,
    modifier: Modifier = Modifier,
) {
    if (!state.visible) return
    TvCenterFlash(state.tick, onFinished = { state.onFinished() }, modifier) { color, mod ->
        Icon(
            if (state.forward) Icons.Rounded.Forward5 else Icons.Rounded.Replay5,
            null,
            mod.size(TV_SEEK_FLASH_ICON_SIZE),
            tint = color,
        )
    }
}

/**
 * 长按倍速指示 (按住确认键期间常显): 深色药丸 + 双箭头 + 倍数.
 *
 * 与暂停/快进退那两个中央反馈不同, 这个**不渐隐** —— 它表示的是一个持续状态, 不是一次动作.
 * 因此也不用 [TvCenterFlash] 那套投影, 改用药丸底: 常显期间画面还在动, 白字加投影会糊.
 */
@Composable
private fun TvFastForwardIndicator(
    speed: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.FastForward,
            null,
            Modifier.size(TV_FAST_FORWARD_ICON_SIZE),
            tint = Color.White,
        )
        Text(
            "${speed.formatSpeedValue()}x",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** 双竖杠暂停图形: Material Pause 图标太矮胖, 对照 Prime 实测 (4K/640dpi 下 18x112px, 胶囊端头) 自绘. */
@Composable
private fun TvPauseBars(color: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(TV_PAUSE_FLASH_BAR_GAP)) {
        repeat(2) {
            Box(
                Modifier
                    .size(TV_PAUSE_FLASH_BAR_WIDTH, TV_PAUSE_FLASH_BAR_HEIGHT)
                    .background(color, CircleShape),
            )
        }
    }
}

/** 暂停反馈双竖杠尺寸 (Prime 实测换算). */
private val TV_PAUSE_FLASH_BAR_WIDTH = 4.5.dp
private val TV_PAUSE_FLASH_BAR_HEIGHT = 28.dp
private val TV_PAUSE_FLASH_BAR_GAP = 7.5.dp

/**
 * 快进退图标尺寸: 与暂停竖杠的**外接圆**对齐, 两个提示在画面中央占一样大的一团.
 *
 * 竖杠组包围盒 16.5 x 28dp (两杠分列两角), 外接圆直径即对角线 32.5dp; 圆弧箭头是圆形图形,
 * 占满 Material 24dp 网格里 20dp 的活动区, 外接圆直径 = 尺寸 x 20/24. 于是 40dp 给出
 * 33.3dp, 与 32.5dp 差 3% —— 不能按"图形高度"凑 (那样得 34dp, 外接圆就小了两成).
 */
private val TV_SEEK_FLASH_ICON_SIZE = 40.dp

/**
 * 确认键按住多久算长按 (毫秒). 与手机端 [detectLongPressGesture] 的 500ms 对齐 ——
 * 两端"长按"的手感应当一致.
 */
private const val TV_FAST_FORWARD_HOLD_MILLIS = 500L

/** 长按倍速指示里的双箭头尺寸. */
private val TV_FAST_FORWARD_ICON_SIZE = 26.dp

/** 暂停反馈的渐隐时长与起始停留 (毫秒). */
private const val TV_PAUSE_FLASH_DURATION_MS = 500
private const val TV_PAUSE_FLASH_HOLD_MS = 120

/** 暂停反馈投影不透明度. */
private const val TV_PAUSE_FLASH_SHADOW_ALPHA = 0.55f

private val logger = logger("TvEpisodeScreen")
