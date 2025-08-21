/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.login

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.layout.isWidthAtLeastMedium
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.widgets.BackNavigationIconButton


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EmailLoginScreenLayout(
    onBangumiLoginClick: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit = { Text("登录") },
    showThirdPartyLogin: Boolean = true,
    content: @Composable ColumnScope.(scrollState: ScrollState) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    Scaffold(
        modifier,
        topBar = {
            TopAppBar(
                title = title,
                navigationIcon = { BackNavigationIconButton(onNavigateBack) },
                scrollBehavior = scrollBehavior,
                actions = { IconButton(onNavigateSettings) { Icon(Icons.Outlined.Settings, "设置") } },
                windowInsets = AniWindowInsets.forTopAppBar(),
            )
        },
        contentWindowInsets = AniWindowInsets.forPageContent(),
    ) { contentPadding ->
        BoxWithConstraints {
            val availableHeight = maxHeight - contentPadding.calculateTopPadding() - contentPadding.calculateBottomPadding() - 48.dp
            val thirdPartyLoginHeight = if (showThirdPartyLogin) 180.dp else 0.dp
            val contentAreaHeight = availableHeight - thirdPartyLoginHeight
            val scrollState = rememberScrollState()
            
            Column(
                Modifier
                    .fillMaxWidth()
                    .wrapContentWidth(align = Alignment.CenterHorizontally)
                    .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium) {
                        widthIn(max = 480.dp)
                    }
                    .padding(contentPadding)
                    .padding(horizontal = 24.dp)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(scrollState),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = contentAreaHeight),
                    verticalArrangement = Arrangement.Center,
                ) {
                    content(scrollState)
                }

                if (showThirdPartyLogin) {
                    ThirdPartyLoginMethods(
                        onBangumiLoginClick,
                        Modifier.heightIn(min = 180.dp).wrapContentHeight(align = Alignment.Top),
                    )
                }
            }
        }
    }
}

/**
 * 适合全屏中间使用的
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenteredSectionHeader(
    title: @Composable () -> Unit,
    description: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
) {
    Column(
        modifier.padding(contentPadding)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ProvideTextStyleContentColor(
            MaterialTheme.typography.titleLargeEmphasized
                .copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
        ) {
            title()
        }

        ProvideTextStyleContentColor(
            MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            description()
        }
    }
}
