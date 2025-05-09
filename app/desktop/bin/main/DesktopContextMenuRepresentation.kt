/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.desktop

import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuRepresentation
import androidx.compose.foundation.ContextMenuState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset

object DesktopContextMenuRepresentation : ContextMenuRepresentation {

    @Composable
    override fun Representation(state: ContextMenuState, items: () -> List<ContextMenuItem>) {
        val status = state.status
        if (status is ContextMenuState.Status.Open) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = {
                    state.status = ContextMenuState.Status.Closed
                },
                offset = with(LocalDensity.current) {
                    status.rect.center.let {
                        DpOffset(
                            it.x.toDp(),
                            (-it.y).toDp()
                        )
                    }
                },
            ) {
                items().forEach {
                    DropdownMenuItem(
                        onClick = {
                            it.onClick()
                            state.status = ContextMenuState.Status.Closed
                        },
                        text = {
                            Text(it.label)
                        },
                    )
                }
            }
        }
    }
}