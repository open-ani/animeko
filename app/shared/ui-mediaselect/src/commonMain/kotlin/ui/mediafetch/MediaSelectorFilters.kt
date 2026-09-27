/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_bt_filter_any
import me.him188.ani.app.ui.lang.media_selector_filter_alliance
import me.him188.ani.app.ui.lang.media_selector_filter_expand
import me.him188.ani.app.ui.lang.media_selector_filter_resolution
import me.him188.ani.app.ui.lang.media_selector_filter_selected
import me.him188.ani.app.ui.lang.media_selector_filter_subtitle
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import org.jetbrains.compose.resources.stringResource

private inline val maxChipWidth get() = 160.dp

/**
 * 分辨率 / 字幕 / 字幕组三个下拉 chip, 只在 BT 页表格模式使用. 候选值由调用方给出 (BT 页从基础列表自算).
 * chip 选中态只靠填充色, 尾部恒为下拉箭头; 菜单首项「不限」= removePreference, 其后各值带 Check.
 *
 * @param singleLine 为 true 时排成一行 (不换行, 由调用方负责横向滚动); 否则 FlowRow.
 */
@Composable
fun MediaSelectorFilters(
    resolution: MediaPreferenceItemState<String>,
    subtitleLanguageId: MediaPreferenceItemState<String>,
    alliance: MediaPreferenceItemState<String>,
    availableResolutions: List<String>,
    availableSubtitleLanguageIds: List<String>,
    availableAlliances: List<String>,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val mediaDetailsStrings = rememberMediaDetailsStrings()
    val resolutionText = stringResource(Lang.media_selector_filter_resolution)
    val subtitleText = stringResource(Lang.media_selector_filter_subtitle)
    val allianceText = stringResource(Lang.media_selector_filter_alliance)
    val content = @Composable {
        val resolutionPresentation by resolution.presentationFlow.collectAsStateWithLifecycle()
        MediaSelectorFilterChip(
            selected = resolutionPresentation.finalSelected,
            allValues = availableResolutions,
            onSelect = { scope.launch { resolution.prefer(it) } },
            onDeselect = { scope.launch { resolution.removePreference() } },
            name = resolutionText,
        )
        val subtitleLanguagePresentation by subtitleLanguageId.presentationFlow.collectAsStateWithLifecycle()
        MediaSelectorFilterChip(
            selected = subtitleLanguagePresentation.finalSelected,
            allValues = availableSubtitleLanguageIds,
            onSelect = { scope.launch { subtitleLanguageId.prefer(it) } },
            onDeselect = { scope.launch { subtitleLanguageId.removePreference() } },
            name = subtitleText,
            label = { renderSubtitleLanguage(it, mediaDetailsStrings) },
        )
        val alliancePresentation by alliance.presentationFlow.collectAsStateWithLifecycle()
        MediaSelectorFilterChip(
            selected = alliancePresentation.finalSelected,
            allValues = availableAlliances,
            onSelect = { scope.launch { alliance.prefer(it) } },
            onDeselect = { scope.launch { alliance.removePreference() } },
            name = allianceText,
        )
    }

    if (singleLine) {
        Row(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/**
 * @param selected 选中的值, 为 null 时表示未选中
 * @param name 未被选中时显示
 * @param label 选中时显示
 */
@Composable
private fun MediaSelectorFilterChip(
    selected: String?,
    allValues: List<String>,
    onSelect: (String) -> Unit,
    onDeselect: () -> Unit,
    name: String,
    modifier: Modifier = Modifier,
    label: (String) -> String = { it },
) {
    var showDropdown by remember { mutableStateOf(false) }
    val expandText = stringResource(Lang.media_selector_filter_expand)
    val selectedText = stringResource(Lang.media_selector_filter_selected)
    val anyText = stringResource(Lang.media_selector_bt_filter_any)
    // 当前偏好不在候选里时追加显示, 让用户能看到并取消它.
    val shownValues = if (selected != null && selected !in allValues) allValues + selected else allValues

    Box(modifier) {
        FilterChip(
            selected = selected != null,
            onClick = { showDropdown = true },
            label = {
                Text(
                    selected?.let(label) ?: name,
                    Modifier.widthIn(max = maxChipWidth),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, expandText) },
        )

        DropdownMenu(
            showDropdown,
            onDismissRequest = { showDropdown = false },
            properties = PlatformPopupProperties(clippingEnabled = false),
        ) {
            DropdownMenuItem(
                text = { Text(anyText) },
                trailingIcon = {
                    if (selected == null) {
                        Icon(Icons.Rounded.Check, selectedText)
                    }
                },
                onClick = {
                    onDeselect()
                    showDropdown = false
                },
            )
            for (item in shownValues) {
                DropdownMenuItem(
                    text = { Text(label(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    trailingIcon = {
                        if (selected == item) {
                            Icon(Icons.Rounded.Check, selectedText)
                        }
                    },
                    onClick = {
                        onSelect(item)
                        showDropdown = false
                    },
                )
            }
        }
    }
}
