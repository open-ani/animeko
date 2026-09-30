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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import me.him188.ani.app.domain.foundation.LoadError
import me.him188.ani.app.tools.MonoTasker
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.lang.*
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.showLoadError
import me.him188.ani.datasources.api.topic.UnifiedCollectionType
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.*
import kotlin.coroutines.cancellation.CancellationException

/**
 * 编辑条目收藏类型的交互. 由 [EditableSubjectCollectionTypeState] 实现, 也可由页面状态委托实现.
 */
interface SubjectCollectionTypeEditActions {
    /**
     * @return 失败原因, 成功为 `null`.
     */
    suspend fun setSelfCollectionType(new: UnifiedCollectionType): LoadError?

    /**
     * [setSelfCollectionType] 成功后是否应该询问 "同时设置所有剧集为看过".
     * 供不使用 [EditableSubjectCollectionTypeDialogsHost] 的界面 (例如 TV) 在设置完成后同步读取.
     */
    val shouldOfferMarkAllWatched: Boolean

    fun setAllEpisodesWatched()

    /**
     * 等待完成的 [setAllEpisodesWatched].
     *
     * @return 失败原因, 成功为 `null`.
     */
    suspend fun setAllEpisodesWatchedAwait(): LoadError?
    fun dismissSetAllEpisodesDoneDialog()

    companion object Noop : SubjectCollectionTypeEditActions {
        override suspend fun setSelfCollectionType(new: UnifiedCollectionType): LoadError? = null
        override val shouldOfferMarkAllWatched: Boolean get() = false
        override fun setAllEpisodesWatched() {}
        override suspend fun setAllEpisodesWatchedAwait(): LoadError? = null
        override fun dismissSetAllEpisodesDoneDialog() {}
    }
}

@Stable
class EditableSubjectCollectionTypeState(
    selfCollectionTypeFlow: Flow<UnifiedCollectionType>,
    private val hasAnyUnwatched: suspend () -> Boolean,
    private val onSetSelfCollectionType: suspend (UnifiedCollectionType) -> Unit,
    private val onSetAllEpisodesWatched: suspend () -> Unit,
    private val backgroundScope: CoroutineScope,
) : SubjectCollectionTypeEditActions {
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
    override val shouldOfferMarkAllWatched: Boolean get() = showSetAllEpisodesDoneDialogFlow.value

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

    override suspend fun setSelfCollectionType(new: UnifiedCollectionType): LoadError? {
        return setSelfCollectionTypeTasker.async {
            try {
                onSetSelfCollectionType(new)
                if (new == UnifiedCollectionType.DONE && hasAnyUnwatched()) {
                    showSetAllEpisodesDoneDialogFlow.value = true
                }
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                LoadError.fromException(e)
            }
        }.await()
    }

    override fun setAllEpisodesWatched() {
        backgroundScope.launch { setAllEpisodesWatchedAwait() }
    }

    override suspend fun setAllEpisodesWatchedAwait(): LoadError? = setAllEpisodesWatchedTasker.async {
        LoadError.runAndWrapOrThrowCancellation { onSetAllEpisodesWatched() }
    }.await()

    override fun dismissSetAllEpisodesDoneDialog() {
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
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    EditableSubjectCollectionTypeButton(presentation, state, modifier)
}

/**
 * 展示当前收藏状态的按钮, 点击弹出 [EditCollectionTypeDropDown]; 自带 [EditableSubjectCollectionTypeDialogsHost].
 *
 * 展示数据与动作分离的版本, 供页面级 UiState 使用.
 */
@Composable
fun EditableSubjectCollectionTypeButton(
    presentation: EditableSubjectCollectionTypeState.Presentation,
    actions: SubjectCollectionTypeEditActions,
    modifier: Modifier = Modifier,
) {
    // 同时设置所有剧集为看过
    EditableSubjectCollectionTypeDialogsHost(presentation, actions)

    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current

    SubjectCollectionTypeButton(
        presentation.selfCollectionType,
        onEdit = {
            scope.launch {
                val error = actions.setSelfCollectionType(it)
                error?.let(toaster::showLoadError)
            }
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
    val presentation by state.presentationFlow.collectAsStateWithLifecycle()
    EditableSubjectCollectionTypeDialogsHost(presentation, state)
}

/**
 * "同时设置所有剧集为看过" 对话框, 展示数据与动作分离的版本.
 */
@Composable
fun EditableSubjectCollectionTypeDialogsHost(
    presentation: EditableSubjectCollectionTypeState.Presentation,
    actions: SubjectCollectionTypeEditActions,
) {
    val scope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    if (presentation.showSetAllEpisodesDoneDialog) {
        SetAllEpisodeDoneDialog(
            onDismissRequest = { actions.dismissSetAllEpisodesDoneDialog() },
            isWorking = presentation.isSetAllEpisodesWatchedWorking,
            onConfirm = {
                scope.launch {
                    val error = actions.setAllEpisodesWatchedAwait()
                    if (error == null) actions.dismissSetAllEpisodesDoneDialog()
                    else toaster.showLoadError(error)
                }
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
        text = { Text(stringResource(Lang.subject_collection_set_all_episodes_watched)) },
        confirmButton = {
            TextButton(onConfirm, enabled = !isWorking) { Text(stringResource(Lang.subject_collection_set)) }

            if (isWorking) {
                CircularProgressIndicator(Modifier.padding(start = 8.dp).size(24.dp))
            }
        },
        dismissButton = { TextButton(onDismissRequest) { Text(stringResource(Lang.subject_collection_ignore)) } },
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
