/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.mediasource.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import me.him188.ani.app.domain.mediasource.codec.MediaSourceArguments
import me.him188.ani.app.ui.foundation.interaction.WindowDragArea
import me.him188.ani.app.ui.foundation.layout.ListDetailAnimatedPane
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthCompact
import me.him188.ani.app.ui.foundation.layout.materialWindowMarginPadding
import me.him188.ani.app.ui.foundation.layout.withoutAutoFocus
import me.him188.ani.app.ui.foundation.navigation.BackHandler
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.settings_mediasource_selector_more
import me.him188.ani.app.ui.lang.settings_mediasource_selector_test
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuExport
import me.him188.ani.app.ui.settings.mediasource.DropdownMenuImport
import me.him188.ani.app.ui.settings.mediasource.ExportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.ImportMediaSourceState
import me.him188.ani.app.ui.settings.mediasource.MediaSourceConfigurationDefaults
import org.jetbrains.compose.resources.stringResource

/**
 * 数据源编辑页的骨架: 顶栏 (标题、紧凑窗口下的 "测试" 入口、导入导出菜单) 与左配置右测试的双窗格布局.
 *
 * @param topBarOverride 非 `null` 时整个顶栏由它绘制 (例如详情页在紧凑窗口下的专用顶栏), 传入的是应当放在顶栏上的导航图标.
 */
@Composable
fun <A : MediaSourceArguments> MediaSourceEditorScaffold(
    title: @Composable () -> Unit,
    importState: ImportMediaSourceState<A>,
    exportState: ExportMediaSourceState,
    modifier: Modifier = Modifier,
    navigator: ThreePaneScaffoldNavigator<*> = rememberListDetailPaneScaffoldNavigator(),
    windowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    navigationIcon: @Composable () -> Unit = {},
    enableImportExport: Boolean = true,
    topBarOverride: (@Composable (navigationIcon: @Composable () -> Unit) -> Unit)? = null,
    listPane: @Composable (contentPadding: PaddingValues) -> Unit,
    detailPane: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    Scaffold(
        modifier,
        topBar = {
            WindowDragArea {
                val myNavigationIcon = @Composable {
                    if (navigator.canNavigateBack()) {
                        BackNavigationIconButton(
                            onNavigateBack = {
                                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                    navigator.navigateBack()
                                }
                            },
                        )
                    } else {
                        navigationIcon()
                    }
                }

                if (topBarOverride != null) {
                    topBarOverride(myNavigationIcon)
                } else {
                    TopAppBar(
                        title = title,
                        navigationIcon = myNavigationIcon,
                        actions = {
                            if (currentWindowAdaptiveInfo1().isWidthCompact && navigator.currentDestination?.pane != ListDetailPaneScaffoldRole.Detail) {
                                TextButton(
                                    onClick = {
                                        coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                                            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail)
                                        }
                                    },
                                ) {
                                    Text(stringResource(Lang.settings_mediasource_selector_test))
                                }
                            }
                            Box {
                                var showDropdown by remember { mutableStateOf(false) }
                                IconButton({ showDropdown = true }) {
                                    Icon(
                                        Icons.Rounded.MoreVert,
                                        stringResource(Lang.settings_mediasource_selector_more),
                                    )
                                }
                                DropdownMenu(showDropdown, { showDropdown = false }) {
                                    MediaSourceConfigurationDefaults.DropdownMenuImport(
                                        state = importState,
                                        onImported = { showDropdown = false },
                                        enabled = enableImportExport,
                                    )
                                    MediaSourceConfigurationDefaults.DropdownMenuExport(
                                        state = exportState,
                                        onDismissRequest = { showDropdown = false },
                                        enabled = enableImportExport,
                                    )
                                }
                            }
                        },
                        windowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        colors = AniThemeDefaults.topAppBarColors(),
                    )
                }
            }
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        BackHandler(navigator.canNavigateBack()) {
            coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                navigator.navigateBack()
            }
        }

        ListDetailPaneScaffold(
            remember(navigator.scaffoldDirective) { navigator.scaffoldDirective.withoutAutoFocus() },
            navigator.scaffoldValue,
            listPane = {
                ListDetailAnimatedPane(Modifier.preferredWidth(480.dp)) {
                    listPane(paddingValues)
                }
            },
            detailPane = {
                ListDetailAnimatedPane {
                    detailPane(paddingValues)
                }
            },
            Modifier.materialWindowMarginPadding(),
        )
    }
}
