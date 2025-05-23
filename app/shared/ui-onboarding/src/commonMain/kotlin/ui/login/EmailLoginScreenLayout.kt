package me.him188.ani.app.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    Scaffold(
        modifier,
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("登录") },
                navigationIcon = { BackNavigationIconButton(onNavigateBack) },
                scrollBehavior = scrollBehavior,
                actions = { IconButton(onNavigateSettings) { Icon(Icons.Outlined.Settings, "设置") } },
                windowInsets = AniWindowInsets.forPageContent(),
            )
        },
        contentWindowInsets = AniWindowInsets.forPageContent(),
    ) { contentPadding ->
        Column(
            Modifier
                .fillMaxWidth()
                .wrapContentWidth(align = Alignment.CenterHorizontally)
                .ifThen(currentWindowAdaptiveInfo1().windowSizeClass.isWidthAtLeastMedium) {
                    widthIn(max = 480.dp)
                }
                .padding(contentPadding)
                .padding(horizontal = 24.dp)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .wrapContentHeight(align = Alignment.CenterVertically)
                    .fillMaxWidth()
                    .heightIn(min = 230.dp),
            ) {
                content()
            }

            ThirdPartyLoginMethods(
                onBangumiLoginClick,
                Modifier.heightIn(min = 180.dp).wrapContentHeight(align = Alignment.Top),
            )
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
