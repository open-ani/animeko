/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.LocalAniUiBehavior
import me.him188.ani.app.ui.foundation.aniCombinedClickable
import me.him188.ani.app.ui.foundation.consumeHeldConfirmKey
import me.him188.ani.app.ui.foundation.focus.resolveFocusRepeatedly
import me.him188.ani.app.ui.foundation.widgets.DismissDialogButton
import me.him188.ani.app.ui.foundation.widgets.dismissDialogButton
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.interaction.onRightClickIfSupported
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_add
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_delete
import me.him188.ani.app.ui.lang.settings_media_source_delete_can_readd
import me.him188.ani.app.ui.lang.settings_media_source_delete_confirm
import me.him188.ani.app.ui.lang.settings_media_source_delete_no_config
import me.him188.ani.app.ui.lang.settings_media_source_delete_selected
import me.him188.ani.app.ui.lang.settings_media_source_delete_with_config
import me.him188.ani.app.ui.lang.settings_media_source_deselect_all
import me.him188.ani.app.ui.lang.settings_media_source_disable
import me.him188.ani.app.ui.lang.settings_media_source_disable_selected
import me.him188.ani.app.ui.lang.settings_media_source_disabled
import me.him188.ani.app.ui.lang.settings_media_source_edit
import me.him188.ani.app.ui.lang.settings_media_source_enable
import me.him188.ani.app.ui.lang.settings_media_source_enable_selected
import me.him188.ani.app.ui.lang.settings_media_source_enter_selection_mode
import me.him188.ani.app.ui.lang.settings_media_source_exit_selection
import me.him188.ani.app.ui.lang.settings_media_source_from_subscription
import me.him188.ani.app.ui.lang.settings_media_source_list
import me.him188.ani.app.ui.lang.settings_media_source_list_description
import me.him188.ani.app.ui.lang.settings_media_source_more
import me.him188.ani.app.ui.lang.settings_media_source_select_all
import me.him188.ani.app.ui.lang.settings_media_source_select_template
import me.him188.ani.app.ui.lang.settings_media_source_selected_count
import me.him188.ani.app.ui.lang.settings_media_source_sort
import me.him188.ani.app.ui.lang.settings_media_source_start_test
import me.him188.ani.app.ui.lang.settings_media_source_stop_test
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.MediaSourceTier
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.isEmpty
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.rememberReorderableLazyListState
import org.burnoutcrew.reorderable.reorderable
import org.jetbrains.compose.resources.stringResource

@Stable
internal val MediaSourcesUsingNewSettings = listOf(
    RssMediaSource.FactoryId,
    SelectorMediaSource.FactoryId,
)

internal object MediaSourceGroupTestTags {
    const val ENTER_SELECTION = "media_source_enter_selection"
    const val EXIT_SELECTION = "media_source_exit_selection"
    const val SELECT_ALL = "media_source_select_all"

    fun item(instanceId: String): String = "media_source_item_$instanceId"
}

@Composable
internal fun SettingsScope.MediaSourceGroup(
    state: MediaSourceGroupState,
    edit: EditMediaSourceState,
    selectionState: MediaSourceSelectionState,
) {
    val navigator = LocalNavigator.current
    val uiScope = rememberCoroutineScope()
    var showSelectTemplate by remember { mutableStateOf(false) }
    if (showSelectTemplate) {
        // 选一个数据源来添加
        SelectMediaSourceTemplateDialog(
            templates = state.availableMediaSourceTemplates,
            onClick = { template ->
                showSelectTemplate = false

                // 一些数据源要用单独编辑页面
                when {
                    template.factoryId in MediaSourcesUsingNewSettings -> {
                        val editing = edit.startAdding(template)
                        val job = edit.confirmEdit(editing)
                        uiScope.launch {
                            job.join()
                            navigator.navigateEditMediaSource(template.factoryId, editing.editingMediaSourceId)
                        }
                        return@SelectMediaSourceTemplateDialog
                    }

                    // 旧的数据源类型, 仍然使用旧的对话框形式添加
                    template.parameters.list.isEmpty() -> {
                        // 没有参数, 直接添加
                        edit.confirmEdit(edit.startAdding(template))
                        return@SelectMediaSourceTemplateDialog
                    }

                    else -> edit.startAdding(template)
                }
            },
            onDismissRequest = { showSelectTemplate = false },
        )
    }

    edit.editMediaSourceState?.let {
        // 准备添加这个数据源, 需要配置
        // TODO: replace with a separate page
        EditMediaSourceDialog(it, onDismissRequest = { edit.cancelEdit() })
    }

    // 多选模式下的列表数据. 拖拽排序时先在本地重排, 拖拽结束后再持久化.
    var reorderData by remember { mutableStateOf(state.mediaSources) }
    val reorderableState = rememberReorderableLazyListState(
        onMove = { from, to ->
            reorderData = reorderData.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        },
        onDragEnd = { _, _ ->
            state.reorderMediaSources(newOrder = reorderData.map { it.instanceId })
        },
    )
    // 遥控器排序 (多选模式内, 替代指针的拖拽把手): 批量菜单里"排序"把该行拿起来 (carrying),
    // 上下键在 reorderData 里就地移动, 确认/返回键放下 —— 与拖拽一样, 放下那一刻才持久化
    var carryingId by remember { mutableStateOf<String?>(null) }
    val dropCarrying = {
        if (carryingId != null) {
            carryingId = null
            state.reorderMediaSources(newOrder = reorderData.map { it.instanceId })
        }
    }

    val selectionCount = selectionState.selectedIds.size
    val allSelected = state.mediaSources.isNotEmpty() &&
        state.mediaSources.all { it.instanceId in selectionState.selectedIds }

    LaunchedEffect(state.mediaSources, selectionState.inSelection) {
        reorderData = state.mediaSources
        if (selectionState.inSelection) {
            selectionState.retainSelection(state.mediaSources.mapTo(mutableSetOf()) { it.instanceId })
        } else {
            carryingId = null
        }
    }

    // ---- 遥控器形态 (见 AniUiBehavior.focusDrivenNavigation) 的多选交互 ----
    // 指针设备保持原样: 长按进多选 + 屏幕底部那条浮动工具栏.
    // 遥控器上那条浮窗要穿过整页设置项才够得到, 改成"长按行出下拉菜单":
    //   非多选态 -> 该项自己的菜单 (启用/禁用、编辑、删除) + 一项"多选";
    //   多选态   -> 批量菜单 (启用/禁用/删除所选、全选、退出多选), 长按的那一项先被选中,
    //              保证菜单里"所选"至少包含用户正对着的这一项.
    val focusDriven = LocalAniUiBehavior.current.focusDrivenNavigation

    // 进/退多选与批量删除都会把当前持焦的节点整个换掉 (行的 trailing 按钮片、标题栏的动作按钮、
    // 被删掉的行), 而 Compose 移除聚焦节点时是把焦点**清掉**, 不交给祖先 —— 不接手就是一屏
    // 没有焦点, 方向键全失效. 统一记一个"焦点该落到哪一行", 由下面的解析器送过去.
    var pendingRowFocusId by remember { mutableStateOf<String?>(null) }
    val pendingRowFocusRequester = remember { FocusRequester() }
    // 到位判据读实时状态而不是"到过位"的latch: 请求器会在各行之间搬家, latch 不清就会
    // 拿着上一次的 true 提前收工, 这一次一个 requestFocus 都不发
    var pendingRowFocused by remember { mutableStateOf(false) }
    // 退出多选时的落点: 用户最后碰过的那一行 (从标题栏的 ✕ 退出时焦点在标题栏上, 回不到列表)
    var lastTouchedRowId by remember { mutableStateOf<String?>(null) }
    val requestRowFocus: (String?) -> Unit = { id ->
        pendingRowFocused = false
        pendingRowFocusId = id
    }
    LaunchedEffect(pendingRowFocusId) {
        if (pendingRowFocusId == null) return@LaunchedEffect
        resolveFocusRepeatedly(attempts = 30, arrived = { pendingRowFocused }) {
            runCatching { pendingRowFocusRequester.requestFocus() }
        }
        pendingRowFocusId = null
    }

    val bulkActions = rememberMediaSourceBulkActions(state.mediaSources, selectionState, edit)
    var showBulkDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    if (showBulkDeleteConfirmation) {
        MediaSourceBulkDeleteConfirmation(
            count = bulkActions.selected.size,
            onConfirm = {
                // 删完这些行就没了; 焦点落到第一个幸存的行上 (全删光则交给全局兜底)
                requestRowFocus(
                    state.mediaSources.firstOrNull { it.instanceId !in selectionState.selectedIds }?.instanceId,
                )
                bulkActions.delete()
                showBulkDeleteConfirmation = false
            },
            onDismissRequest = { showBulkDeleteConfirmation = false },
        )
    }

    // 多选态下返回键 = 退出多选, 而不是退出设置页 (组合在页面导航的 BackHandler 之后, 最内层优先).
    // 遥控器上顺带把焦点接回最后碰过的那一行 —— 多选态的行片段会随退出整体换掉, 不接手焦点会丢.
    // 搬运态 (carrying) 的返回是"放下", 在行的 onPreviewKeyEvent 里就地消费, 传不到这里
    BackHandler(enabled = selectionState.inSelection) {
        if (focusDriven) requestRowFocus(lastTouchedRowId ?: state.mediaSources.firstOrNull()?.instanceId)
        selectionState.clear()
    }

    Group(
        title = {
            if (selectionState.inSelection) {
                Text(stringResource(Lang.settings_media_source_selected_count, selectionCount))
            } else {
                Text(stringResource(Lang.settings_media_source_list, state.mediaSources.size))
            }
        },
        description = if (selectionState.inSelection) {
            null
        } else {
            { Text(stringResource(Lang.settings_media_source_list_description)) }
        },
        actions = {
            if (selectionState.inSelection) {
                Row {
                    IconButton(
                        onClick = {
                            // 本按钮自己会随多选态一起消失, 焦点得先安排好去处
                            requestRowFocus(lastTouchedRowId ?: state.mediaSources.firstOrNull()?.instanceId)
                            selectionState.clear()
                        },
                        modifier = Modifier.testTag(MediaSourceGroupTestTags.EXIT_SELECTION),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(Lang.settings_media_source_exit_selection),
                        )
                    }
                    IconButton(
                        onClick = {
                            if (allSelected) {
                                selectionState.selectAll(emptyList())
                            } else {
                                selectionState.selectAll(state.mediaSources.map { it.instanceId })
                            }
                        },
                        enabled = state.mediaSources.isNotEmpty(),
                        modifier = Modifier.testTag(MediaSourceGroupTestTags.SELECT_ALL),
                    ) {
                        Icon(
                            if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll,
                            contentDescription = stringResource(
                                if (allSelected) {
                                    Lang.settings_media_source_deselect_all
                                } else {
                                    Lang.settings_media_source_select_all
                                },
                            ),
                        )
                    }
                }
            } else {
                Row {
                    IconButton(
                        {
                            edit.cancelEdit()
                            showSelectTemplate = true
                        },
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(Lang.settings_media_source_add))
                    }
                    IconButton(
                        {
                            edit.cancelEdit()
                            // 本按钮会随多选态换成 ✕/全选 (节点重建), 遥控器上焦点得先安排去处
                            if (focusDriven) requestRowFocus(state.mediaSources.firstOrNull()?.instanceId)
                            selectionState.enterSelection()
                        },
                        enabled = state.mediaSources.isNotEmpty(),
                        modifier = Modifier.testTag(MediaSourceGroupTestTags.ENTER_SELECTION),
                    ) {
                        Icon(
                            Icons.Rounded.Checklist,
                            contentDescription = stringResource(Lang.settings_media_source_enter_selection_mode),
                        )
                    }
                }
            }
        },
    ) {
        Box {
            // 指针平台的多选模式: 本列只撑高度 (alpha 0), 显示与交互由上面的覆盖层 LazyColumn 承担.
            // 遥控器 (focusDriven) 的多选直接用本列 —— 覆盖层是拖拽形态, 遥控器操作不了;
            // 且 alpha 0 的隐形项照样可聚焦, 会跟真实列表抢焦点, 绝不能在遥控器上把本列藏起来
            Column(
                Modifier
                    .ifThen(selectionState.inSelection && !focusDriven) { alpha(0f) }
                    .wrapContentHeight(),
            ) {
                // 多选态下渲染 reorderData (本地暂存序): 遥控器"排序"就地搬运, 放下才持久化.
                // 非多选态两者内容一致 (LaunchedEffect 同步), 用仓库序
                val rowItems = if (selectionState.inSelection) reorderData else state.mediaSources
                rowItems.forEachIndexed { index, item ->
                    if (index != 0) {
                        HorizontalDividerItem()
                    }
                    // key 定位: 搬运移动行时组合节点随之移动, 焦点跟着行走 (而不是留在原位置)
                    key(item.instanceId) {
                    val startEditing = {
                        if (item.factoryId in MediaSourcesUsingNewSettings) {
                            navigator.navigateEditMediaSource(item.factoryId, item.instanceId)
                        } else {
                            edit.startEditing(item)
                        }
                    }
                    val editText = stringResource(Lang.settings_media_source_edit)
                    val enterSelectionText = stringResource(Lang.settings_media_source_enter_selection_mode)
                    val moreText = stringResource(Lang.settings_media_source_more)
                    val selected = item.instanceId in selectionState.selectedIds

                    var showMoreDropdown by remember { mutableStateOf(false) }
                    var showConfirmDeletionDialog by rememberSaveable { mutableStateOf(false) }
                    if (showConfirmDeletionDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDeletionDialog = false },
                            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            title = { Text(stringResource(Lang.settings_media_source_delete)) },
                            text = {
                                if (item.parameters.isEmpty()) {
                                    Text(stringResource(Lang.settings_media_source_delete_no_config))
                                } else {
                                    Text(stringResource(Lang.settings_media_source_delete_with_config))
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    {
                                        edit.deleteMediaSource(item)
                                        showConfirmDeletionDialog = false
                                    },
                                ) {
                                    Text(
                                        stringResource(Lang.settings_media_source_delete_confirm),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            dismissButton = dismissDialogButton(
                                stringResource(Lang.settings_media_source_cancel),
                            ) { showConfirmDeletionDialog = false },
                        )
                    }

                    // 多选态下行的 trailing 整片不渲染, 三个点那颗 Box 不能当菜单的锚点,
                    // 批量菜单只好锚在整行上
                    var showBulkDropdown by remember { mutableStateOf(false) }
                    val carrying = carryingId == item.instanceId
                    // 搬运中的行每移一步显式滚进视野: 焦点没换节点, 系统那套"聚焦即滚动"不会跑
                    val carryBringIntoView = remember { BringIntoViewRequester() }
                    LaunchedEffect(carrying, index) {
                        if (!carrying) return@LaunchedEffect
                        withFrameNanos { } // 等这一帧布局落定, 否则拿到的还是移动前的坐标
                        runCatching { carryBringIntoView.bringIntoView() }
                    }
                    Box {
                        MediaSourceItem(
                            item,
                            Modifier
                                .testTag(MediaSourceGroupTestTags.item(item.instanceId))
                                .ifThen(item.instanceId == pendingRowFocusId) {
                                    focusRequester(pendingRowFocusRequester)
                                        .onFocusChanged { pendingRowFocused = it.hasFocus }
                                }
                                .ifThen(carrying) { shadow(16.dp) }
                                .bringIntoViewRequester(carryBringIntoView)
                                // 搬运态: 上下键移动本行, 确认/返回键放下并持久化. 挂在
                                // aniCombinedClickable 之前, 放下那一下确认键不会漏下去变成点击
                                .ifThen(carrying) {
                                    onPreviewKeyEvent { event ->
                                        when (event.key) {
                                            Key.DirectionUp, Key.DirectionDown -> {
                                                if (event.type == KeyEventType.KeyDown) {
                                                    val from =
                                                        reorderData.indexOfFirst { it.instanceId == item.instanceId }
                                                    val to = from + if (event.key == Key.DirectionUp) -1 else 1
                                                    if (from >= 0 && to in reorderData.indices) {
                                                        reorderData = reorderData.toMutableList()
                                                            .apply { add(to, removeAt(from)) }
                                                    }
                                                }
                                                true // 边界处也吞掉: 搬运期间焦点绝不离开本行
                                            }

                                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter,
                                            Key.Back, Key.Escape,
                                                -> {
                                                if (event.type == KeyEventType.KeyUp) dropCarrying()
                                                true
                                            }

                                            else -> false
                                        }
                                    }
                                }
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                // aniCombinedClickable 而不是原生的: 后者不会把遥控器确认键按住
                                // 500ms 转成 onLongClick, 用它的话下面这些长按全是死的
                                .aniCombinedClickable(
                                    onClickLabel = if (selectionState.inSelection) enterSelectionText else editText,
                                    onLongClick = {
                                        lastTouchedRowId = item.instanceId
                                        if (focusDriven && selectionState.inSelection) {
                                            // 长按未选中的项: 先把它选上再出批量菜单. 静默不响应
                                            // 在遥控器上和"按键坏了"分不出来, 而"我长按它"
                                            // 本来就是"连它一起操作"的意思
                                            if (item.instanceId !in selectionState.selectedIds) {
                                                selectionState.toggleSelection(item.instanceId)
                                            }
                                            showBulkDropdown = true
                                        } else {
                                            // 与上游一致: 长按进入多选并选中本行. 遥控器上行的
                                            // trailing 片段会随多选态消失, 焦点先接回本行
                                            if (focusDriven) requestRowFocus(item.instanceId)
                                            selectionState.enterSelectionWith(item.instanceId)
                                        }
                                    },
                                    onLongClickLabel = if (focusDriven && selectionState.inSelection) {
                                        moreText
                                    } else {
                                        enterSelectionText
                                    },
                                    onClick = {
                                        lastTouchedRowId = item.instanceId
                                        if (selectionState.inSelection) {
                                            selectionState.toggleSelection(item.instanceId)
                                        } else {
                                            startEditing()
                                        }
                                    },
                                ).onRightClickIfSupported {
                                    if (!selectionState.inSelection) {
                                        showMoreDropdown = true
                                    }
                                },
                            selectionMode = selectionState.inSelection,
                            selected = selected,
                            onToggleSelected = { selectionState.toggleSelection(item.instanceId) },
                        ) {
                            if (!selectionState.inSelection) {
                                IconButton({}, enabled = false) { // 放在 button 里保持 padding 一致
                                    ConnectionTesterResultIndicator(
                                        item.connectionTester,
                                        showIdle = false,
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showMoreDropdown = true },
                                    ) {
                                        Icon(
                                            Icons.Rounded.MoreVert,
                                            contentDescription = moreText,
                                        )
                                    }

                                    MoreOptionsDropdown(
                                        showMoreDropdown,
                                        onDismissRequest = { showMoreDropdown = false },
                                        onDeleteRequest = { showConfirmDeletionDialog = true },
                                        item,
                                        onEnabledChange = { edit.toggleMediaSourceEnabled(item, it) },
                                        onEdit = startEditing,
                                        // 遥控器上没有浮动工具栏, 进多选的入口只有这里和长按
                                        onEnterSelection = if (focusDriven) {
                                            {
                                                // 本菜单连同整片 trailing 会随多选态一起消失, 焦点交回该行
                                                requestRowFocus(item.instanceId)
                                                selectionState.enterSelectionWith(item.instanceId)
                                            }
                                        } else {
                                            null
                                        },
                                    )
                                }
                            }
                        }
                        if (focusDriven && selectionState.inSelection) {
                            BulkOptionsDropdown(
                                showBulkDropdown,
                                onDismissRequest = { showBulkDropdown = false },
                                actions = bulkActions,
                                allSelected = allSelected,
                                onDeleteRequest = { showBulkDeleteConfirmation = true },
                                // 菜单收起后焦点还在本行上, 拿起的就是长按的这一行
                                onStartCarry = { carryingId = item.instanceId },
                                onSelectAllChange = {
                                    if (allSelected) {
                                        selectionState.selectAll(emptyList())
                                    } else {
                                        selectionState.selectAll(state.mediaSources.map { it.instanceId })
                                    }
                                },
                            )
                        }
                    }
                    }
                }
            }
            if (selectionState.inSelection && !focusDriven) {
                // 往上面再盖一层, 因为 SettingsTab 已经有 scrollable 了, LazyColumn 如果不加高度限制会出错
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .matchParentSize()
                        .reorderable(reorderableState),
                ) {
                    itemsIndexed(
                        reorderData,
                        key = { _, item -> item.instanceId },
                    ) { index, item ->
                        if (index != 0) {
                            HorizontalDividerItem()
                        }
                        ReorderableItem(reorderableState, key = item.instanceId) { isDragging ->
                            val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                            val selected = item.instanceId in selectionState.selectedIds
                            MediaSourceItem(
                                item,
                                Modifier
                                    .shadow(elevation.value)
                                    .background(
                                        if (selected) {
                                            MaterialTheme.colorScheme.surfaceContainer
                                        } else {
                                            MaterialTheme.colorScheme.surface // match card background
                                        },
                                    )
                                    .clickable { selectionState.toggleSelection(item.instanceId) },
                                selectionMode = true,
                                selected = selected,
                                onToggleSelected = { selectionState.toggleSelection(item.instanceId) },
                            ) {
                                Icon(
                                    Icons.Rounded.Reorder,
                                    stringResource(Lang.settings_media_source_sort),
                                    Modifier
                                        .minimumInteractiveComponentSize()
                                        .detectReorder(reorderableState),
                                )
                            }
                        }
                    }
                }
            } else {
                // 清空 list 状态, 否则在删除一个项目后再进入多选模式, 有的项目会消失
                LazyColumn(Modifier.height(0.dp), reorderableState.listState) { }
            }
        }

        HorizontalDividerItem()


        TextButtonItem(
            onClick = {
                state.mediaSourceTesters.toggleTest()
            },
            title = {
                if (state.mediaSourceTesters.anyTesting) {
                    Text(stringResource(Lang.settings_media_source_stop_test))
                } else {
                    Text(stringResource(Lang.settings_media_source_start_test))
                }
            },
        )
    }
}


private const val DISABLED_ALPHA = 0.38f

@Composable
internal fun SettingsScope.MediaSourceItem(
    item: MediaSourcePresentation,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = item.isEnabled,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    actions: @Composable RowScope.() -> Unit,
) {
    Item(
        modifier = modifier,
        supportingContent = {
            SelectionContainer {
                val fromSubscriptionText = stringResource(Lang.settings_media_source_from_subscription)
                Text(
                    remember(item, fromSubscriptionText) {
                        buildString {
                            val desc = item.info.description.orEmpty()
                            val subUrl = item.ownerSubscriptionUrl
                            if (subUrl != null) {
                                if (desc.isNotBlank()) {
                                    appendLine(desc)
                                }
                                append(fromSubscriptionText)
                                append(subUrl)
                            } else {
                                append(desc)
                            }
                        }
                    },
                    Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) },
                )
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggleSelected() },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .ifThen(!isEnabled) { alpha(DISABLED_ALPHA) }
                            .clip(MaterialTheme.shapes.extraSmall)
                            .size(48.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        MediaSourceIcon(item.info, Modifier.size(48.dp))
                    }
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        },
        headlineContent = {
            val disabledText = stringResource(Lang.settings_media_source_disabled)
            val name = if (!isEnabled) {
                item.info.displayName + disabledText
            } else {
                item.info.displayName
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item.instance.source.apply {
                    Icon(
                        imageVector = MediaSourceIcons.location(this.location, this.kind),
                        contentDescription = this.info.description,
                        modifier = Modifier.size(20.dp).ifThen(!isEnabled) { alpha(DISABLED_ALPHA) },
                    )
                }
                Text(
                    name,
                    Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) }.basicMarquee(),
                    textAlign = TextAlign.Center,
                )
                item.info.tier?.let { tier ->
                    MediaSourceTierTag(
                        tier = tier,
                        modifier = Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) },
                    )
                }
            }
        },
    )
}

@Composable
private fun MediaSourceTierTag(
    tier: MediaSourceTier,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text = "T${tier.value}",
            modifier = Modifier.wrapContentSize().padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            softWrap = false,
        )
    }
}

@Composable
private fun MoreOptionsDropdown(
    showMore: Boolean,
    onDismissRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    item: MediaSourcePresentation,
    onEnabledChange: (enabled: Boolean) -> Unit,
    onEdit: () -> Unit,
    /** 非 null 时菜单末尾多一项"多选"; 指针设备传 null (长按本身就是进多选). */
    onEnterSelection: (() -> Unit)? = null,
) {
    // 本菜单只从"三个点"按钮/右键打开 (长按行是进多选), 没有长按余波要吞
    DropdownMenu(
        expanded = showMore,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            leadingIcon = {
                if (item.isEnabled) {
                    Icon(Icons.Rounded.VisibilityOff, null)
                } else {
                    Icon(Icons.Rounded.Visibility, null)
                }
            },
            text = {
                if (item.isEnabled) {
                    Text(stringResource(Lang.settings_media_source_disable))
                } else {
                    Text(stringResource(Lang.settings_media_source_enable))
                }
            },
            onClick = {
                onEnabledChange(!item.isEnabled)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            text = { Text(stringResource(Lang.settings_media_source_edit)) }, // 直接点击数据源一行也可以编辑, 但还是在这里放一个按钮以免有人不知道
            onClick = {
                onEdit()
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    stringResource(Lang.settings_media_source_delete_can_readd),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            onClick = {
                onDeleteRequest()
                onDismissRequest()
            },
        )
        if (onEnterSelection != null) {
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.SelectAll, null) },
                text = { Text(stringResource(Lang.settings_media_source_enter_selection_mode)) },
                onClick = {
                    onDismissRequest()
                    onEnterSelection()
                },
            )
        }
    }
}

/**
 * 多选态下长按某一选中项弹出的批量菜单 (遥控器专用, 替代屏幕底部那条浮动工具栏).
 *
 * 菜单锚在某一行上, 动作却作用于**全部**选中项 —— 顶上那条"已选 N 项"是消歧用的, 不能省.
 */
@Composable
private fun BulkOptionsDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: MediaSourceBulkActions,
    allSelected: Boolean,
    onDeleteRequest: () -> Unit,
    /** "排序": 把菜单锚定的这一行拿起来搬运 (上下键移动, 确认/返回放下), 替代指针的拖拽把手. */
    onStartCarry: () -> Unit,
    onSelectAllChange: () -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        // 本菜单只有长按一个入口, 恒吞掉还没松手的那一下确认键
        modifier = Modifier.consumeHeldConfirmKey(),
    ) {
        Text(
            stringResource(Lang.settings_media_source_selected_count, actions.selected.size),
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Visibility, null) },
            text = { Text(stringResource(Lang.settings_media_source_enable_selected)) },
            enabled = actions.canEnable,
            onClick = {
                actions.enable()
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) },
            text = { Text(stringResource(Lang.settings_media_source_disable_selected)) },
            enabled = actions.canDisable,
            onClick = {
                actions.disable()
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    stringResource(Lang.settings_media_source_delete_selected),
                    color = MaterialTheme.colorScheme.error,
                )
            },
            enabled = actions.canDelete,
            onClick = {
                onDismissRequest()
                onDeleteRequest()
            },
        )
        HorizontalDivider()
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Reorder, null) },
            text = { Text(stringResource(Lang.settings_media_source_sort)) },
            onClick = {
                onDismissRequest()
                onStartCarry()
            },
        )
        // 全选放进来: 遥控器上没有别的办法触发它 (标题行右边那颗图标按钮要从列表往上够).
        // "退出多选"不放 —— 返回键已经是退出的出口, 语义完全一样, 多一项只是多占一个焦点位
        DropdownMenuItem(
            leadingIcon = { Icon(if (allSelected) Icons.Filled.Deselect else Icons.Filled.SelectAll, null) },
            text = {
                Text(
                    stringResource(
                        if (allSelected) {
                            Lang.settings_media_source_deselect_all
                        } else {
                            Lang.settings_media_source_select_all
                        },
                    ),
                )
            },
            onClick = {
                onSelectAllChange()
                onDismissRequest()
            },
        )
    }
}

@Composable
internal fun SelectMediaSourceTemplateDialog(
    templates: List<MediaSourceTemplate>,
    onClick: (MediaSourceTemplate) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(stringResource(Lang.settings_media_source_select_template))
        },
        // 唯一的按钮就是"取消" (= 关掉本弹窗)
        confirmButton = {
            DismissDialogButton(stringResource(Lang.settings_media_source_cancel), onDismissRequest)
        },
        text = {
            val scrollState = rememberScrollState()
            Column {
                if (scrollState.canScrollBackward) {
                    HorizontalDivider()
                }
                Column(
                    Modifier.verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    templates.forEach { item ->
                        MediaSourceCard(
                            onClick = { onClick(item) },
                            title = {
                                Text(
                                    item.info.displayName,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                            },
                            Modifier,
                            icon = {
                                Box(Modifier.clip(MaterialTheme.shapes.extraSmall).size(48.dp)) {
                                    MediaSourceIcon(item.info, Modifier.size(48.dp))
                                }
                            },
                            content = {
                                item.info.description?.let {
                                    Text(it)
                                }
                            },
                        )
                    }
                }
                if (scrollState.canScrollForward) {
                    HorizontalDivider()
                }
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun MediaSourceCard(
    onClick: () -> Unit,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ListItem(
        headlineContent = title,
        modifier.clickable(onClick = onClick),
        leadingContent = icon?.let {
            {
                Box(Modifier.wrapContentSize().size(24.dp), contentAlignment = Alignment.Center) {
                    it()
                }
            }
        },
        supportingContent = content,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Preview
@Composable
private fun PreviewSelectMediaSourceTemplateDialog() {
    SelectMediaSourceTemplateDialog(
        templates = listOf(
            MediaSourceTemplate(
                factoryId = FactoryId("1"),
                info = MediaSourceInfo("Test"),
                parameters = MediaSourceParameters.Empty,
            ),
            MediaSourceTemplate(
                factoryId = FactoryId("123"),
                info = MediaSourceInfo("Test2"),
                parameters = MediaSourceParameters.Empty,
            ),
        ),
        onClick = {},
        onDismissRequest = {},
    )
}
