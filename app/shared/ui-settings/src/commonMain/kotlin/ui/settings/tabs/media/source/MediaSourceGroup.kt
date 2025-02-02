/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.rss.RssMediaSource
import me.him188.ani.app.domain.mediasource.web.SelectorMediaSource
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.interaction.onRightClickIfSupported
import me.him188.ani.app.ui.settings.framework.ConnectionTesterResultIndicator
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.TextButtonItem
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.settings.framework.rememberSorterState
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcon
import me.him188.ani.app.ui.settings.rendering.MediaSourceIcons
import me.him188.ani.datasources.api.source.parameter.isEmpty
import me.him188.ani.utils.platform.isMobile
import org.burnoutcrew.reorderable.ReorderableItem
import org.burnoutcrew.reorderable.detectReorder
import org.burnoutcrew.reorderable.detectReorderAfterLongPress
import org.burnoutcrew.reorderable.reorderable

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

    val sorter = rememberSorterState<MediaSourcePresentation>(
        onComplete = { list -> state.reorderMediaSources(newOrder = list.map { it.instanceId }) },
    )

    Group(
        title = { Text("数据源列表 (${state.mediaSources.size})") },
        description = { Text("在播放时，禁用的数据源不会自动查询，但可手动点击临时启用。注意，排序将优先采用云端顺序，同数据源订阅之内的手动排序可能无效") },
        actions = {
            AniAnimatedVisibility(
                visible = sorter.isSorting,
            ) {
                Row {
                    IconButton({ sorter.cancel() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "取消排序")
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
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加数据源")
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
                        Icon(Icons.Rounded.Check, contentDescription = "保存排序")
                    }
                } else {
                    IconButton(
                        {
                            edit.cancelEdit()
                            sorter.start(state.mediaSources)
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "排序")
                    }
                }
            }
        },
    ) {
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
                    val platform = LocalPlatform.current

                    var showMoreDropdown by remember { mutableStateOf(false) }
                    var showConfirmDeletionDialog by rememberSaveable { mutableStateOf(false) }
                    if (showConfirmDeletionDialog) {
                        AlertDialog(
                            onDismissRequest = { showConfirmDeletionDialog = false },
                            icon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            title = { Text("删除数据源") },
                            text = {
                                if (item.parameters.isEmpty()) {
                                    Text("该数据源无特殊配置，删除后可以重新从模板直接添加，确认删除吗？")
                                } else {
                                    Text("该数据源有配置，删除后将丢失配置，之后从模板添加时需要重新配置，确认删除吗？")
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    {
                                        edit.deleteMediaSource(item);
                                        showConfirmDeletionDialog = false
                                    },
                                ) {
                                    Text(
                                        "删除",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            },
                            dismissButton = {
                                TextButton(
                                    {
                                        showConfirmDeletionDialog = false
                                    },
                                ) { Text("取消") }
                            },
                        )
                    }

                    MediaSourceItem(
                        item,
                        Modifier.combinedClickable(
                            onClickLabel = "编辑",
                            onLongClick = {
                                if (platform.isMobile()) {
                                    sorter.start(state.mediaSources)
                                }
                            },
                            onLongClickLabel = "开始排序",
                            onClick = startEditing,
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
                                    contentDescription = "更多",
                                )
                            }

                            MoreOptionsDropdown(
                                showMoreDropdown,
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


        TextButtonItem(
            onClick = {
                state.mediaSourceTesters.toggleTest()
            },
            Modifier.ifThen(sorter.isSorting) { alpha(0f) },
            enabled = !sorter.isSorting,
            title = {
                if (state.mediaSourceTesters.anyTesting) {
                    Text("终止测试")
                } else {
                    Text("开始测试")
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
                Text(
                    remember(item) {
                        buildString {
                            val desc = item.info.description.orEmpty()
                            val subUrl = item.ownerSubscriptionUrl
                            if (subUrl != null) {
                                if (desc.isNotBlank()) {
                                    appendLine(desc)
                                }
                                append("来自订阅，不可编辑 ")
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
            val name = if (!isEnabled) {
                item.info.displayName + "（已禁用）"
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
            leadingIcon = {
                if (item.isEnabled) {
                    Icon(Icons.Rounded.VisibilityOff, null)
                } else {
                    Icon(Icons.Rounded.Visibility, null)
                }
            },
            text = {
                if (item.isEnabled) {
                    Text("禁用")
                } else {
                    Text("启用")
                }
            },
            onClick = {
                onEnabledChange(!item.isEnabled)
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            text = { Text("编辑") }, // 直接点击数据源一行也可以编辑, 但还是在这里放一个按钮以免有人不知道
            onClick = {
                onEdit()
                onDismissRequest()
            },
        )
        DropdownMenuItem(
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            text = { Text("删除（可重新添加）", color = MaterialTheme.colorScheme.error) },
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
            Text("选择模板")
        },
        confirmButton = {
            TextButton(onDismissRequest) {
                Text("取消")
            }
        },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(100.dp),
                Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(templates) { item ->
                    MediaSourceCard(
                        onClick = { onClick(item) },
                        title = {
                            Text(
                                item.info.displayName,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        },
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
    Card(onClick, modifier) {
        Column(
            Modifier.align(Alignment.CenterHorizontally).padding(all = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.wrapContentSize().align(Alignment.CenterHorizontally),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                icon?.let {
                    Box(Modifier.wrapContentSize().size(24.dp), contentAlignment = Alignment.Center) {
                        it()
                    }
                }
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    title()
                }
            }

            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                content()
            }
        }
    }
}