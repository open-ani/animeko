/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.notification

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import me.him188.ani.app.ui.foundation.layout.LocalAppWindowVisible
import me.him188.ani.app.ui.framework.runAniComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationForegroundObserverTest {
    @Test
    fun `a window hidden to the tray counts as background`() = runAniComposeUiTest {
        val badgeManager = FakeNotificationBadgeManager()
        val windowVisible = mutableStateOf(true)
        setContent {
            CompositionLocalProvider(LocalAppWindowVisible provides windowVisible) {
                NotificationForegroundObserver(badgeManager)
            }
        }
        waitUntil { badgeManager.foregroundChanges.lastOrNull() == true }

        runOnIdle { windowVisible.value = false }
        waitUntil { badgeManager.foregroundChanges.lastOrNull() == false }

        // 从托盘恢复窗口时回到前台, 管理器借此立即刷新
        runOnIdle { windowVisible.value = true }
        waitUntil { badgeManager.foregroundChanges.lastOrNull() == true }

        assertEquals(listOf(true, false, true), badgeManager.foregroundChanges.toList())
    }
}
