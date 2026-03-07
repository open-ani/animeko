/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.media.source

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.onRightClickIfSupported
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_media_source_add
import me.him188.ani.app.ui.lang.settings_media_source_cancel
import me.him188.ani.app.ui.lang.settings_media_source_cancel_sort
import me.him188.ani.app.ui.lang.settings_media_source_clear_invalid
import me.him188.ani.app.ui.lang.settings_media_source_clear_invalid_confirmation
import me.him188.ani.app.ui.lang.settings_media_source_clear_invalid_result
import me.him188.ani.app.ui.lang.settings_media_source_delete
import me.him188.ani.app.ui.lang.settings_media_source_delete_can_readd
import me.him188.ani.app.ui.lang.settings_media_source_delete_confirm
import me.him188.ani.app.ui.lang.settings_media_source_delete_no_config
import me.him188.ani.app.ui.lang.settings_media_source_delete_with_config
import me.him188.ani.app.ui.lang.settings_media_source_disable
import me.him188.ani.app.ui.lang.settings_media_source_disabled
import me.him188.ani.app.ui.lang.settings_media_source_edit
import me.him188.ani.app.ui.lang.settings_media_source_enable
import me.him188.ani.app.ui.lang.settings_media_source_from_subscription
import me.him188.ani.app.ui.lang.settings_media_source_list
import me.him188.ani.app.ui.lang.settings_media_source_list_description
import me.him188.ani.app.ui.lang.settings_media_source_save_sort
import me.him188.ani.app.ui.lang.settings_media_source_select_template
import me.him188.ani.app.ui.lang.settings_media_source_sort
import me.him188.ani.app.ui.lang.settings_media_source_start_test
import me.him188.ani.app.ui.lang.settings_media_source_stop_test
import me.him188.ani.app.ui.settings.framework.ConnectionTestResult
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.framework.rememberSorterState
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.source.FactoryId
import me.him188.ani.datasources.api.source.MediaSourceInfo
import me.him188.ani.datasources.api.source.parameter.MediaSourceParameters
import me.him188.ani.datasources.api.source.parameter.isEmpty
import me.him188.ani.utils.platform.isMobile
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

@Stable
internal val MediaSourcesUsingNewSettings = listOf(
    RssMediaSource.FactoryId,
    SelectorMediaSource.FactoryId,
)

@Composable
internal fun SettingsScope.MediaSourceGroup(
    state: MediaSourceGroupState,
    edit: EditMediaSourceState,
) {
    val navigator = LocalNavigator.current
    val uiScope = rememberCoroutineScope()
    val toaster = LocalToaster.current
    var showSelectTemplate by remember { mutableStateOf(false) }
    var showConfirmClearDialog by rememberSaveable { mutableStateOf(false) }
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

    val sorter = rememberSorterState<MediaSourcePresentation>(
        onComplete = { list -> state.reorderMediaSources(newOrder = list.map { it.instanceId }) },
    )
    val isEditTaskRunning by edit.editTaskRunningFlow.collectAsState()
    val canMutateMediaSources = !isEditTaskRunning
    val invalidSources by remember {
        derivedStateOf {
            // 不能用 remember(state.mediaSources) 缓存：tester.result 是原地变化，不会改变列表 identity。
            state.mediaSources.filter {
                it.connectionTester.result == ConnectionTestResult.FAILED
            }
        }
    }
    val isEditingUiActive = edit.editMediaSourceState != null || showSelectTemplate
    val showClearButton = remember(
        invalidSources,
        state.mediaSourceTesters.anyTesting,
        isEditTaskRunning,
        sorter.isSorting,
        isEditingUiActive,
    ) {
        invalidSources.isNotEmpty() &&
                !state.mediaSourceTesters.anyTesting &&
                !isEditTaskRunning &&
                !sorter.isSorting &&
                !isEditingUiActive
    }
    val platform = LocalPlatform.current

    Group(
        title = { Text(stringResource(Lang.settings_media_source_list, state.mediaSources.size)) },
        description = { Text(stringResource(Lang.settings_media_source_list_description)) },
        actions = {
            AniAnimatedVisibility(
                visible = sorter.isSorting,
            ) {
                Row {
                    IconButton({ sorter.cancel() }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(Lang.settings_media_source_cancel_sort),
                        )
                    }
                }
            }
            AniAnimatedVisibility(
                visible = !sorter.isSorting,
            ) {
                Row {
                    IconButton(
                        {
                            edit.cancelEdit()
                            showSelectTemplate = true
                        },
                        enabled = canMutateMediaSources,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(Lang.settings_media_source_add))
                    }
                }
            }
            Crossfade(sorter.isSorting, Modifier.animateContentSize()) { isSorting ->
                if (isSorting) {
                    Button(
                        {
                            sorter.complete()
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = stringResource(Lang.settings_media_source_save_sort),
                        )
                    }
                } else {
                    IconButton(
                        {
                            edit.cancelEdit()
                            sorter.start(state.mediaSources)
                        },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = stringResource(Lang.settings_media_source_sort),
                        )
                    }
                }
            }
        },
    ) {
        if (showConfirmClearDialog) {
            AlertDialog(
                onDismissRequest = { showConfirmClearDialog = false },
                icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                title = {
                    Text(
                        stringResource(
                            Lang.settings_media_source_clear_invalid,
                            invalidSources.size,
                        ),
                    )
                },
                text = {
                    Text(
                        stringResource(
                            Lang.settings_media_source_clear_invalid_confirmation,
                            invalidSources.size,
                        ),
                    )
                },
                confirmButton = {
                    TextButton(
                        {
                            val targets = invalidSources.toList()
                            edit.deleteMediaSources(targets) { result ->
                                toaster.toast(
                                    getString(
                                        Lang.settings_media_source_clear_invalid_result,
                                        result.deleted,
                                        result.failed,
                                    ),
                                )
                            }
                            showConfirmClearDialog = false
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
                            showConfirmClearDialog = false
                        },
                    ) { Text(stringResource(Lang.settings_media_source_cancel)) }
                },
            )
        }

        Box {
            Column(
                Modifier
                    .ifThen(sorter.isSorting) { alpha(0f) }
                    .wrapContentHeight(),
            ) {
                state.mediaSources.forEachIndexed { index, item ->
                    if (index != 0) {
                        HorizontalDividerItem()
                    }
                    val startEditing = {
                        if (item.factoryId in MediaSourcesUsingNewSettings) {
                            navigator.navigateEditMediaSource(item.factoryId, item.instanceId)
                        } else {
                            edit.startEditing(item)
                        }
                    }

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
                                        edit.deleteMediaSource(item);
                                        showConfirmDeletionDialog = false
                                    },
                                    enabled = canMutateMediaSources,
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
                        Modifier.combinedClickable(
                            enabled = canMutateMediaSources,
                            onClickLabel = "编辑",
                            onLongClick = {
                                if (platform.isMobile()) {
                                    sorter.start(state.mediaSources)
                                }
                            },
                            onLongClickLabel = "开始排序",
                            onClick = startEditing,
                        ).onRightClickIfSupported {
                            if (canMutateMediaSources) {
                                showMoreDropdown = true
                            }
                        },
                    ) {
                        IconButton({}, enabled = false) { // 放在 button 里保持 padding 一致
                            ConnectionTesterResultIndicator(
                                item.connectionTester,
                                showIdle = false,
                            )
                        }

                        Box {
                            IconButton(
                                onClick = { showMoreDropdown = true },
                                enabled = canMutateMediaSources,
                            ) {
                                Icon(
                                    Icons.Rounded.MoreVert,
                                    contentDescription = "更多",
                                )
                            }

                            MoreOptionsDropdown(
                                showMoreDropdown,
                                enabled = canMutateMediaSources,
                                onDismissRequest = { showMoreDropdown = false },
                                onDeleteRequest = { showConfirmDeletionDialog = true },
                                item,
                                onEnabledChange = { edit.toggleMediaSourceEnabled(item, it) },
                                onEdit = startEditing,
                            )
                        }
                    }
                }
            }
            if (sorter.isSorting) {
                // 往上面再盖一层, 因为 SettingsTab 已经有 scrollable 了, LazyColumn 如果不加高度限制会出错
                LazyColumn(
                    state = sorter.listState,
                    modifier = Modifier
                        .matchParentSize()
                        .reorderable(sorter.reorderableState)
                        .detectReorderAfterLongPress(sorter.reorderableState),
                ) {
                    itemsIndexed(
                        sorter.sortingData,
                        key = { _, item -> item.instanceId },
                    ) { index, item ->
                        if (index != 0) {
                            HorizontalDividerItem()
                        }
                        ReorderableItem(sorter.reorderableState, key = item.instanceId) { isDragging ->
                            val elevation = animateDpAsState(if (isDragging) 16.dp else 0.dp)
                            MediaSourceItem(
                                item,
                                Modifier
                                    .shadow(elevation.value)
                                    .background(MaterialTheme.colorScheme.surface), // match card background
                            ) {
                                Icon(
                                    Icons.Rounded.Reorder,
                                    "拖拽排序",
                                    Modifier.detectReorder(sorter.reorderableState),
                                )
                            }
                        }
                    }
                }
            } else {
                // 清空 list 状态, 否则在删除一个项目后再切换到排序状态, 有的项目会消失
                LazyColumn(Modifier.height(0.dp), sorter.listState) { }
            }
        }

        HorizontalDividerItem()

        if (showClearButton) {
            TextButtonItem(
                onClick = { showConfirmClearDialog = true },
                title = {
                    Text(
                        stringResource(Lang.settings_media_source_clear_invalid, invalidSources.size),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
            )
            HorizontalDividerItem()
        }

        TextButtonItem(
            onClick = {
                state.mediaSourceTesters.toggleTest()
            },
            Modifier.ifThen(sorter.isSorting) { alpha(0f) },
            enabled = !sorter.isSorting,
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
    actions: @Composable RowScope.() -> Unit,
) {
//    ListItem(
//        headlineContent = title,
//        leadingContent = icon?.let { { it() } },
//        supportingContent = description,
//        trailingContent = action,
//    )
    TextItem(
        modifier = modifier,
        description = {
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
        icon = {
            Box(
                Modifier.ifThen(!isEnabled) { alpha(DISABLED_ALPHA) }.clip(MaterialTheme.shapes.extraSmall).size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                MediaSourceIcon(item.info, Modifier.size(48.dp))
            }
        },
        action = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        },
        title = {
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
            }
        },
    )
}

@Composable
private fun MoreOptionsDropdown(
    showMore: Boolean,
    enabled: Boolean,
    onDismissRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    item: MediaSourcePresentation,
    onEnabledChange: (enabled: Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    DropdownMenu(
        expanded = showMore,
        onDismissRequest = onDismissRequest,
    ) {
        DropdownMenuItem(
            enabled = enabled,
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
            enabled = enabled,
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            text = { Text(stringResource(Lang.settings_media_source_edit)) }, // 直接点击数据源一行也可以编辑, 但还是在这里放一个按钮以免有人不知道
            onClick = {
                onEdit()
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            enabled = enabled,
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
