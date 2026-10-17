/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.effects.moveFocusOnEnter
import me.him188.ani.app.ui.foundation.layout.cardVerticalPadding
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_rss_auto_save_hint
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_icon_url
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name
import me.him188.ani.app.ui.lang.settings_mediasource_selector_config_name_placeholder
import me.him188.ani.app.ui.lang.settings_mediasource_selector_step1
import me.him188.ani.app.ui.lang.settings_mediasource_selector_step2
import me.him188.ani.app.ui.lang.settings_mediasource_selector_step3
import me.him188.ani.app.ui.lang.settings_mediasource_selector_unsupported_format
import org.jetbrains.compose.resources.stringResource

/**
 * 数据源配置编辑器共用的尺寸、形状与基础控件. 每种数据源的编辑面板由这些控件拼成, 只描述自己有哪些字段.
 */
@Stable
object MediaSourceEditorDefaults {
    val STEP_NAME_1 @Composable get() = stringResource(Lang.settings_mediasource_selector_step1)
    val STEP_NAME_2 @Composable get() = stringResource(Lang.settings_mediasource_selector_step2)
    val STEP_NAME_3 @Composable get() = stringResource(Lang.settings_mediasource_selector_step3)

    val verticalSpacing: Dp
        @Composable
        get() = currentWindowAdaptiveInfo1().windowSizeClass.cardVerticalPadding

    val textFieldShape: Shape
        @Composable
        get() = MaterialTheme.shapes.medium

    val listItemColors: ListItemColors
        @Composable
        get() = ListItemDefaults.colors(containerColor = Color.Transparent)
}

/**
 * 编辑面板的骨架: 大图标与标题, 名称与图标地址两个字段, [content] 里的各配置区, 以及末尾的自动保存提示.
 */
@Composable
fun MediaSourceConfigurationColumn(
    state: MediaSourceConfigState<*>,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalSpacing: Dp = MediaSourceEditorDefaults.verticalSpacing,
    textFieldShape: Shape = MediaSourceEditorDefaults.textFieldShape,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(contentPadding),
    ) {
        MediaSourceHeadline(state.iconUrl, state.displayName)

        Column(
            Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(verticalSpacing)) {
                ConfigTextField(
                    state.displayName, { state.displayName = it },
                    label = stringResource(Lang.settings_mediasource_selector_config_name),
                    placeholder = stringResource(Lang.settings_mediasource_selector_config_name_placeholder),
                    isError = state.displayNameIsError,
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
                ConfigTextField(
                    state.iconUrl, { state.iconUrl = it },
                    label = stringResource(Lang.settings_mediasource_selector_config_icon_url),
                    enabled = state.enableEdit,
                    shape = textFieldShape,
                )
            }

            content()

            Row(Modifier.align(Alignment.End).padding(top = verticalSpacing, bottom = 12.dp)) {
                if (state.enableEdit) {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.labelMedium,
                        MaterialTheme.colorScheme.outline,
                    ) {
                        Text(stringResource(Lang.settings_mediasource_rss_auto_save_hint))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.titleMedium,
            MaterialTheme.colorScheme.primary,
        ) {
            Text(text)
        }
    }
}

/**
 * 配置字段的文本框. 单行字段按回车移动焦点到下一个字段; 多行字段 ([multiline]) 用于请求体等内容, 回车换行.
 */
@Composable
fun ConfigTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    placeholder: String? = null,
    isError: Boolean = false,
    enabled: Boolean = true,
    multiline: Boolean = false,
    shape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    OutlinedTextField(
        value, onValueChange,
        modifier
            .fillMaxWidth()
            .then(if (multiline) Modifier else Modifier.moveFocusOnEnter()),
        enabled = enabled,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = MaterialTheme.colorScheme.outline) } },
        supportingText = supportingText?.let { { Text(it) } },
        isError = isError,
        keyboardOptions = if (multiline) KeyboardOptions.Default else KeyboardOptions(imeAction = ImeAction.Next),
        minLines = if (multiline) 3 else 1,
        shape = shape,
    )
}

/**
 * 整数字段. 文本框保留用户的原始输入, 只在能解析且通过 [isValid] 时写回 [onValueChange].
 */
@Composable
fun ConfigLongTextField(
    value: Long,
    onValueChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    isValid: (Long) -> Boolean = { true },
    enabled: Boolean = true,
    shape: Shape = MediaSourceEditorDefaults.textFieldShape,
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val parsed = text.toLongOrNull()
    ConfigTextField(
        text,
        {
            text = it
            it.toLongOrNull()?.takeIf(isValid)?.let(onValueChange)
        },
        label, modifier, supportingText,
        isError = parsed == null || !isValid(parsed),
        enabled = enabled,
        shape = shape,
    )
}

@Composable
fun SwitchListItem(
    headline: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    switchModifier: Modifier = Modifier,
    colors: ListItemColors = MediaSourceEditorDefaults.listItemColors,
) {
    ListItem(
        headlineContent = { Text(headline) },
        modifier.focusable(false).clickable(enabled = enabled) { onCheckedChange(!checked) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            Switch(checked, onCheckedChange, switchModifier, enabled = enabled)
        },
        colors = colors,
    )
}

/**
 * 点击后弹出下拉菜单在 [options] 中选择一项.
 */
@Composable
fun <T> DropdownListItem(
    headline: String,
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    enabled: Boolean = true,
    colors: ListItemColors = MediaSourceEditorDefaults.listItemColors,
    display: (T) -> String,
) {
    var showMenu by rememberSaveable { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(headline) },
        modifier.focusable(false).clickable(enabled = enabled) { showMenu = !showMenu },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = {
            TextButton(onClick = { showMenu = true }, enabled = enabled) {
                Text(display(selected))
            }
            if (showMenu) {
                DropdownMenu(showMenu, { showMenu = false }) {
                    for (option in options) {
                        DropdownMenuItem(
                            text = { Text(display(option)) },
                            onClick = {
                                onSelect(option)
                                showMenu = false
                            },
                        )
                    }
                }
            }
        },
        colors = colors,
    )
}

/**
 * 在几种配置格式 (条目格式、线路格式等) 之间单选的分段按钮.
 */
@Composable
fun <F> FormatSelectionButtonRow(
    formats: List<F>,
    isSelected: (F) -> Boolean,
    onSelect: (F) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonModifier: (F) -> Modifier = { Modifier },
    label: @Composable (F) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(modifier) {
        for ((index, format) in formats.withIndex()) {
            val selected = isSelected(format)
            SegmentedButton(
                selected,
                { onSelect(format) },
                SegmentedButtonDefaults.itemShape(index, formats.size),
                buttonModifier(format),
                icon = { SegmentedButtonDefaults.Icon(selected) },
                label = { label(format) },
                enabled = enabled,
            )
        }
    }
}

/**
 * 当前版本不认识的格式 ID (通常来自更新版本导出的配置).
 */
@Composable
fun UnsupportedFormatIdHint(formatId: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        ProvideTextStyleContentColor(MaterialTheme.typography.bodyLarge, MaterialTheme.colorScheme.error) {
            Icon(
                Icons.Rounded.Error, null,
                Modifier.align(Alignment.CenterHorizontally).size(48.dp),
            )
            Text(
                stringResource(Lang.settings_mediasource_selector_unsupported_format, formatId),
                Modifier.padding(top = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}
