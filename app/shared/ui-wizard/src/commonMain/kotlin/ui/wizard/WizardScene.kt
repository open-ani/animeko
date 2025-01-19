/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideIn
import androidx.compose.animation.slideOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.text.ProvideTextStyleContentColor
import me.him188.ani.app.ui.foundation.theme.NavigationMotionScheme
import me.him188.ani.app.ui.wizard.navigation.WizardController

@Composable
fun WizardScene(
    controller: WizardController,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
}


object WizardDefaults {

    val motionScheme = kotlin.run {
        val slideEnter = 350
        val fadeEnter = 262
        val slideExit = 200
        val fadeExit = 150

        NavigationMotionScheme(
            enterTransition = fadeIn(animationSpec = tween(fadeEnter, slideEnter - fadeEnter)) +
                    slideIn(tween(slideEnter)) {
                        IntOffset((it.width * 0.15).toInt(), 0)
                    },
            exitTransition = fadeOut(animationSpec = tween(fadeExit, slideExit - fadeExit)) +
                    slideOut(tween(slideExit)) {
                        IntOffset(-(it.width * 0.15).toInt(), 0)
                    },
            popEnterTransition = fadeIn(animationSpec = tween(fadeEnter, slideEnter - fadeEnter)) +
                    slideIn(tween(slideEnter)) {
                        IntOffset(-(it.width * 0.15).toInt(), 0)
                    },
            popExitTransition = fadeOut(animationSpec = tween(fadeExit, slideExit - fadeExit)) +
                    slideOut(tween(slideExit)) {
                        IntOffset((it.width * 0.15).toInt(), 0)
                    },
        )
    }
    
    @Composable
    fun StepTopAppBar(
        currentStep: Int,
        totalStep: Int,
        modifier: Modifier = Modifier,
        windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
        stepName: @Composable () -> Unit,
    ) {
        val renderedStep = remember(currentStep, totalStep) {
            "步骤 $currentStep / $totalStep"
        }
        LargeTopAppBar(
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProvideTextStyleContentColor(
                        value = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(renderedStep)
                    }
                    stepName()
                }
            },
            modifier = modifier,
            windowInsets = windowInsets,

            )
    }

    @Composable
    fun StepControlBar(
        forwardAction: @Composable () -> Unit,
        backwardAction: @Composable () -> Unit,
        modifier: Modifier = Modifier,
        tertiaryAction: @Composable () -> Unit = {},
        windowInsets: WindowInsets = AniWindowInsets.forNavigationBar(),
    ) {
        Box(modifier = modifier) {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(
                        windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    )
                    .fillMaxWidth(),
            ) {
                HorizontalDivider(Modifier.fillMaxWidth())
                Row(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    backwardAction()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        tertiaryAction()
                        forwardAction()
                    }
                }
            }
        }
    }

    @Composable
    fun GoForwardButton(
        onClick: () -> Unit,
        enabled: Boolean,
        text: String = "继续"
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
        ) {
            Text(text)
        }
    }

    @Composable
    fun GoBackwardButton(
        onClick: () -> Unit,
        text: String = "上一步"
    ) {
        TextButton(onClick = onClick) {
            Text(text)
        }
    }

    @Composable
    fun SkipButton(
        onClick: () -> Unit,
        text: String = "跳过"
    ) {
        TextButton(onClick = onClick) {
            Text(text)
        }
    }
}