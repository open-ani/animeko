/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.adaptive

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarColors
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.preview.PreviewSizeClasses
import me.him188.ani.utils.platform.isMobile

/**
 * @see PopupSearchBar
 * @see SearchBar
 */
@Composable
fun AdaptiveSearchBar(
    inputField: @Composable () -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    colors: SearchBarColors = SearchBarDefaults.colors(),
    tonalElevation: Dp = SearchBarDefaults.TonalElevation,
    shadowElevation: Dp = SearchBarDefaults.ShadowElevation,
    windowInsets: WindowInsets = AniWindowInsets.forSearchBar(),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium
        && !LocalPlatform.current.isMobile() // #1104
    ) {
        PopupSearchBar(
            inputField,
            expanded,
            onExpandedChange,
            modifier,
            colors,
            tonalElevation,
            shadowElevation,
            content = content,
        )
    } else {
        SearchBar(
            inputField,
            expanded,
            onExpandedChange,
            modifier,
            shape = SearchBarDefaults.inputFieldShape,
            colors,
            tonalElevation,
            shadowElevation,
            windowInsets,
            content,
        )
    }
}

@Composable
@PreviewSizeClasses
private fun PreviewAdaptiveSearchBar() {
    PreviewImpl(false)
}

@Composable
@PreviewSizeClasses
private fun PreviewAdaptiveSearchBarExpanded() {
    PreviewImpl(true)
}

@Composable
private fun PreviewImpl(initialExpanded: Boolean) {
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(initialExpanded) }
    ProvideCompositionLocalsForPreview {
        AdaptiveSearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = query,
                    onQueryChange = { query = it.trim('\n') },
                    onSearch = { expanded = false },
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    placeholder = { Text("Hinted search text") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                )
            },
            expanded = expanded,
            onExpandedChange = { expanded = it },
        ) {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.History, contentDescription = null) },
                headlineContent = { Text("Item 1") },
                colors = ListItemDefaults.colors(containerColor = Color.Unspecified),
            )
        }
    }
}
