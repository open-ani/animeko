/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.utils.selectorworkflow

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import me.him188.ani.utils.selectorworkflow.draw.WorkflowLayout
import me.him188.ani.utils.selectorworkflow.draw.WorkflowMetrics
import me.him188.ani.utils.selectorworkflow.draw.WorkflowPalette
import me.him188.ani.utils.selectorworkflow.draw.drawSelectorWorkflow
import me.him188.ani.utils.selectorworkflow.draw.rememberWorkflowPalette

/**
 * 数据源选择流程示意动画.
 *
 * 自己驱动帧. 想控制播放 (暂停、拖进度) 就用下面那个接受现成 [SelectorWorkflowState] 的重载.
 */
@Composable
fun SelectorWorkflowAnimation(
    viewModel: SelectorWorkflowViewModel,
    modifier: Modifier = Modifier,
    palette: WorkflowPalette = rememberWorkflowPalette(),
    metrics: WorkflowMetrics = WorkflowMetrics.Default,
) {
    LaunchedEffect(viewModel) {
        while (true) {
            withFrameNanos { viewModel.onFrame(it) }
        }
    }
    SelectorWorkflowAnimation(
        state = viewModel.state,
        config = viewModel.config,
        modifier = modifier,
        palette = palette,
        metrics = metrics,
    )
}

/**
 * 画出某一帧.
 *
 * 几何完全由 [config] 与 [metrics] 决定, 与 [state] 无关, 所以只在配置变化时重算.
 * 默认按虚拟画布的比例撑满宽度; 给了别的尺寸约束就在里面居中缩放, 不会变形.
 */
@Composable
fun SelectorWorkflowAnimation(
    state: SelectorWorkflowState,
    config: SelectorWorkflowConfig,
    modifier: Modifier = Modifier,
    palette: WorkflowPalette = rememberWorkflowPalette(),
    metrics: WorkflowMetrics = WorkflowMetrics.Default,
) {
    val layout = remember(config, metrics) { WorkflowLayout.of(config, metrics) }
    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(layout.canvasSize.width / layout.canvasSize.height),
    ) {
        drawSelectorWorkflow(state, layout, palette)
    }
}
