/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.player.analytics.PlayFlowDebugEvent
import me.him188.ani.app.domain.player.analytics.PlayFlowDebugLog
import me.him188.ani.app.domain.player.analytics.PlayFlowDebugSession
import me.him188.ani.app.domain.player.analytics.PlayFlowStep
import me.him188.ani.app.tools.formatDateTime
import me.him188.ani.app.ui.foundation.setClipEntryText
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.play_flow_debug_attempt
import me.him188.ani.app.ui.lang.play_flow_debug_back
import me.him188.ani.app.ui.lang.play_flow_debug_close
import me.him188.ani.app.ui.lang.play_flow_debug_copy
import me.him188.ani.app.ui.lang.play_flow_debug_empty
import me.him188.ani.app.ui.lang.play_flow_debug_follow
import me.him188.ani.app.ui.lang.play_flow_debug_minimize
import me.him188.ani.app.ui.lang.play_flow_debug_restore
import me.him188.ani.app.ui.lang.play_flow_debug_session_summary
import me.him188.ani.app.ui.lang.play_flow_debug_tab_history
import me.him188.ani.app.ui.lang.play_flow_debug_tab_live
import me.him188.ani.app.ui.lang.play_flow_debug_title
import org.jetbrains.compose.resources.stringResource

object PlayFlowDebugWindowTestTags {
    const val WINDOW = "playFlowDebugWindow"
    const val TITLE_BAR = "playFlowDebugTitleBar"
    const val TAB_LIVE = "playFlowDebugTabLive"
    const val TAB_HISTORY = "playFlowDebugTabHistory"
    const val TIMELINE = "playFlowDebugTimeline"
    const val FOLLOW_BUTTON = "playFlowDebugFollow"
    const val MINIMIZE_BUTTON = "playFlowDebugMinimize"
    const val MINIMIZED_BUTTON = "playFlowDebugMinimized"
    const val COPY_BUTTON = "playFlowDebugCopy"

    fun event(seq: Long) = "playFlowDebugEvent_$seq"
    fun session(playSessionId: String) = "playFlowDebugSession_$playSessionId"
}

/**
 * 可拖动的 `play_flow` 调试悬浮窗. 放在页面之上 (`AniAppContent` 根部), 跟随页面导航一直显示, 直到 [onClose].
 *
 * 大小按可用空间收缩, 初始在右上角; 拖动标题栏移动, 始终留在可见范围内.
 * 最小化后只剩一个同样可拖动的小按钮 (带当前会话的事件数), 点击展开; 期间页签与滚动位置保留.
 */
@Composable
fun PlayFlowDebugFloatingWindow(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    log: PlayFlowDebugLog = PlayFlowDebugLog.Global,
) {
    var minimized by rememberSaveable { mutableStateOf(false) }
    val windowStateHolder = rememberSaveableStateHolder()

    BoxWithConstraints(modifier.fillMaxSize().padding(8.dp)) {
        val width = if (minimized) MINIMIZED_SIZE else minOf(WINDOW_WIDTH, maxWidth)
        val height = if (minimized) MINIMIZED_SIZE else minOf(WINDOW_HEIGHT, maxHeight)
        // 右上角为原点, 可拖动范围: 向左最多 maxWidth - width, 向下最多 maxHeight - height
        val density = LocalDensity.current
        val slackX = with(density) { (maxWidth - width).roundToPx() }
        val slackY = with(density) { (maxHeight - height).roundToPx() }
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        offset = offset.clampTo(slackX, slackY)
        val dragModifier = Modifier.pointerInput(slackX, slackY) {
            detectDragGestures { change, dragAmount ->
                change.consume()
                offset = (offset + dragAmount.round()).clampTo(slackX, slackY)
            }
        }
        val placement = Modifier
            .align(Alignment.TopEnd)
            .offset { offset }
            .size(width, height)

        if (minimized) {
            val sessions by log.sessions.collectAsStateWithLifecycle()
            MinimizedButton(
                eventCount = sessions.lastOrNull()?.events?.size ?: 0,
                onClick = { minimized = false },
                modifier = placement.then(dragModifier),
            )
        } else {
            windowStateHolder.SaveableStateProvider("window") {
                PlayFlowDebugWindow(
                    log = log,
                    onClose = onClose,
                    onMinimize = { minimized = true },
                    dragModifier = dragModifier,
                    modifier = placement,
                )
            }
        }
    }
}

@Composable
private fun MinimizedButton(eventCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalIconButton(
        onClick,
        modifier.testTag(PlayFlowDebugWindowTestTags.MINIMIZED_BUTTON),
    ) {
        BadgedBox(
            badge = {
                if (eventCount > 0) {
                    Badge { Text(eventCount.toString()) }
                }
            },
        ) {
            Icon(Icons.Outlined.Analytics, stringResource(Lang.play_flow_debug_restore))
        }
    }
}

private val WINDOW_WIDTH = 380.dp
private val WINDOW_HEIGHT = 560.dp
private val MINIMIZED_SIZE = 56.dp

private fun IntOffset.clampTo(slackX: Int, slackY: Int) = IntOffset(
    x.coerceIn(-slackX, 0),
    y.coerceIn(0, slackY),
)

/**
 * 悬浮窗内容: 标题栏, 「实时」与「历史」两个页签.
 *
 * 实时页显示最新播放会话的时间线并自动跟随新事件; 历史页列出本次运行内的播放会话, 点开后查看其时间线.
 *
 * @param onMinimize 标题栏的最小化按钮; 为 `null` 时不显示.
 * @param dragModifier 加在标题栏上, 由宿主实现拖动.
 */
@Composable
fun PlayFlowDebugWindow(
    log: PlayFlowDebugLog,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onMinimize: (() -> Unit)? = null,
    dragModifier: Modifier = Modifier,
) {
    val sessions by log.sessions.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var openedHistorySessionId by rememberSaveable { mutableStateOf<String?>(null) }

    // 正在查看的会话: 实时页是最新会话, 历史页是打开的那条
    val viewedSession = when (tab) {
        0 -> sessions.lastOrNull()
        else -> openedHistorySessionId?.let { id -> sessions.firstOrNull { it.playSessionId == id } }
    }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    Surface(
        modifier.testTag(PlayFlowDebugWindowTestTags.WINDOW),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
    ) {
        Column {
            Row(
                dragModifier
                    .fillMaxWidth()
                    .testTag(PlayFlowDebugWindowTestTags.TITLE_BAR)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Lang.play_flow_debug_title),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                IconButton(
                    onClick = {
                        val session = viewedSession ?: return@IconButton
                        scope.launch { clipboard.setClipEntryText(session.toJsonString()) }
                    },
                    Modifier.testTag(PlayFlowDebugWindowTestTags.COPY_BUTTON),
                    enabled = viewedSession != null,
                ) {
                    Icon(Icons.Rounded.ContentCopy, stringResource(Lang.play_flow_debug_copy))
                }
                if (onMinimize != null) {
                    IconButton(onMinimize, Modifier.testTag(PlayFlowDebugWindowTestTags.MINIMIZE_BUTTON)) {
                        Icon(Icons.Rounded.Remove, stringResource(Lang.play_flow_debug_minimize))
                    }
                }
                IconButton(onClose) {
                    Icon(Icons.Rounded.Close, stringResource(Lang.play_flow_debug_close))
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    modifier = Modifier.testTag(PlayFlowDebugWindowTestTags.TAB_LIVE),
                    text = { Text(stringResource(Lang.play_flow_debug_tab_live)) },
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    modifier = Modifier.testTag(PlayFlowDebugWindowTestTags.TAB_HISTORY),
                    text = { Text(stringResource(Lang.play_flow_debug_tab_history)) },
                )
            }
            when (tab) {
                0 -> LiveTab(viewedSession, Modifier.fillMaxSize())
                else -> HistoryTab(
                    sessions,
                    opened = viewedSession,
                    onOpenSession = { openedHistorySessionId = it },
                    onBack = { openedHistorySessionId = null },
                    Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun LiveTab(session: PlayFlowDebugSession?, modifier: Modifier = Modifier) {
    if (session == null) {
        EmptyHint(modifier)
        return
    }
    Column(modifier) {
        SessionHeader(session, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        HorizontalDivider()
        // 换了播放会话就换一条时间线, 滚动位置从头开始
        key(session.playSessionId) {
            PlayFlowTimeline(session.events, autoFollow = true, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun HistoryTab(
    sessions: List<PlayFlowDebugSession>,
    opened: PlayFlowDebugSession?,
    onOpenSession: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (opened != null) {
        Column(modifier) {
            Row(Modifier.padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(Lang.play_flow_debug_back))
                }
                SessionHeader(opened, Modifier.weight(1f))
            }
            HorizontalDivider()
            key(opened.playSessionId) {
                PlayFlowTimeline(opened.events, autoFollow = false, Modifier.fillMaxSize())
            }
        }
        return
    }
    if (sessions.isEmpty()) {
        EmptyHint(modifier)
        return
    }
    LazyColumn(modifier) {
        // 新的在前
        itemsIndexed(sessions.asReversed(), key = { _, it -> it.playSessionId }) { _, session ->
            SessionHeader(
                session,
                Modifier
                    .fillMaxWidth()
                    .testTag(PlayFlowDebugWindowTestTags.session(session.playSessionId))
                    .clickable { onOpenSession(session.playSessionId) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SessionHeader(session: PlayFlowDebugSession, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            stringResource(Lang.play_flow_debug_session_summary, session.subjectId, session.events.size),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            "${formatDateTime(session.startedAtMillis)}  ${session.playSessionId.take(8)}",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyHint(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            stringResource(Lang.play_flow_debug_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 事件时间线: 旧在上, 新在下, 每次剧集尝试之前插一行分隔.
 *
 * [autoFollow] 时, 列表停在底部就跟随新事件滚动; 用户往上滚动后停止跟随, 显示「跳到最新」按钮, 点击或手动滚回底部恢复跟随.
 */
@Composable
fun PlayFlowTimeline(
    events: List<PlayFlowDebugEvent>,
    autoFollow: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var followTail by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    // 用户滚动 (拖动、滚轮、惯性) 经过嵌套滚动, 结束一段后按停在哪里决定是否继续跟随; 自动跟随的 scrollToItem 不经过这里.
    val userScrollConnection = remember(listState) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                followTail = !listState.canScrollForward
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(events.size, autoFollow) {
        if (autoFollow && followTail && events.isNotEmpty()) {
            listState.scrollToItem(events.lastIndex)
        }
    }

    Box(modifier) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .nestedScroll(userScrollConnection)
                .testTag(PlayFlowDebugWindowTestTags.TIMELINE),
            state = listState,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            itemsIndexed(events, key = { _, it -> it.seq }) { index, event ->
                val previous = events.getOrNull(index - 1)
                if (previous?.episodeAttempt != event.episodeAttempt) {
                    AttemptHeader(event)
                }
                TimelineRow(event, isFirst = index == 0, isLast = index == events.lastIndex)
            }
        }
        if (autoFollow && !followTail && events.isNotEmpty()) {
            FilledTonalButton(
                onClick = {
                    followTail = true
                    scope.launch { listState.scrollToItem(events.lastIndex) }
                },
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .testTag(PlayFlowDebugWindowTestTags.FOLLOW_BUTTON),
            ) {
                Text(stringResource(Lang.play_flow_debug_follow))
            }
        }
    }
}

@Composable
private fun AttemptHeader(event: PlayFlowDebugEvent) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val attempt = event.episodeAttempt
        val episodeId = event.episodeId
        HorizontalDivider(Modifier.weight(1f))
        Text(
            if (attempt != null && episodeId != null) {
                stringResource(Lang.play_flow_debug_attempt, attempt, episodeId)
            } else {
                "—"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun TimelineRow(event: PlayFlowDebugEvent, isFirst: Boolean, isLast: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag(PlayFlowDebugWindowTestTags.event(event.seq))
            .padding(horizontal = 16.dp),
    ) {
        TimelineRail(color = event.dotColor(), lineAbove = !isFirst, lineBelow = !isLast)
        Column(Modifier.weight(1f).padding(start = 12.dp, top = 2.dp, bottom = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MonoLabel("#${event.seq}", MaterialTheme.typography.labelSmall)
                MonoLabel("+${event.tMs} ms", MaterialTheme.typography.labelSmall)
                Text(
                    event.step,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            if (event.details.isNotEmpty()) {
                MonoLabel(
                    event.details.entries.joinToString("  ") { (key, value) -> "$key=$value" },
                    MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

/**
 * 等宽、弱化颜色的辅助文字.
 */
@Composable
private fun MonoLabel(text: String, style: TextStyle) {
    Text(
        text,
        style = style.copy(fontFamily = FontFamily.Monospace),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimelineRail(color: Color, lineAbove: Boolean, lineBelow: Boolean) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.width(12.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(2.dp).height(6.dp).background(if (lineAbove) lineColor else Color.Transparent))
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Box(Modifier.width(2.dp).weight(1f).background(if (lineBelow) lineColor else Color.Transparent))
    }
}

/**
 * 结果好坏决定圆点颜色: 失败类红色, 成功类主色, 其他中性.
 */
@Composable
private fun PlayFlowDebugEvent.dotColor(): Color {
    val outcome = details["outcome"] as? String
    return when {
        step in FAILED_STEPS || outcome in FAILED_OUTCOMES -> MaterialTheme.colorScheme.error
        step in GOOD_STEPS || outcome in GOOD_OUTCOMES -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
}

private val FAILED_STEPS = setOf(PlayFlowStep.PLAY_ERROR, PlayFlowStep.TRUNCATED, PlayFlowStep.SOURCE_FAILED).map { it.value }.toSet()
private val GOOD_STEPS = setOf(PlayFlowStep.PLAYBACK_STARTED.value)
private val FAILED_OUTCOMES =
    setOf("failed", "cancelled", "incomplete", "no_media", "timeout", "no_more_sources", "captcha_required", "rate_limited", "slow")
private val GOOD_OUTCOMES = setOf("ok", "selected", "complete", "switched")
