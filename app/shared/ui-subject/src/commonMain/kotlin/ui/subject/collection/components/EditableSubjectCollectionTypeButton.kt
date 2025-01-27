/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly

@Stable
class EditableSubjectCollectionTypeState(
    selfCollectionTypeFlow: Flow<UnifiedCollectionType>,
    private val hasAnyUnwatched: suspend () -> Boolean,
    private val onSetSelfCollectionType: suspend (UnifiedCollectionType) -> Unit,
    private val onSetAllEpisodesWatched: suspend () -> Unit,
    private val backgroundScope: CoroutineScope,
) {
    data class Presentation(
        val selfCollectionType: UnifiedCollectionType,
        val isSetSelfCollectionTypeWorking: Boolean,
        val isSetAllEpisodesWatchedWorking: Boolean,
        val showSetAllEpisodesDoneDialog: Boolean,
        val isPlaceholder: Boolean = false,
    ) {
        companion object {
            val Placeholder = Presentation(
                UnifiedCollectionType.WISH,
                false,
                false,
                false,
                isPlaceholder = true,
            )
        }
    }

    /**
     * 是否显示 "将所有剧集标记为看过" 对话框
     */
    private val showSetAllEpisodesDoneDialogFlow = MutableStateFlow(false)

    /**
     * 是否显示下拉菜单, 选择需要修改为的状态
     */
    var showDropdown by mutableStateOf(false)

    /**
     * [setSelfCollectionType] 的后台任务
     */
    private val setSelfCollectionTypeTasker = MonoTasker(backgroundScope)

    /**
     * [setAllEpisodesWatched] 的后台任务
     */
    private val setAllEpisodesWatchedTasker = MonoTasker(backgroundScope)

    /**
     * 待 UI 显示的数据
     */
    val presentationFlow: StateFlow<Presentation> =
        combine(
            selfCollectionTypeFlow,
            setSelfCollectionTypeTasker.isRunning,
            showSetAllEpisodesDoneDialogFlow,
            setAllEpisodesWatchedTasker.isRunning,
        ) { type, setSelfCollectionTypeTaskerWorking, showSetAllEpisodesDoneDialog, setAllEpisodesWatchedWorking ->
            Presentation(
                selfCollectionType = type,
                isSetSelfCollectionTypeWorking = setSelfCollectionTypeTaskerWorking,
                isSetAllEpisodesWatchedWorking = setAllEpisodesWatchedWorking,
                showSetAllEpisodesDoneDialog = showSetAllEpisodesDoneDialog,
            )
        }.stateIn(
            backgroundScope,
            SharingStarted.WhileSubscribed(5000),
            initialValue = Presentation.Placeholder,
        )

    fun setSelfCollectionType(new: UnifiedCollectionType) {
        setSelfCollectionTypeTasker.launch {
            onSetSelfCollectionType(new)
            if (new == UnifiedCollectionType.DONE && hasAnyUnwatched()) {
                showSetAllEpisodesDoneDialogFlow.value = true
            }
        }
    }

    fun setAllEpisodesWatched() {
        backgroundScope.launch { onSetAllEpisodesWatched() }
    }

    fun dismissSetAllEpisodesDoneDialog() {
        showSetAllEpisodesDoneDialogFlow.value = false
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

    val presentation by state.presentationFlow
        .collectAsStateWithLifecycle()

    SubjectCollectionTypeButton(
        presentation.selfCollectionType,
        onEdit = {
            state.setSelfCollectionType(it)
        },
        modifier = modifier.placeholder(presentation.isPlaceholder),
        enabled = !presentation.isSetSelfCollectionTypeWorking,
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
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    if (presentation.showSetAllEpisodesDoneDialog) {
        SetAllEpisodeDoneDialog(
            onDismissRequest = { state.dismissSetAllEpisodesDoneDialog() },
            isWorking = presentation.isSetAllEpisodesWatchedWorking,
            onConfirm = {
                state.setAllEpisodesWatched()
                state.dismissSetAllEpisodesDoneDialog()
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
        MutableStateFlow(type)
    }
    return remember {
        createTestEditableSubjectCollectionTypeState(selfCollectionType, backgroundScope)
    }
}

@TestOnly
fun createTestEditableSubjectCollectionTypeState(
    selfCollectionType: MutableStateFlow<UnifiedCollectionType>,
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
