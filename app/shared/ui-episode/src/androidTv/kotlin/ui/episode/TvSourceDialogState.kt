/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.leanback.ui.episode

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

internal enum class TvSourceMode { Simple, Detailed }

/** UI-owned browsing choices; selecting a tab never changes the media selected for playback. */
@Stable
internal class TvSourceDialogState(
    mode: TvSourceMode = TvSourceMode.Simple,
    selectedSourceId: String? = null,
    showExcluded: Boolean = false,
) {
    var mode by mutableStateOf(mode)
    var selectedSourceId by mutableStateOf(selectedSourceId)
    var showExcluded by mutableStateOf(showExcluded)

    fun selectedIndex(groups: List<TvSourceGroup>): Int =
        groups.indexOfFirst { it.instanceId == selectedSourceId }.coerceAtLeast(0)

    fun selectedGroup(groups: List<TvSourceGroup>): TvSourceGroup? = groups.getOrNull(selectedIndex(groups))

    fun moveHorizontally(direction: Int, groups: List<TvSourceGroup>) {
        when {
            mode == TvSourceMode.Simple && direction > 0 -> mode = TvSourceMode.Detailed
            mode == TvSourceMode.Simple -> Unit
            direction < 0 && selectedIndex(groups) == 0 -> mode = TvSourceMode.Simple
            else -> groups.getOrNull(selectedIndex(groups) + direction)?.let { selectedSourceId = it.instanceId }
        }
    }

    companion object {
        val Saver = listSaver<TvSourceDialogState, Any?>(
            save = { listOf(it.mode.name, it.selectedSourceId, it.showExcluded) },
            restore = {
                TvSourceDialogState(
                    TvSourceMode.valueOf(it[0] as String),
                    it[1] as String?,
                    it[2] as Boolean
                )
            },
        )
    }
}

/** Remember at the player screen so closing and reopening the dialog retains its browsing choices. */
@Composable
internal fun rememberTvSourceDialogState(): TvSourceDialogState =
    rememberSaveable(saver = TvSourceDialogState.Saver) { TvSourceDialogState() }
