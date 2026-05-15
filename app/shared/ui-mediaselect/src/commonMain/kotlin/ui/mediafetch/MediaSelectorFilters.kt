/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.mediafetch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.him188.ani.app.ui.foundation.dialogs.PlatformPopupProperties
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.media_selector_filter_alliance
import me.him188.ani.app.ui.lang.media_selector_filter_clear
import me.him188.ani.app.ui.lang.media_selector_filter_expand
import me.him188.ani.app.ui.lang.media_selector_filter_resolution
import me.him188.ani.app.ui.lang.media_selector_filter_selected
import me.him188.ani.app.ui.lang.media_selector_filter_subtitle
import me.him188.ani.app.ui.media.rememberMediaDetailsStrings
import me.him188.ani.app.ui.media.renderSubtitleLanguage
import org.jetbrains.compose.resources.stringResource


private inline val minWidth get() = 60.dp
private inline val maxWidth get() = 120.dp

/**
 * 筛选
 */
@Composable
fun MediaSelectorFilters(
    resolution: MediaPreferenceItemState<String>,
    subtitleLanguageId: MediaPreferenceItemState<String>,
    alliance: MediaPreferenceItemState<String>,
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
            allValues = { resolutionPresentation.available },
            onSelect = { scope.launch { resolution.prefer(it) } },
            onDeselect = { scope.launch { resolution.removePreference() } },
            name = { Text(resolutionText) },
            Modifier.widthIn(min = minWidth, max = maxWidth),
        )
        val subtitleLanguagePresentation by subtitleLanguageId.presentationFlow.collectAsStateWithLifecycle()
        MediaSelectorFilterChip(
            selected = subtitleLanguagePresentation.finalSelected,
            allValues = { subtitleLanguagePresentation.available },
            onSelect = { scope.launch { subtitleLanguageId.prefer(it) } },
            onDeselect = { scope.launch { subtitleLanguageId.removePreference() } },
            name = { Text(subtitleText) },
            Modifier.widthIn(min = minWidth, max = maxWidth),
            label = { MediaSelectorFilterChipText(renderSubtitleLanguage(it, mediaDetailsStrings)) },
        )
        val alliancePresentation by alliance.presentationFlow.collectAsStateWithLifecycle()
        MediaSelectorFilterChip(
            selected = alliancePresentation.finalSelected,
            allValues = { alliancePresentation.available },
            onSelect = { scope.launch { alliance.prefer(it) } },
            onDeselect = { scope.launch { alliance.removePreference() } },
            name = { Text(allianceText) },
            Modifier.widthIn(min = minWidth, max = maxWidth),
        )
    }

    if (singleLine) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    } else {
        FlowRow(
            modifier,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun MediaSelectorFilterChipText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        overflow = TextOverflow.Clip,
        softWrap = false,
        maxLines = 1,
        modifier = modifier,
    )
}

/**
 * @param selected 选中的值, 为 null 时表示未选中
 * @param name 未被选中时显示
 * @param label 选中时显示
 */
@Composable
private fun <T : Any> MediaSelectorFilterChip(
    selected: T?,
    allValues: () -> List<T>,
    onSelect: (T) -> Unit,
    onDeselect: (T) -> Unit,
    name: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (T) -> Unit = { MediaSelectorFilterChipText(it.toString()) },
    leadingIcon: @Composable ((T?) -> Unit)? = null,
) {
    var showDropdown by rememberSaveable {
        mutableStateOf(false)
    }
    val expandText = stringResource(Lang.media_selector_filter_expand)
    val clearText = stringResource(Lang.media_selector_filter_clear)
    val selectedText = stringResource(Lang.media_selector_filter_selected)

    val allValuesState by remember(allValues) {
        derivedStateOf(allValues)
    }
    val isSingleValue by remember { derivedStateOf { allValuesState.size == 1 } }
    val selectedState by rememberUpdatedState(selected)

    Box {
        val chipSelected = isSingleValue || selected != null
        InputChip(
            selected = chipSelected,
            onClick = {
                if (!isSingleValue) {
                    showDropdown = true
                }
            },
            label = {
                if (isSingleValue) {
                    allValuesState.firstOrNull()?.let {
                        label(it)
                    }
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier.alpha(if (selectedState == null) 1f else 0f), // 总是占位
                        ) {
                            name()
                        }
                        selectedState?.let {
                            label(it)
                        }
                    }
                }
            },
            leadingIcon = leadingIcon?.let { { leadingIcon(selectedState) } },
            trailingIcon = if (isSingleValue) null else {
                {
                    if (selected == null) {
                        Icon(Icons.Default.ArrowDropDown, expandText)
                    } else {
                        Icon(
                            Icons.Default.Close, clearText,
                            Modifier.clickable { selectedState?.let { onDeselect(it) } },
                        )
                    }
                }
            },
            modifier = modifier.heightIn(min = 40.dp),
            border = InputChipDefaults.inputChipBorder(
                enabled = true, chipSelected,
                // M3 spec is outlineVariant, but we use outline for prominent visual
                borderColor = MaterialTheme.colorScheme.outline,
            ),
        )

        DropdownMenu(
            showDropdown,
            onDismissRequest = { showDropdown = false },
            properties = PlatformPopupProperties(clippingEnabled = false),
        ) {
            allValuesState.forEach { item ->
                DropdownMenuItem(
                    text = { label(item) },
                    trailingIcon = {
                        if (selectedState == item) {
                            Icon(Icons.Default.Check, selectedText)
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
