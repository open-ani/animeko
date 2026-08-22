/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlin.time.Duration

/**
 * 调试用的 playground: 动画 + 设计稿上的三个开关 + 一条播放控制.
 *
 * 三个开关和最终要放进「设置 → 观看偏好 → 高级设置」的是同一套语义, 但这里是独立的、只影响这个动画的
 * 演示开关 —— 真接进设置页时它们会换成读写用户设置的版本.
 */
@Composable
fun SelectorWorkflowPlayground(
    modifier: Modifier = Modifier,
    viewModel: SelectorWorkflowViewModel = remember { SelectorWorkflowViewModel() },
) {
    var eager by remember { mutableStateOf(false) }
    var priorityEnabled by remember { mutableStateOf(false) }
    var prioritySeconds by remember { mutableStateOf(DEFAULT_PRIORITY_SECONDS) }
    var resolveDemo by remember { mutableStateOf(false) }
    var budgetSeconds by remember { mutableStateOf(DEFAULT_BUDGET_SECONDS) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            SelectorWorkflowAnimation(viewModel, Modifier.padding(12.dp))
        }

        TransportBar(viewModel)

        // ---- 设计稿上的三个开关 ----

        WorkflowSwitchRow(
            title = "抢先选源",
            description = "每个数据源一返回，就为它自己的结果起一个遍历，不等其余数据源搜完",
            checked = eager,
            onCheckedChange = {
                if (viewModel.setEagerSelect(it)) eager = it
            },
        )

        WorkflowSwitchRow(
            title = "最大等待高优先级源的时长",
            description = "这段时间内谁都不选，只等高优先级源。它带着候选回来就直接用它的\n" +
                    "打开后连演「等到了」与「等超时」两条路径",
            checked = priorityEnabled,
            onCheckedChange = {
                if (viewModel.setPriorityWait(it, prioritySeconds)) priorityEnabled = it
            },
            seconds = prioritySeconds,
            onSecondsChange = {
                if (viewModel.setPriorityWaitSeconds(it)) prioritySeconds = it
            },
            secondsEnabled = priorityEnabled,
        )

        WorkflowSwitchRow(
            title = "拦截播放链接的最大等待时长",
            description = "页面打开后最多等这么久。超时仍没匹配到播放链接，本次解析判定失败\n" +
                    "打开后一轮里连演成功、超时、换下一个候选再成功",
            checked = resolveDemo,
            onCheckedChange = {
                if (viewModel.setResolveDemo(it)) resolveDemo = it
            },
            seconds = budgetSeconds,
            onSecondsChange = {
                if (viewModel.setInterceptBudgetSeconds(it)) budgetSeconds = it
            },
        )
    }
}

/**
 * 一行设置. 与设计稿一致: 左边标题 + 说明, 右边可选的秒数输入框, 最右边开关.
 */
@Composable
private fun WorkflowSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    seconds: Int? = null,
    onSecondsChange: (Int) -> Unit = {},
    secondsEnabled: Boolean = true,
) {
    Surface(
        modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(description, style = MaterialTheme.typography.bodySmall) },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (seconds != null) {
                        SecondsField(seconds, onSecondsChange, enabled = secondsEnabled)
                    }
                    Switch(checked, onCheckedChange)
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

/**
 * 秒数输入框. 输进非法值时保留输入内容但不往下提交, 免得动画一边打字一边重编译.
 */
@Composable
private fun SecondsField(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var text by remember(seconds) { mutableStateOf(seconds.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.takeIf { it > 0 }?.let(onSecondsChange)
        },
        modifier = modifier.widthIn(min = 88.dp),
        enabled = enabled,
        singleLine = true,
        suffix = { Text("秒") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

/**
 * playground 专用: 播放 / 暂停 / 重来 + 进度条 + 当前这一拍的名字.
 * 真接进设置页时不会有这一条.
 */
@Composable
private fun TransportBar(
    viewModel: SelectorWorkflowViewModel,
    modifier: Modifier = Modifier,
) {
    val player = viewModel.player
    val state = viewModel.state
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilledTonalIconButton({ player.isPlaying = !player.isPlaying }) {
            Icon(
                if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (player.isPlaying) "暂停" else "播放",
            )
        }
        FilledTonalIconButton({ player.restart() }) {
            Icon(Icons.Default.Replay, contentDescription = "重来")
        }
        Slider(
            value = state.progress,
            onValueChange = { player.seekToFraction(it) },
            modifier = Modifier.weight(1f),
        )
        Text(
            "${state.time.formatSeconds()} / ${state.duration.formatSeconds()}",
            style = MaterialTheme.typography.labelMedium,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            state.phase,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun Duration.formatSeconds(): String {
    val tenths = inWholeMilliseconds / 100
    return "${tenths / 10}.${tenths % 10}s"
}

private const val DEFAULT_PRIORITY_SECONDS = 5
private const val DEFAULT_BUDGET_SECONDS = 8

@Preview
@Composable
private fun PreviewSelectorWorkflowPlayground() {
    // 静态预览不会跑 LaunchedEffect, 播放位置停在 0 会是一张空画. 先拨到一个有内容的时刻.
    val viewModel = remember {
        SelectorWorkflowViewModel().apply { player.seekToFraction(PREVIEW_FRACTION) }
    }
    Surface(color = MaterialTheme.colorScheme.surface) {
        SelectorWorkflowPlayground(Modifier.padding(16.dp), viewModel)
    }
}

private const val PREVIEW_FRACTION = 0.42f

@Preview
@Composable
private fun PreviewSelectorWorkflowFrames() {
    // 定格看几个关键时刻, 不用等动画跑到那里
    val config = remember {
        SelectorWorkflowPresets.threeSources(
            mode = SelectMode.Eager,
            resolveOutcomes = listOf(ResolveOutcome.Hit, ResolveOutcome.Timeout, ResolveOutcome.HitAfterFallback),
        )
    }
    val timeline = remember(config) { config.buildTimeline() }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.08f, 0.2f, 0.5f, 0.72f, 0.95f).forEach { fraction ->
                SelectorWorkflowAnimation(
                    state = timeline.sampleAt(timeline.duration * fraction.toDouble()),
                    config = config,
                )
            }
        }
    }
}
