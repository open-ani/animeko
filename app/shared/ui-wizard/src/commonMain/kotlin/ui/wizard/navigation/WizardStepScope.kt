/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Stable

@Stable
class WizardStepScope(
    /**
     * Access the list state.
     */
    val wizardScrollState: ScrollState,
    /**
     * Access scrolling top app bar.
     */
    private val scrollTopAppBarExpanded: suspend () -> Unit,
    /**
     * Access scrolling top app bar.
     */
    private val scrollTopAppBarCollapsed: suspend () -> Unit,
) {
    suspend fun scrollTopAppBarExpanded() {
        scrollTopAppBarExpanded.invoke()
    }

    suspend fun scrollTopAppBarCollapsed() {
        scrollTopAppBarCollapsed.invoke()
    }
}