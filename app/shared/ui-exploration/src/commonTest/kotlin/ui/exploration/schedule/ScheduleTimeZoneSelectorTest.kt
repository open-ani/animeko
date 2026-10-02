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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import kotlinx.datetime.TimeZone
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * 覆盖 [ScheduleTimeZoneSelector] 的交互: 打开菜单, 选择某个时区, 以及选择"系统时区"时回传 `null`.
 */
class ScheduleTimeZoneSelectorTest {
    private val shanghai = TimeZone.of("Asia/Shanghai")
    private val tokyo = TimeZone.of("Asia/Tokyo")

    /**
     * 受控地渲染选择器并记录每次选择, 模拟页面的 timeZone / onSelectTimeZone 接线.
     */
    @Composable
    private fun ControlledSelector(
        initial: TimeZone,
        onSelected: (TimeZone?) -> Unit,
    ) {
        var timeZone by remember(initial) { mutableStateOf(initial) }
        Box(Modifier.width(400.dp)) {
            ScheduleTimeZoneSelector(
                timeZone = timeZone,
                onSelectTimeZone = {
                    timeZone = it ?: TimeZone.currentSystemDefault()
                    onSelected(it)
                },
            )
        }
    }

    @Test
    fun `clicking the selector opens the menu with the current zone selected`() = runAniComposeUiTest {
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(shanghai) {}
            }
        }

        // 菜单未打开时选项不存在
        onNodeWithTag("schedule-time-zone-option-Asia/Tokyo").assertDoesNotExist()

        onNodeWithTag("schedule-time-zone-selector").assertIsDisplayed().performClick()

        // 打开后可看到系统时区与若干具体时区
        onNodeWithTag("schedule-time-zone-option-system").assertIsDisplayed()
        onNodeWithTag("schedule-time-zone-option-Asia/Tokyo").assertIsDisplayed()
        onNodeWithTag("schedule-time-zone-option-Asia/Shanghai").assertIsDisplayed()
    }

    @Test
    fun `selecting a zone reports it and closes the menu`() = runAniComposeUiTest {
        var selected: TimeZone? = null
        var called = false
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(shanghai) {
                    selected = it
                    called = true
                }
            }
        }

        onNodeWithTag("schedule-time-zone-selector").performClick()
        onNodeWithTag("schedule-time-zone-option-Asia/Tokyo").performClick()

        runOnIdle {
            assertEquals(true, called, "应当回调一次")
            assertEquals(tokyo, selected)
        }
        // 选择后菜单关闭
        onNodeWithTag("schedule-time-zone-option-Asia/Tokyo").assertDoesNotExist()
    }

    @Test
    fun `selecting the system option reports null`() = runAniComposeUiTest {
        var selected: TimeZone? = shanghai
        var called = false
        setContent {
            ProvideCompositionLocalsForPreview {
                ControlledSelector(shanghai) {
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
