/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.collection.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
class EditableSubjectCollectionTypeState(
    selfCollectionType: State<UnifiedCollectionType>,
    private val hasAnyUnwatched: suspend () -> Boolean,
    private val onSetSelfCollectionType: suspend (UnifiedCollectionType) -> Unit,
    private val onSetAllEpisodesWatched: suspend () -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    val selfCollectionType by selfCollectionType
    val isCollected by derivedStateOf {
        this.selfCollectionType != UnifiedCollectionType.NOT_COLLECTED
    }

    var showSetAllEpisodesDoneDialog by mutableStateOf(false)
    var showDropdown by mutableStateOf(false)

    private val setSelfCollectionTypeTasker = MonoTasker(backgroundScope)
    val isSetSelfCollectionTypeWorking get() = setSelfCollectionTypeTasker.isRunning

    fun setSelfCollectionType(new: UnifiedCollectionType) {
        setSelfCollectionTypeTasker.launch {
            onSetSelfCollectionType(new)
            if (new == UnifiedCollectionType.DONE && hasAnyUnwatched()) {
                withContext(Dispatchers.Main) { showSetAllEpisodesDoneDialog = true }
            }
        }
    }

    private val setAllEpisodesWatchedTasker = MonoTasker(backgroundScope)
    val isSetAllEpisodesWatchedWorking get() = setAllEpisodesWatchedTasker.isRunning

    fun setAllEpisodesWatched() {
        backgroundScope.launch { onSetAllEpisodesWatched() }
    }
}

/**
 * 展示当前收藏状态的按钮, 点击弹出 [EditCollectionTypeDropDown].
 * 当设置为 "看过" 时, 还会弹出 [SetAllEpisodeDoneDialog].
 */
@Composable
fun EditableSubjectCollectionTypeButton(
    state: EditableSubjectCollectionTypeState,
    modifier: Modifier = Modifier,
) {
    // 同时设置所有剧集为看过
    EditableSubjectCollectionTypeDialogsHost(state)

    SubjectCollectionTypeButton(
        state.selfCollectionType,
        onEdit = {
            state.setSelfCollectionType(it)
        },
        enabled = !state.isSetSelfCollectionTypeWorking.collectAsStateWithLifecycle().value,
        modifier = modifier,
    )
}

/**
 * 用于显示 "同时设置所有剧集为看过" 的对话框.
 *
 * [EditableSubjectCollectionTypeButton] 已经包含了这个 dialog, 所以一般来说不需要单独使用这个.
 *
 * @see EditableSubjectCollectionTypeButton
 */
@Composable
fun EditableSubjectCollectionTypeDialogsHost(
    state: EditableSubjectCollectionTypeState,
) {
    // 同时设置所有剧集为看过
    if (state.showSetAllEpisodesDoneDialog) {
        SetAllEpisodeDoneDialog(
            onDismissRequest = { state.showSetAllEpisodesDoneDialog = false },
            isWorking = state.isSetAllEpisodesWatchedWorking.collectAsStateWithLifecycle().value,
            onConfirm = {
                state.setAllEpisodesWatched()
                state.showSetAllEpisodesDoneDialog = false
            },
        )
    }
}

@Composable
private fun SetAllEpisodeDoneDialog(
    isWorking: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Icons.Rounded.TaskAlt, null) },
        text = { Text("要同时设置所有剧集为看过吗？") },
        confirmButton = {
            TextButton(onConfirm) { Text("设置") }

            if (isWorking) {
                CircularProgressIndicator(Modifier.padding(start = 8.dp).size(24.dp))
            }
        },
        dismissButton = { TextButton(onDismissRequest) { Text("忽略") } },
        modifier = modifier,
    )
}

@TestOnly
@Composable
fun rememberTestEditableSubjectCollectionTypeState(type: UnifiedCollectionType = UnifiedCollectionType.WISH): EditableSubjectCollectionTypeState {
    val backgroundScope = rememberCoroutineScope()
    val selfCollectionType = remember {
        mutableStateOf(type)
    }
    return remember {
        createTestEditableSubjectCollectionTypeState(selfCollectionType, backgroundScope)
    }
}

@TestOnly
fun createTestEditableSubjectCollectionTypeState(
    selfCollectionType: MutableState<UnifiedCollectionType>,
    backgroundScope: CoroutineScope
) = EditableSubjectCollectionTypeState(
    selfCollectionType,
    hasAnyUnwatched = { false },
    onSetSelfCollectionType = {
        selfCollectionType.value = it
    },
    onSetAllEpisodesWatched = { },
    backgroundScope,
)
