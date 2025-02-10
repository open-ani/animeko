/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.theme.SystemBarColorEffect
import me.him188.ani.app.ui.foundation.theme.appColorScheme
import me.him188.ani.app.ui.wizard.navigation.WizardController

@Composable
fun WelcomeScreen(
    vm: WelcomeViewModel,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    wizardLayoutParams: WizardLayoutParams =
        WizardLayoutParams.fromWindowSizeClass(currentWindowAdaptiveInfo1().windowSizeClass),
) {
    val navController = rememberNavController()
    vm.welcomeNavController = navController

    CompositionLocalProvider(
        LocalThemeSettings provides vm.wizardState.selectThemeState.value,
    ) {
        SystemBarColorEffect()
        MaterialTheme(colorScheme = appColorScheme()) {
            Box(
                modifier = modifier,
                contentAlignment = Alignment.Center,
            ) {
                WelcomePage(
                    navController = navController,
                    modifier = Modifier.widthIn(max = 720.dp),
                    wizardController = vm.wizardController,
                    wizardState = vm.wizardState,
                    onFinishWizard = {
                        vm.finishWizard()
                        onFinishWizard()
                    },
                    contactActions = contactActions,
                    windowInsets = windowInsets,
                    wizardLayoutParams = wizardLayoutParams,
                )
            }
        }
    }
}

@Composable
fun WelcomePage(
    navController: NavHostController,
    wizardController: WizardController,
    wizardState: WizardPresentationState,
    onFinishWizard: () -> Unit,
    contactActions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
    wizardLayoutParams: WizardLayoutParams = WizardLayoutParams.Default
) {
    Surface {
        NavHost(
            navController = navController,
            modifier = modifier,
            startDestination = "first_screen",
        ) {
            composable("first_screen") {
                FirstScreenScene(
                    onLinkStart = { navController.navigate("wizard") },
                    contactActions = contactActions,
                    windowInsets = windowInsets,
                    layoutParams = wizardLayoutParams,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable("wizard") {
                WizardScene(
                    controller = wizardController,
                    state = wizardState,
                    onNavigateBack = { navController.navigateUp() },
                    modifier = Modifier.fillMaxSize(),
                    contactActions = contactActions,
                    wizardLayoutParams = wizardLayoutParams,
                    onFinishWizard = onFinishWizard,
                )
            }
        }
    }
}