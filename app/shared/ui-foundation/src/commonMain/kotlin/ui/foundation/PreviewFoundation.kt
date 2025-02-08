/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.serviceLoaderEnabled
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.animation.ProvideAniMotionCompositionLocals
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.foundation.widgets.LocalToaster
import me.him188.ani.app.ui.foundation.widgets.NoOpToaster
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.imageResource

/**
 * 只提供最基础的组件. 不启动 Koin, 也就不支持 viewmodel.
 *
 * @since 3.10
 */
// @TestOnly // 这里就不标记了, 名字已经足够明显了
@OptIn(TestOnly::class)
@Composable
inline fun ProvideCompositionLocalsForPreview(
    isDark: Boolean = isSystemInDarkTheme(),
    crossinline content: @Composable () -> Unit,
) {
    val aniNavigator = remember { AniNavigator() }
    val previewImage = imageResource(Res.drawable.a)
    val coilContext = LocalPlatformContext.current
    val coilImage by lazy(LazyThreadSafetyMode.NONE) {
        previewImage.asCoilImage()
    }
    val viewModelStoreOwner = remember {
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore = ViewModelStore()
        }
    }
    DisposableEffect(viewModelStoreOwner) {
        onDispose {
            viewModelStoreOwner.viewModelStore.clear()
        }
    }
    CompositionLocalProvider(
        LocalIsPreviewing provides true,
        LocalNavigator providesDefault aniNavigator,
        LocalToaster providesDefault NoOpToaster,
        LocalImageLoader providesDefault remember {
            ImageLoader.Builder(coilContext)
                .placeholder { coilImage }
                .error { coilImage }
                .fallback { coilImage }
                .serviceLoaderEnabled(false)
                .build()
        },
        LocalImageViewerHandler providesDefault rememberImageViewerHandler(),
        LocalTimeFormatter providesDefault remember { TimeFormatter() },
        LocalOnBackPressedDispatcherOwner provides remember {
            object : OnBackPressedDispatcherOwner {
                override val onBackPressedDispatcher: OnBackPressedDispatcher = OnBackPressedDispatcher(null)
                override val lifecycle: Lifecycle get() = TestGlobalLifecycleOwner.lifecycle
            }
        },
        LocalLifecycleOwner providesDefault remember {
            TestGlobalLifecycleOwner
        },
        LocalThemeSettings providesDefault ThemeSettings.Default.copy(
            darkMode = if (isDark) DarkMode.DARK else DarkMode.LIGHT,
        ),
        LocalViewModelStoreOwner provides viewModelStoreOwner,
    ) {
        val navController = rememberNavController()
        aniNavigator.setNavController(navController)
        ProvidePlatformCompositionLocalsForPreview {
            AniTheme(isDark = isDark) {
                ProvideAniMotionCompositionLocals {
                    content()
                }
            }
        }
    }
}

@TestOnly
data object TestGlobalLifecycleOwner : LifecycleOwner {
    override val lifecycle: Lifecycle by lazy {
        LifecycleRegistry.createUnsafe(this).apply {
            this.currentState = Lifecycle.State.RESUMED
        }
    }
}

@TestOnly
@Composable
@PublishedApi
internal expect inline fun ProvidePlatformCompositionLocalsForPreview(
    crossinline content: @Composable () -> Unit
)


// kept for developer convenience, remove in 4.8.0.
@Composable
@Deprecated(
    "Replaced with ProvideCompositionLocalsForPreview",
    ReplaceWith("ProvideCompositionLocalsForPreview(isDark, content)"),
    level = DeprecationLevel.ERROR,
)
inline fun ProvideFoundationCompositionLocalsForPreview(
    isDark: Boolean = isSystemInDarkTheme(),
    crossinline content: @Composable () -> Unit,
) = ProvideCompositionLocalsForPreview(isDark, content)


/**
 * 用于 UI test. 固定主题颜色.
 */
@TestOnly
@Composable
fun ProvideFoundationCompositionLocalsForTest(
    isDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    ProvideCompositionLocalsForPreview(
        isDark,
    ) {
        content()
    }
}