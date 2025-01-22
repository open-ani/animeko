/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.wizard.navigation.WizardController

@Composable
fun WelcomePage(
    vm: WelcomeViewModel,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    val navController = rememberNavController()
    vm.welcomeNavController = navController

    WelcomePage(
        navController = navController,
        wizardController = vm.wizardController,
        wizardState = vm.wizardState,
        modifier = modifier,
        windowInsets = windowInsets,
    )
}

@Composable
fun WelcomePage(
    navController: NavHostController,
    wizardController: WizardController,
    wizardState: WizardPresentationState,
    modifier: Modifier = Modifier,
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
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
                    contactActions = { },
                    windowInsets = windowInsets,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            composable("wizard") {
                WizardScene(
                    controller = wizardController,
                    state = wizardState,
                    windowInsets = windowInsets,
                    useEnterAnim = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}