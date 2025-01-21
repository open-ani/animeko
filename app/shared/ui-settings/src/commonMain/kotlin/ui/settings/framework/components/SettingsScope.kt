/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.framework.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.weaken

object SettingsDefaults {
    val groupBackgroundColor
        @Composable
        get() = MaterialTheme.colorScheme.surfaceContainerLow

    @Composable
    fun listItemColors() = ListItemDefaults.colors(containerColor = groupBackgroundColor)

    @Composable
    fun ItemIcon(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        Box(modifier.size(28.dp), contentAlignment = Alignment.Center) {
            CompositionLocalProvider(LocalContentColor providesDefault MaterialTheme.colorScheme.onSurface) {
                content()
            }
        }
    }
}

/**
 * 设置页的组件
 *
 * @see Group
 * @see TextItem
 * @see TextFieldItem
 * @see TextButtonItem
 * @see RowButtonItem
 * @see SliderItem
 * @see SwitchItem
 * @see SorterItem
 * @see DropdownItem
 */
@SettingsDsl
abstract class SettingsScope {
    companion object {
        @Stable
        val itemHorizontalPadding = 16.dp

        @Stable
        val itemExtraHorizontalPadding = 8.dp
    }

    @Stable
    @PublishedApi
    internal inline val labelAlpha get() = 0.8f

    @SettingsDsl
    @Composable
    fun Group(
        title: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        description: (@Composable () -> Unit)? = null,
        useThinHeader: Boolean = false,
        actions: (@Composable RowScope.() -> Unit)? = null,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        Surface(modifier = modifier.fillMaxWidth(), color = SettingsDefaults.groupBackgroundColor) {
            Column(Modifier.padding(vertical = if (useThinHeader) 12.dp else 16.dp)) {
                // Group header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.padding(horizontal = itemHorizontalPadding)
                            .padding(bottom = 8.dp)
                            .weight(1f)
                            .heightIn(min = if (description != null) 48.dp else 24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProvideTextStyleContentColor(
                            MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        ) {
                            Row { title() }
                        }

                        description?.let {
                            ProvideTextStyleContentColor(
                                MaterialTheme.typography.bodyMedium,
                                ListItemDefaults.colors().supportingTextColor,
                            ) {
                                Row(Modifier.padding()) { it() }
                            }
                        }
                    }

                    actions?.let {
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                            Row(
                                Modifier.height(48.dp).padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                it()
                            }
                        }
                    }
                }

                // items
                content()
            }
        }
    }

    @Composable
    inline fun SubGroup(
        content: () -> Unit,
    ) {
        Column(Modifier.padding(start = itemHorizontalPadding)) {
            content()
        }
    }

    @Composable
    fun Item(
        headlineContent: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        overlineContent: @Composable (() -> Unit)? = null,
        supportingContent: @Composable (() -> Unit)? = null,
        leadingContent: @Composable (() -> Unit)? = null,
        trailingContent: @Composable (() -> Unit)? = null,
    ) {
        ListItem(
            headlineContent = headlineContent,
            modifier,
            overlineContent,
            supportingContent = supportingContent?.let {
                {
                    Column(Modifier.padding(top = 6.dp)) {
                        it()
                    }
                }
            },
            leadingContent,
            trailingContent = trailingContent?.let {
                {
                    ProvideTextStyleContentColor(
                        MaterialTheme.typography.labelMedium,
                        ListItemDefaults.colors().supportingTextColor,
                    ) {
                        it()
                    }
                }
            },
            colors = SettingsDefaults.listItemColors(),
        )
    }

    @SettingsDsl
    @Composable
    fun HorizontalDividerItem(
        modifier: Modifier = Modifier,
        color: Color = MaterialTheme.colorScheme.outlineVariant.weaken()
    ) {
        Row(
            modifier
                .padding(horizontal = itemHorizontalPadding)
                .fillMaxWidth(), // no min 48.dp height
        ) {
            HorizontalDivider(color = color)
        }
    }
}

@DslMarker
annotation class SettingsDsl
