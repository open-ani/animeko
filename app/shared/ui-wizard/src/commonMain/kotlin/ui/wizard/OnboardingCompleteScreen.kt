/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults

@Composable
fun OnboardingCompleteScreen(
    vm: OnboardingCompleteViewModel,
    onClickContinue: () -> Unit,
    backNavigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val state by vm.state.collectAsStateWithLifecycle(OnboardingCompleteState.Placeholder)

    Surface(color = AniThemeDefaults.pageContentBackgroundColor) {
        OnboardingCompleteScreen(
            state = state,
            onClickContinue = onClickContinue,
            backNavigation = backNavigation,
            modifier = modifier,
            windowInsets = windowInsets
        )
    }
}

@Composable
internal fun OnboardingCompleteScreen(
    state: OnboardingCompleteState,
    onClickContinue: () -> Unit,
    backNavigation: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    layoutParams: WizardLayoutParams = WizardLayoutParams.calculate(currentWindowAdaptiveInfo1().windowSizeClass),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = backNavigation,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AniThemeDefaults.pageContentBackgroundColor
                ),
                windowInsets = windowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
        },
        containerColor = Color.Transparent
    ) { _ ->
        Box(
            modifier = Modifier
                .padding(
                    horizontal = layoutParams.horizontalPadding,
                    vertical = layoutParams.verticalPadding
                )
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) { 
                Text(
                    text = if (state.username != null) "欢迎，${state.username}" else "欢迎"  ,
                    modifier = Modifier
                        .widthIn(max = 240.dp)
                        .animateContentSize(),
                    style = MaterialTheme.typography.headlineMedium,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
                Text(
                    "...",
                    style = MaterialTheme.typography.titleMedium
                )
                Column(modifier = Modifier.padding(vertical = 16.dp)) { 
                    AvatarImage(
                        url = state.avatarUrl,
                        modifier = Modifier.size(96.dp).clip(CircleShape),
                    )
                }
                Column(modifier = Modifier.padding(vertical = 16.dp)) {
                    Button(
                        onClick = onClickContinue,
                        Modifier,
                        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowForward, 
                            null,
                            Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text("完成", softWrap = false)
                    }
                }
            }
        }
    }
}

@Stable
class OnboardingCompleteState(
    val username: String?, // guest has no username
    val avatarUrl: String, // guest use default avatar
) {
    companion object {
        val Placeholder = OnboardingCompleteState(null, OnboardingCompleteViewModel.DEFAULT_AVATAR)
    }
}