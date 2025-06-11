/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.danmaku.DanmakuRegexFilter
import me.him188.ani.app.ui.settings.danmaku.DanmakuRegexFilterState
import me.him188.ani.app.ui.settings.danmaku.RegexFilterItem
import me.him188.ani.app.ui.settings.danmaku.isValidRegex
import me.him188.ani.app.ui.settings.framework.components.SettingsDefaults
import me.him188.ani.app.ui.subject.episode.video.settings.SideSheetLayout
import me.him188.ani.utils.platform.Uuid


@Suppress("UnusedReceiverParameter")
@Composable
fun DanmakuRegexFilterContent(
    state: DanmakuRegexFilterState,
    onAdd: (String) -> Unit,
    onDelete: (DanmakuRegexFilter) -> Unit,
    onToggle: (DanmakuRegexFilter) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    var input by rememberSaveable { mutableStateOf("") }
    val isBlank by remember { derivedStateOf { input.isBlank() } }
    val valid by remember { derivedStateOf { isValidRegex(input) } }
    var isError by remember { mutableStateOf(false) }

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
            .navigationBarsPadding()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; isError = false },
                label = { Text("正则表达式") },
                supportingText = {
                    if (isError) Text("正则表达式语法不正确。")
                    else Text("例如：‘签’ 会屏蔽含文字‘签’的弹幕。")
                },
                isError = isError,
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .onKeyEvent { e: KeyEvent ->
                        if (e.key == Key.Enter) {
                            add(); true
                        } else false
                    },
                keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
            )
            IconButton(onClick = { add() }, enabled = !isBlank) {
                Icon(Icons.Rounded.Add, contentDescription = "添加")
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.list.forEach { item ->
                RegexFilterItem(
                    item,
                    onDelete = { onDelete(item) },
                    onDisable = { onToggle(item) },
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

    val layoutModifier = if (!expanded) modifier.fillMaxWidth() else modifier

    SideSheetLayout(
        title = { Text("正则弹幕过滤管理") },
        onDismissRequest = onDismissRequest,
        modifier = layoutModifier,
        closeButton = {
            IconButton(onClick = onDismissRequest) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭")
            }
        },
    ) {
        Surface(
            Modifier.fillMaxSize(),
            color = SettingsDefaults.groupBackgroundColor,
        ) {
            DanmakuRegexFilterContent(
                state = state,
                onAdd = { regex -> state.add(DanmakuRegexFilter(Uuid.randomString(), "", regex, true)) },
                onDelete = { state.remove(it) },
                onToggle = { state.switch(it) },
            )
        }
    }
}

