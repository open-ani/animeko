@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.web

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.browser.document
import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.PlatformWindow
import me.him188.ani.app.platform.WebContext
import me.him188.ani.app.platform.createAppRootCoroutineScope
import me.him188.ani.app.platform.getCommonKoinModule
import me.him188.ani.app.platform.startCommonKoinModule
import me.him188.ani.app.ui.foundation.layout.LocalPlatformWindow
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.Toaster
import me.him188.ani.app.ui.main.AniApp
import me.him188.ani.app.ui.main.AniAppContent
import org.koin.core.context.startKoin

fun main() {
    AppStartupTasks.printVersions()
    val scope: CoroutineScope = createAppRootCoroutineScope()
    val context = WebContext()

    startKoin {
        modules(getCommonKoinModule({ context }, scope))
        modules(getWebModules(scope))
    }.startCommonKoinModule(context, scope)

    ComposeViewport(document.getElementById("root") ?: document.body!!) {
        val aniNavigator = remember { AniNavigator() }
        val backDispatcherOwner = remember {
            WebOnBackPressedDispatcherOwner {
                aniNavigator.popBackStack()
            }
        }
        CompositionLocalProvider(
            LocalPlatformWindow provides remember { PlatformWindow() },
            LocalToaster provides WebNoopToaster,
            LocalOnBackPressedDispatcherOwner provides backDispatcherOwner,
        ) {
            AniApp(Modifier) {
                AniAppContent(aniNavigator)
            }
        }
    }
}

private object WebNoopToaster : Toaster {
    override fun toast(text: String) {}
}

private class WebOnBackPressedDispatcherOwner(
    fallbackOnBackPressed: () -> Unit,
) : OnBackPressedDispatcherOwner, LifecycleOwner {
    override val lifecycle: Lifecycle = LifecycleRegistry.createUnsafe(this).apply {
        currentState = Lifecycle.State.RESUMED
    }

    override val onBackPressedDispatcher: OnBackPressedDispatcher =
        OnBackPressedDispatcher(fallbackOnBackPressed)
}
