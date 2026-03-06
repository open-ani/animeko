/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.cache.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class CacheSelectionState(
    initialInSelection: Boolean,
    initialSelectedIds: Set<String>,
) {
    var inSelection by mutableStateOf(initialInSelection)
    var selectedIds by mutableStateOf(initialSelectedIds)

    fun overrideSelected(list: Set<String>) {
        selectedIds = list
    }

    fun toggleSelection(vararg ids: String) {
        val allSelected = ids.all { it in selectedIds }
        val nextIds = selectedIds.toMutableSet().apply {
            if (allSelected) removeAll(ids) else addAll(ids)
        }
        selectedIds = nextIds.toSet()
    }

    fun enterSelectionWith(list: Set<String>) {
        inSelection = true
        selectedIds = list
    }

    fun clear() {
        inSelection = false
        selectedIds = emptySet()
    }

    companion object {
        val Saver: Saver<CacheSelectionState, List<String>> = Saver(
            save = {
                buildList {
                    add(it.inSelection.toString())
                    addAll(it.selectedIds)
                }
            },
            restore = {
                val inSelection = it.getOrNull(0)?.toBoolean() ?: false
                val selectedIds = it.drop(1).toSet()
                CacheSelectionState(inSelection, selectedIds)
            },
        )
    }
}

@Composable
internal fun rememberCacheSelectionState(
    initialInSelection: Boolean = false,
    initialSelectedIds: Set<String> = emptySet(),
): CacheSelectionState {
    return rememberSaveable(saver = CacheSelectionState.Saver) {
        CacheSelectionState(initialInSelection, initialSelectedIds)
    }
}