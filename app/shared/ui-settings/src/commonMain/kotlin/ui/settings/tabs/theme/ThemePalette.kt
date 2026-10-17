/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.app.ui.settings.tabs.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_theme_palette
import me.him188.ani.app.ui.theme.themeColorOptions
import org.jetbrains.compose.resources.stringResource

/** Shared palette presentation; hosts supply selection persistence and optional focus anchors. */
@Composable
fun ThemePalette(
    selectedColor: Color?,
    onSelect: (Color) -> Unit,
    modifier: Modifier = Modifier,
    colorModifier: (Int) -> Modifier = { Modifier },
) {
    val label = stringResource(Lang.settings_theme_palette)
    FlowRow(
        modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.Center,
    ) {
        AniThemeDefaults.themeColorOptions.forEachIndexed { index, color ->
            ColorButton(
                onClick = { onSelect(color) },
                baseColor = color,
                selected = selectedColor == color,
                modifier = colorModifier(index).padding(4.dp)
                    .semantics { contentDescription = "$label ${index + 1}" },
            )
        }
    }
}
