/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
internal class MediaSourceSelectionState(
    initialInSelection: Boolean = false,
    initialSelectedIds: Set<String> = emptySet(),
) {
    var inSelection by mutableStateOf(initialInSelection)
        private set

    var selectedIds by mutableStateOf(initialSelectedIds)
        private set

    fun toggleSelection(instanceId: String) {
        selectedIds = selectedIds.toMutableSet().apply {
            if (!add(instanceId)) {
                remove(instanceId)
            }
        }
    }

    fun enterSelection() {
        inSelection = true
    }

    fun enterSelectionWith(instanceId: String) {
        inSelection = true
        selectedIds = selectedIds + instanceId
    }

    fun selectAll(instanceIds: Collection<String>) {
        inSelection = true
        selectedIds = instanceIds.toSet()
    }

    fun retainSelection(instanceIds: Set<String>) {
        selectedIds = selectedIds.intersect(instanceIds)
    }

    fun clear() {
        inSelection = false
        selectedIds = emptySet()
    }

    companion object {
        val Saver: Saver<MediaSourceSelectionState, List<String>> = Saver(
            save = {
                buildList {
                    add(it.inSelection.toString())
                    addAll(it.selectedIds)
                }
            },
            restore = {
                MediaSourceSelectionState(
                    initialInSelection = it.firstOrNull()?.toBoolean() ?: false,
                    initialSelectedIds = it.drop(1).toSet(),
                )
            },
        )
    }
}

@Composable
internal fun rememberMediaSourceSelectionState(): MediaSourceSelectionState {
    return rememberSaveable(saver = MediaSourceSelectionState.Saver) {
        MediaSourceSelectionState()
    }
}
