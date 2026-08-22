/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.utils.selectorworkflow.SelectorWorkflowAnimation
import me.him188.ani.utils.selectorworkflow.SelectorWorkflowViewModel
import kotlin.time.Duration

/**
 * 「搜源 · 选源 · 解析」示意动画在设置页里的联动.
 *
 * 规则是: **动哪个设置项, 就只演它对应的那一段**, 同时把另一段关掉 ——
 * 一次只演一件事, 注意力才落得到刚动的那个选项上. 每次都从头播放, 免得用户看到的是半截.
 *
 * 对应关系:
 * - 「快速选择在线数据源」开关 -> 抢先选源. 它是常驻状态, 不是"某一段", 所以切换它只是同步状态,
 *   并把另外两段都关掉 (回到最朴素的三步流程);
 * - 「最长等待时间」-> 高优先级等待那一段;
 * - 「视频链接解析超时」-> 第三步的拦截超时那一段.
 */
@Stable
class MediaSelectorWorkflowDemoState(
    initialEagerSelect: Boolean,
) {
    val viewModel = SelectorWorkflowViewModel()

    /** 「快速选择在线数据源」的当前状态. 它一直生效, 不随其它设置项开关. */
    private var eagerSelect by mutableStateOf(initialEagerSelect)

    init {
        viewModel.configure(eager = initialEagerSelect)
    }

    /** 切换「快速选择在线数据源」: 同步抢先选源, 另外两段都收起来. */
    fun onFastSelectWebKindChanged(enabled: Boolean) {
        eagerSelect = enabled
        viewModel.configure(eager = enabled)
    }

    /**
     * 跟上设置项的真实值, 但 **不重播**.
     *
     * 设置是异步读出来的: 刚进页面时拿到的是占位值, 真值稍后才到. 这时候只该悄悄对齐,
     * 不该像用户刚点了开关那样把动画拨回开头.
     */
    fun syncEagerSelect(enabled: Boolean) {
        if (eagerSelect == enabled) return
        eagerSelect = enabled
        viewModel.configure(
            eager = enabled,
            priorityWaitSeconds = viewModel.config.selection.priorityWait?.inWholeSeconds?.toInt(),
            resolveBudgetSeconds = viewModel.config.resolve.budget.inWholeSeconds.toInt()
                .takeIf { viewModel.config.showInterceptClock },
            restart = false,
        )
    }

    /**
     * 选了「最长等待时间」: 演高优先级等待.
     *
     * "不等待" (0) 与 "无限制" 都没有可数的时限 —— 前者压根没有这道闸, 后者永远不会到点,
     * 计时器数不出东西来, 所以这两档只把动画收回到朴素流程.
     */
    fun onLowTierToleranceChanged(duration: Duration) {
        val seconds = duration.takeIf { it.isFinite() }?.inWholeSeconds?.toInt()?.takeIf { it > 0 }
        viewModel.configure(eager = eagerSelect, priorityWaitSeconds = seconds)
    }

    /** 选了「视频链接解析超时」: 演第三步的拦截超时. */
    fun onResolveTimeoutChanged(seconds: Int) {
        viewModel.configure(eager = eagerSelect, resolveBudgetSeconds = seconds.coerceAtLeast(1))
    }
}

@Composable
fun rememberMediaSelectorWorkflowDemoState(eagerSelect: Boolean): MediaSelectorWorkflowDemoState {
    val state = remember { MediaSelectorWorkflowDemoState(eagerSelect) }
    // 设置是异步读出来的, 真值到了再悄悄对齐一次
    LaunchedEffect(eagerSelect) { state.syncEagerSelect(eagerSelect) }
    return state
}

/**
 * 设置页里那块示意动画. 与设置项同宽, 没有标题 —— 它解释的是紧挨着的那些选项, 自己不该抢戏.
 */
@Composable
fun SettingsScope.MediaSelectorWorkflowItem(
    state: MediaSelectorWorkflowDemoState,
    modifier: Modifier = Modifier,
) {
    val isWidthCompact = currentWindowAdaptiveInfo1().isWidthCompact
    Box(Modifier.fillMaxWidth()) {
        Card(
            modifier
                .ifThen(isWidthCompact) { fillMaxWidth() }
                .ifThen(!isWidthCompact) { widthIn(max = 450.dp) }
                .padding(horizontal = SettingsScope.itemHorizontalPadding, vertical = 4.dp)
                .align(Alignment.Center),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            SelectorWorkflowAnimation(state.viewModel, Modifier.padding(8.dp))
        }
    }
}
