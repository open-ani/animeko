/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media

import me.him188.ani.utils.selectorworkflow.ResolveOutcome
import me.him188.ani.utils.selectorworkflow.SelectMode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * 设置页里那块示意动画的联动规则: 动哪个设置项就只演它对应的那一段, 并且从头播.
 */
class MediaSelectorWorkflowDemoStateTest {

    private fun state(eager: Boolean = true) = MediaSelectorWorkflowDemoState(eager)

    private val MediaSelectorWorkflowDemoState.config get() = viewModel.config
    private val MediaSelectorWorkflowDemoState.playsResolveDemo
        get() = viewModel.config.resolve.outcomes.contains(ResolveOutcome.Timeout)

    @Test
    fun starts_with_the_current_fast_select_state_and_nothing_else() {
        val on = state(eager = true)
        assertEquals(SelectMode.Eager, on.config.selection.mode)
        assertNull(on.config.selection.priorityWait, "刚进来不该演高优先级那一段")
        assertTrue(!on.playsResolveDemo, "刚进来不该演第三步超时那一段")

        assertEquals(SelectMode.WaitAll, state(eager = false).config.selection.mode)
    }

    @Test
    fun picking_the_max_wait_time_plays_only_the_priority_segment() {
        val s = state()
        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(8.seconds, s.config.selection.priorityWait)
        assertTrue(s.config.selection.demoBothPriorityPaths, "该连演等到了/等超时两条路径")
        assertTrue(!s.playsResolveDemo, "同时该把第三步那一段收起来")
    }

    @Test
    fun picking_the_resolve_timeout_plays_only_the_resolve_segment() {
        val s = state()
        s.onLowTierToleranceChanged(8.seconds)      // 先演高优先级
        s.onResolveTimeoutChanged(15)               // 再动第三步

        assertTrue(s.playsResolveDemo)
        assertEquals(15.seconds, s.config.resolve.budget)
        assertNull(s.config.selection.priorityWait, "动第三步就该把高优先级那一段收起来")
    }

    @Test
    fun toggling_fast_select_puts_both_segments_away() {
        val s = state(eager = true)
        s.onResolveTimeoutChanged(15)
        assertTrue(s.playsResolveDemo)

        s.onFastSelectWebKindChanged(false)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode)
        assertNull(s.config.selection.priorityWait)
        assertTrue(!s.playsResolveDemo, "切换快速选择时两段都该收起来")

        s.onFastSelectWebKindChanged(true)
        assertEquals(SelectMode.Eager, s.config.selection.mode)
    }

    @Test
    fun the_fast_select_state_survives_the_other_two_settings() {
        val s = state(eager = false)
        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode, "演别的段不该把抢先选源打开")
        s.onResolveTimeoutChanged(15)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode)
    }

    @Test
    fun degenerate_wait_times_fall_back_to_the_plain_flow() {
        // "不等待" 压根没有这道闸; "无限制" 永远不会到点, 计时器数不出东西来
        val s = state()
        s.onLowTierToleranceChanged(8.seconds)
        s.onLowTierToleranceChanged(Duration.ZERO)
        assertNull(s.config.selection.priorityWait)

        s.onLowTierToleranceChanged(8.seconds)
        s.onLowTierToleranceChanged(Duration.INFINITE)
        assertNull(s.config.selection.priorityWait)
    }

    @Test
    fun syncing_the_loaded_setting_does_not_restart_or_disturb_the_current_segment() {
        // 设置是异步读出来的: 占位值先到, 真值后到. 真值到了只该悄悄对齐
        val s = state(eager = true)
        s.onLowTierToleranceChanged(8.seconds)
        s.viewModel.player.advance(1.seconds)
        val playhead = s.viewModel.player.playhead
        assertTrue(playhead > Duration.ZERO)

        val progress = s.viewModel.state.progress

        s.syncEagerSelect(false)
        assertEquals(SelectMode.WaitAll, s.config.selection.mode, "该跟上真值")
        assertEquals(8.seconds, s.config.selection.priorityWait, "正在演的那一段不该被打断")
        assertTrue(s.viewModel.player.playhead > Duration.ZERO, "不是用户操作, 不该拨回开头")
        // 换了时间线长度也变了, 播放位置按比例保留, 不会跳
        assertTrue(abs(s.viewModel.state.progress - progress) < 0.02f)

        val after = s.viewModel.player.playhead
        s.syncEagerSelect(false)   // 已经一致了, 什么都不该发生
        assertEquals(after, s.viewModel.player.playhead)
    }

    @Test
    fun every_change_restarts_the_animation_from_the_beginning() {
        val s = state()
        fun advanceABit() = s.viewModel.player.advance(1.seconds)

        advanceABit()
        assertTrue(s.viewModel.player.playhead > Duration.ZERO)
        s.onLowTierToleranceChanged(8.seconds)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead, "选了设置项该从头播")

        advanceABit()
        s.onResolveTimeoutChanged(15)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead)

        advanceABit()
        s.onFastSelectWebKindChanged(false)
        assertEquals(Duration.ZERO, s.viewModel.player.playhead)
    }
}
