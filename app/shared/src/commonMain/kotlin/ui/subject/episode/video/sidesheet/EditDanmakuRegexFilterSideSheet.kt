/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.subject.episode.video.sidesheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.settings.danmaku.isValidRegex
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout
import me.him188.ani.utils.platform.Uuid
import me.him188.ani.utils.platform.annotations.TestOnly


@Suppress("UnusedReceiverParameter")
@Composable
fun DanmakuRegexFilterContent(
    state: DanmakuRegexFilterState,
    onAdd: (String) -> Unit,
    onDelete: (DanmakuRegexFilter) -> Unit,
    onToggle: (DanmakuRegexFilter) -> Unit,
    expanded: Boolean
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var input by rememberSaveable { mutableStateOf("") }
    val isBlank by remember { derivedStateOf { input.isBlank() } }
    val valid by remember { derivedStateOf { isValidRegex(input) } }
    var isError by remember { mutableStateOf(false) }

    val isPortrait = !expanded

    fun add() {
        if (!isBlank && valid) {
            isError = false
            onAdd(input)
            input = ""
        } else {
            isError = true
        }
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = if (isPortrait) 12.dp else 16.dp)
            .padding(top = if (isPortrait) 8.dp else 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(if (isPortrait) 12.dp else 16.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; isError = false },
            placeholder = { Text("输入要屏蔽的弹幕关键词（正则）") },
            label = { Text("正则表达式") },
            supportingText = {
                if (isError) Text("正则表达式语法不正确。")
                else Text("例如：‘签’ 会屏蔽含文字‘签’的弹幕。")
            },
            isError = isError,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                        add(); true
                    } else false
                },
            trailingIcon = {
                IconButton(onClick = { add() }, enabled = !isBlank && valid) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加")
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add() }),
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.list.forEach { item ->
                AssistChip(
                    onClick = { onToggle(item) },
                    label = {
                        Text(
                            item.regex,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        if (!item.enabled) {
                            Icon(Icons.Rounded.VisibilityOff, contentDescription = null)
                        }
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = { onDelete(item) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "删除", modifier = Modifier.size(16.dp))
                        }
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (item.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = if (item.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}


@Composable
fun DanmakuRegexFilterSettings(
    state: DanmakuRegexFilterState,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean,
) {
    val isPortrait = !expanded
    val backgroundColor = if (isPortrait) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHigh
    val layoutModifier = if (isPortrait) modifier.fillMaxWidth() else modifier

    SideSheetLayout(
        title = { Text("正则弹幕过滤管理") },
        onDismissRequest = onDismissRequest,
        modifier = layoutModifier,
        containerColor = backgroundColor,
        closeButton = {
            if (expanded) {
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Rounded.Close, contentDescription = "关闭")
                }
            }
        },
    ) {
        DanmakuRegexFilterContent(
            state = state,
            expanded = expanded,
            onAdd = { regex -> state.add(DanmakuRegexFilter(Uuid.randomString(), "", regex, true)) },
            onDelete = { state.remove(it) },
            onToggle = { state.switch(it) },
        )
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview(device = "spec:width=1280dp,height=800dp,dpi=240")
fun PreviewEditDanmakuRegexFilterSideSheet() {
    ProvideCompositionLocalsForPreview {
        DanmakuRegexFilterSettings(
            state = createTestDanmakuRegexFilterState(),
            onDismissRequest = { },
            expanded = true,
        )
    }
}

