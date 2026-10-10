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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.onRightClickIfSupported
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_add
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_custom
import me.him188.ani.app.ui.lang.settings_media_source_delete
import me.him188.ani.app.ui.lang.settings_media_source_delete_confirm
import me.him188.ani.app.ui.lang.settings_media_source_delete_no_config
import me.him188.ani.app.ui.lang.settings_media_source_delete_with_config
import me.him188.ani.app.ui.lang.settings_media_source_deselect_all
import me.him188.ani.app.ui.lang.settings_media_source_disable
import me.him188.ani.app.ui.lang.settings_media_source_disabled_label
import me.him188.ani.app.ui.lang.settings_media_source_done
import me.him188.ani.app.ui.lang.settings_media_source_edit
import me.him188.ani.app.ui.lang.settings_media_source_enable
import me.him188.ani.app.ui.lang.settings_media_source_enter_selection_mode
import me.him188.ani.app.ui.lang.settings_media_source_more
import me.him188.ani.app.ui.lang.settings_media_source_select_all
import me.him188.ani.app.ui.lang.settings_media_source_select_template
import me.him188.ani.app.ui.lang.settings_media_source_selected_count
import me.him188.ani.app.ui.lang.settings_media_source_sort
import me.him188.ani.app.ui.lang.settings_media_source_stop_test
import me.him188.ani.app.ui.lang.settings_media_source_test_connection
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.ConnectionTesterRunner
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.app.ui.settings.rendering.MediaSourceTierTag
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceInfo
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

/**
 * 数据源设置页: 订阅和自定义数据源两组, 中间用分割线隔开.
 *
 * @param backgroundColor 页面的背景色, 见 [MediaSourceSubscriptionGroup] 和 [MediaSourceGroup].
 */
@Composable
internal fun SettingsScope.MediaSourceTab(
    subscriptionState: MediaSourceSubscriptionGroupState,
    groupState: MediaSourceGroupState,
    editState: EditMediaSourceState,
    selectionState: MediaSourceSelectionState,
    backgroundColor: Color,
    onOpenSubscription: (subscriptionId: String) -> Unit,
) {
    // 两组属于同一个页面, 不使用设置页默认的组间距
    Column {
        MediaSourceSubscriptionGroup(
            subscriptionState,
            mediaSourcesOfSubscription = groupState::mediaSourcesOfSubscription,
            onOpenSubscription = onOpenSubscription,
            backgroundColor = backgroundColor,
        )
        HorizontalDividerItem()
        MediaSourceGroup(groupState, editState, selectionState, backgroundColor)
    }
}

/**
 * 不属于订阅的数据源. 用户可以添加, 编辑, 删除, 也可以在多选模式下拖动排序.
 * 订阅中的数据源在订阅详情页 [MediaSourceSubscriptionPage] 中显示.
 *
 * @param backgroundColor 页面的背景色. 多选模式下未选中的行使用它, 拖动时行不透明.
 */
@Composable
internal fun SettingsScope.MediaSourceGroup(
    state: MediaSourceGroupState,
    edit: EditMediaSourceState,
    selectionState: MediaSourceSelectionState,
    backgroundColor: Color,
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

    val mediaSources = state.localMediaSources

    // 多选模式下的列表数据. 拖拽排序时先在本地重排, 拖拽结束后再持久化.
    var reorderData by remember { mutableStateOf(mediaSources) }
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
    val allSelected = mediaSources.isNotEmpty() &&
        mediaSources.all { it.instanceId in selectionState.selectedIds }

    // 组合在页面导航的 BackHandler 之后, 保证多选模式下返回键优先退出多选, 而不是退出设置页
    BackHandler(enabled = selectionState.inSelection) {
        selectionState.clear()
    }

    LaunchedEffect(mediaSources, selectionState.inSelection) {
        reorderData = mediaSources
        if (selectionState.inSelection) {
            selectionState.retainSelection(mediaSources.mapTo(mutableSetOf()) { it.instanceId })
        }
    }

    Group(
        title = {
            if (selectionState.inSelection) {
                Text(stringResource(Lang.settings_media_source_selected_count, selectionState.selectedIds.size))
            } else {
                Text(stringResource(Lang.settings_media_source_custom))
            }
        },
        actions = {
            if (selectionState.inSelection) {
                TextButton(
                    onClick = {
                        if (allSelected) {
                            selectionState.selectAll(emptyList())
                        } else {
                            selectionState.selectAll(mediaSources.map { it.instanceId })
                        }
                    },
                    enabled = mediaSources.isNotEmpty(),
                    modifier = Modifier.testTag(MediaSourceGroupTestTags.SELECT_ALL),
                ) {
                    Text(
                        stringResource(
                            if (allSelected) {
                                Lang.settings_media_source_deselect_all
                            } else {
                                Lang.settings_media_source_select_all
                            },
                        ),
                    )
                }
                TextButton(
                    onClick = { selectionState.clear() },
                    modifier = Modifier.testTag(MediaSourceGroupTestTags.EXIT_SELECTION),
                ) {
                    Text(stringResource(Lang.settings_media_source_done))
                }
            } else {
                IconButton(
                    {
                        edit.cancelEdit()
                        selectionState.enterSelection()
                    },
                    enabled = mediaSources.isNotEmpty(),
                    modifier = Modifier.testTag(MediaSourceGroupTestTags.ENTER_SELECTION),
                ) {
                    Icon(
                        Icons.Rounded.Checklist,
                        contentDescription = stringResource(Lang.settings_media_source_enter_selection_mode),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Box {
            // 多选模式下仅用于撑起高度, 实际显示与交互由上面的 LazyColumn 承担
            Column(
                Modifier
                    .ifThen(selectionState.inSelection) { alpha(0f) }
                    .wrapContentHeight(),
            ) {
                mediaSources.forEach { item ->
                    val openConfiguration = { navigator.openMediaSourceConfiguration(item, edit) }
                    val editText = stringResource(Lang.settings_media_source_edit)
                    val enterSelectionText = stringResource(Lang.settings_media_source_enter_selection_mode)
                    val moreText = stringResource(Lang.settings_media_source_more)

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
                            dismissButton = {
                                TextButton(
                                    {
                                        showConfirmDeletionDialog = false
                                    },
                                ) { Text(stringResource(Lang.settings_media_source_cancel)) }
                            },
                        )
                    }

                    MediaSourceItem(
                        item,
                        Modifier
                            .testTag(MediaSourceGroupTestTags.item(item.instanceId))
                            .combinedClickable(
                                onClickLabel = editText,
                                onLongClick = {
                                    selectionState.enterSelectionWith(item.instanceId)
                                },
                                onLongClickLabel = enterSelectionText,
                                onClick = openConfiguration,
                            ).onRightClickIfSupported {
                                showMoreDropdown = true
                            },
                    ) {
                        IconButton({}, enabled = false) { // 放在 button 里保持 padding 一致
                            ConnectionTesterResultIndicator(
                                item.connectionTester,
                                showIdle = false,
                            )
                        }

                        Box {
                            IconButton(onClick = { showMoreDropdown = true }) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = moreText,
                                )
                            }

                            DropdownMenu(showMoreDropdown, onDismissRequest = { showMoreDropdown = false }) {
                                ToggleEnabledMenuItem(
                                    item,
                                    onEnabledChange = { edit.toggleMediaSourceEnabled(item, it) },
                                    onDismissRequest = { showMoreDropdown = false },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(Lang.settings_media_source_edit)) },
                                    onClick = {
                                        showMoreDropdown = false
                                        openConfiguration()
                                    },
                                )
                                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            stringResource(Lang.settings_media_source_delete_confirm),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    },
                                    onClick = {
                                        showMoreDropdown = false
                                        showConfirmDeletionDialog = true
                                    },
                                )
                            }
                        }
                    }
                }
            }
            if (selectionState.inSelection) {
                // 往上面再盖一层, 因为 SettingsTab 已经有 scrollable 了, LazyColumn 如果不加高度限制会出错
                LazyColumn(
                    state = reorderableState.listState,
                    modifier = Modifier
                        .matchParentSize()
                        .reorderable(reorderableState),
                ) {
                    items(
                        reorderData,
                        key = { item -> item.instanceId },
                    ) { item ->
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
                                            backgroundColor
                                        },
                                    )
                                    .clickable { selectionState.toggleSelection(item.instanceId) },
                                leading = {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { selectionState.toggleSelection(item.instanceId) },
                                    )
                                },
                            ) {
                                Icon(
                                    Icons.Rounded.DragHandle,
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

        if (!selectionState.inSelection) {
            Row(
                Modifier.fillMaxWidth().padding(end = SettingsScope.itemHorizontalPadding).padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TestConnectionButton(state.localMediaSourceTesters)
                TextButton(
                    {
                        edit.cancelEdit()
                        showSelectTemplate = true
                    },
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Lang.settings_media_source_add))
                }
            }
        }
    }
}

internal fun AniNavigator.openMediaSourceConfiguration(item: MediaSourcePresentation, edit: EditMediaSourceState) {
    if (item.factoryId in MediaSourcesUsingNewSettings) {
        navigateEditMediaSource(item.factoryId, item.instanceId)
    } else {
        edit.startEditing(item)
    }
}

@Composable
internal fun TestConnectionButton(testers: ConnectionTesterRunner<*>) {
    TextButton({ testers.toggleTest() }) {
        Icon(
            if (testers.anyTesting) Icons.Rounded.Stop else Icons.Rounded.NetworkCheck,
            contentDescription = null,
            Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        if (testers.anyTesting) {
            Text(stringResource(Lang.settings_media_source_stop_test))
        } else {
            Text(stringResource(Lang.settings_media_source_test_connection))
        }
    }
}

@Composable
internal fun ToggleEnabledMenuItem(
    item: MediaSourcePresentation,
    onEnabledChange: (enabled: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            if (item.isEnabled) {
                Text(stringResource(Lang.settings_media_source_disable))
            } else {
                Text(stringResource(Lang.settings_media_source_enable))
            }
        },
        onClick = {
            onDismissRequest()
            onEnabledChange(!item.isEnabled)
        },
    )
}

internal const val DISABLED_ALPHA = 0.38f

/**
 * 数据源列表中的一行. 禁用的数据源整行内容使用禁用样式, [actions] 不受影响, 以便用户重新启用.
 *
 * @param leading 显示在图标之前, 例如多选模式下的复选框.
 */
@Composable
internal fun SettingsScope.MediaSourceItem(
    item: MediaSourcePresentation,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val contentAlpha = if (item.isEnabled) 1f else DISABLED_ALPHA
    Item(
        modifier = modifier,
        supportingContent = item.info.description?.takeIf { it.isNotBlank() }?.let { description ->
            {
                Text(
                    description,
                    Modifier.alpha(contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Box(
                    Modifier
                        .alpha(contentAlpha)
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    MediaSourceIcon(item.info, Modifier.fillMaxSize())
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        },
        headlineContent = {
            Row(
                Modifier.alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item.instance.source.apply {
                    Icon(
                        imageVector = MediaSourceIcons.location(this.location, this.kind),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    item.info.displayName,
                    Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.info.tier?.let { tier ->
                    MediaSourceTierTag(tier = tier)
                }
                if (!item.isEnabled) {
                    Text(
                        stringResource(Lang.settings_media_source_disabled_label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false,
                    )
                }
            }
        },
    )
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
        confirmButton = {
            TextButton(onDismissRequest) {
                Text(stringResource(Lang.settings_media_source_cancel))
            }
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
