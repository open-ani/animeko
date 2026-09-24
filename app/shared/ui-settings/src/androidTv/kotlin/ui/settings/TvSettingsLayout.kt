/*
 * Copyright (C) 2026 OpenAni and contributors.
 * Use of this source code is governed by the GNU AGPLv3 license.
 */
package me.him188.ani.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.tv.ui.foundation.widgets.tvShellBackgroundColor
import org.jetbrains.compose.resources.stringResource

internal object TvSettingsDefaults {
    val HorizontalPadding = 40.dp
    val VerticalPadding = 40.dp
    val SectionWidth = 320.dp
    val SectionEndPadding = 16.dp
    val TitleSpacing = 28.dp
}

/** Nested details use detail/extra panes, preserving the parent menu beside the active content. */
@Composable
internal fun TvSettingsLayout(
    modifier: Modifier = Modifier,
    sections: @Composable () -> Unit,
    detail: @Composable ColumnScope.() -> Unit,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds().background(tvShellBackgroundColor())) {
        val listWidth = minOf(TvSettingsDefaults.SectionWidth, maxWidth * .38f)
        Row(Modifier.fillMaxSize()) {
            if (extra == null) {
                Column(
                    Modifier.width(listWidth).fillMaxHeight().testTag("tv-settings-sections")
                        .padding(
                            start = TvSettingsDefaults.HorizontalPadding, end = TvSettingsDefaults.SectionEndPadding,
                            top = TvSettingsDefaults.VerticalPadding, bottom = TvSettingsDefaults.VerticalPadding,
                        ),
                    verticalArrangement = Arrangement.spacedBy(TvSettingsDefaults.TitleSpacing),
                ) {
                    Text(
                        stringResource(Lang.settings), Modifier.fillMaxWidth().testTag("tv-settings-title"),
                        style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface,
                    )
                    sections()
                }
            }
            Column(
                (if (extra == null) Modifier.weight(1f) else Modifier.width(listWidth))
                    .fillMaxHeight().testTag("tv-settings-detail")
                    .padding(
                        start = TvSettingsDefaults.HorizontalPadding,
                        end = if (extra == null) TvSettingsDefaults.HorizontalPadding else TvSettingsDefaults.SectionEndPadding,
                        top = TvSettingsDefaults.VerticalPadding, bottom = TvSettingsDefaults.VerticalPadding,
                    ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = detail,
            )
            if (extra != null) Column(
                Modifier.weight(1f).fillMaxHeight().testTag("tv-settings-extra")
                    .padding(horizontal = TvSettingsDefaults.HorizontalPadding, vertical = TvSettingsDefaults.VerticalPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = extra,
            )
        }
    }
}
