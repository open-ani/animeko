/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.exploration.schedule

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import kotlinx.datetime.offsetAt
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * 覆盖 [ScheduleTimeZoneSelector] 的交互: 打开菜单, 选择某个 UTC 偏移, 选择"系统时区"时回传 `null`,
 * 以及菜单高度上限 (防止菜单被窗口挤压后顶到标题栏按钮).
 */
class ScheduleTimeZoneSelectorTest {
    private val utc8 = timeZoneOfUtcOffsetMinutes(8 * 60)
    private val utc9 = timeZoneOfUtcOffsetMinutes(9 * 60)
    private val utc530 = timeZoneOfUtcOffsetMinutes(5 * 60 + 30)

    private fun optionTag(zone: TimeZone) = "schedule-time-zone-option-${zone.id}"

    /**
     * 受控地渲染选择器并记录每次选择, 模拟页面的 timeZonePreference / onSelectTimeZone 接线.
     *
     * 偏好本身保持可空: `null` 表示跟随系统时区, 与"选中了恰好等于系统时区的那一项"是两种状态,
     * 选择器必须能区分它们才能正确勾选.
     */
    @Composable
    private fun ControlledSelector(
        initial: TimeZone?,
        onSelected: (TimeZone?) -> Unit = {},
    ) {
        var preference by remember(initial) { mutableStateOf(initial) }
        Box(Modifier.width(400.dp)) {
            ScheduleTimeZoneSelector(
                selectedTimeZone = preference,
                onSelectTimeZone = {
                    preference = it
                    onSelected(it)
                },
            )
        }
    }

    @Test
    fun `clicking the selector opens the menu with the current offset selected`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(utc8)
            }
        }

        // 菜单未打开时选项不存在
        onNodeWithTag(optionTag(utc9)).assertDoesNotExist()

        onNodeWithTag("schedule-time-zone-selector").assertIsDisplayed().performClick()

        onNodeWithTag("schedule-time-zone-option-system").assertExists()
        onNodeWithTag(optionTag(utc8)).assertExists()
        onNodeWithTag(optionTag(utc9)).assertExists()

        // 固定选择时勾中的是该项, 而不是"系统时区"
        onNodeWithTag(optionTag(utc8)).assertIsSelected()
        onNodeWithTag("schedule-time-zone-option-system").assertIsNotSelected()
    }

    /**
     * 偏好为 `null` 时勾中的必须是"系统时区"一项.
     *
     * 系统时区通常恰好等于列表中的某个偏移; 跟随系统与"固定选了这个偏移"是两种持久化行为不同的状态,
     * 只有原始偏好能区分它们.
     */
    @Test
    fun `following the system selects the system option not a matching offset`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(initial = null)
            }
        }

        onNodeWithTag("schedule-time-zone-selector").performClick()

        onNodeWithTag("schedule-time-zone-option-system").assertIsSelected()
        onNodeWithTag(optionTag(utc8)).assertIsNotSelected()
    }

    /**
     * 菜单高度必须有上限: 否则列表很长时菜单会被窗口约束向上挤压, 顶到标题栏的最小化/关闭按钮.
     */
    @Test
    fun `menu height is capped`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(null)
            }
        }

        onNodeWithTag("schedule-time-zone-selector").performClick()
        val heightPx = onNodeWithTag("schedule-time-zone-menu").assertIsDisplayed().fetchSemanticsNode().size.height
        val capPx = with(density) { 420.dp.roundToPx() }
        assertTrue(heightPx <= capPx, "菜单高度 $heightPx px 不应超过上限 $capPx px")
    }

    @Test
    fun `selecting an offset reports it and closes the menu`() = runAniComposeUiTest {
        var selected: TimeZone? = null
        var called = false
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(utc8) {
                    selected = it
                    called = true
                }
            }
        }

        onNodeWithTag("schedule-time-zone-selector").performClick()
        // 列表很长, +09:00 可能不在可视区内, 先滚动到它
        onNodeWithTag(optionTag(utc9)).performScrollTo().performClick()

        runOnIdle {
            assertEquals(true, called, "应当回调一次")
            assertEquals(utc9, selected)
        }
        // 选择后菜单关闭
        onNodeWithTag(optionTag(utc9)).assertDoesNotExist()
    }

    /** 半小时偏移也要能选中, 证明列表覆盖的不是整点. */
    @Test
    fun `half hour offsets are selectable`() = runAniComposeUiTest {
        var selected: TimeZone? = null
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(null) { selected = it }
            }
        }

        onNodeWithTag("schedule-time-zone-selector").performClick()
        onNodeWithTag(optionTag(utc530)).performScrollTo().performClick()

        runOnIdle {
            assertEquals(utc530, selected)
            assertIs<TimeZone>(selected)
            assertEquals(
                5 * 3600 + 30 * 60,
                selected!!.offsetAt(Clock.System.now()).totalSeconds,
                "选中的应当是 +05:30 固定偏移",
            )
        }
    }

    @Test
    fun `selecting the system option reports null`() = runAniComposeUiTest {
        var selected: TimeZone? = utc8
        var called = false
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(utc8) {
                    selected = it
                    called = true
                }
            }
        }

        onNodeWithTag("schedule-time-zone-selector").performClick()
        onNodeWithTag("schedule-time-zone-option-system").performClick()

        runOnIdle {
            assertEquals(true, called, "应当回调一次")
            assertNull(selected, "选择系统时区应当回传 null")
        }
    }
}
