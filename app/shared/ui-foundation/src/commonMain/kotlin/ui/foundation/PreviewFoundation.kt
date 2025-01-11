/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.serviceLoaderEnabled
import com.materialkolor.rememberDynamicColorScheme
import me.him188.ani.app.data.models.preference.DEFAULT_SEED_COLOR
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.navigation.LocalOnBackPressedDispatcherOwner
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcher
import me.him188.ani.app.ui.foundation.navigation.OnBackPressedDispatcherOwner
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
inline fun ProvideFoundationCompositionLocalsForPreview(
    isDark: Boolean = false,
    crossinline content: @Composable () -> Unit,
) {
    val colorScheme = rememberDynamicColorScheme(
        seedColor = Color(DEFAULT_SEED_COLOR),
        isDark = isDark,
        isAmoled = false,
    )
    
    val aniNavigator = remember { AniNavigator() }
    val previewImage = imageResource(Res.drawable.a)
    val coilContext = LocalPlatformContext.current
    val coilImage by lazy(LazyThreadSafetyMode.NONE) {
        previewImage.asCoilImage()
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
                override val lifecycle: Lifecycle get() = TestGlobalLifecycle
            }
        },
        LocalLifecycleOwner providesDefault remember {
            object : LifecycleOwner {
                override val lifecycle: Lifecycle get() = TestGlobalLifecycle
            }
        },
    ) {
        val navController = rememberNavController()
        SideEffect {
            aniNavigator.setNavController(navController)
        }
        ProvidePlatformCompositionLocalsForPreview {
            MaterialTheme(colorScheme) {
                content()
            }
        }
    }
}

@TestOnly
data object TestGlobalLifecycle : Lifecycle() {
    private val owner = object : LifecycleOwner {
        override val lifecycle get() = this@TestGlobalLifecycle
    }

    override val currentState get() = State.RESUMED

    override fun addObserver(observer: LifecycleObserver) {
//        require(observer is DefaultLifecycleObserver) {
//            "$observer must implement androidx.lifecycle.DefaultLifecycleObserver."
//        }
//
//        // Call the lifecycle methods in order and do not hold a reference to the observer.
//        observer.onCreate(owner)
//        observer.onStart(owner)
//        observer.onResume(owner)
    }

    override fun removeObserver(observer: LifecycleObserver) {}
}

@TestOnly
@Composable
@PublishedApi
internal expect inline fun ProvidePlatformCompositionLocalsForPreview(
    crossinline content: @Composable () -> Unit
)
