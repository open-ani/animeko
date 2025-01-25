/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import io.ktor.client.HttpClient
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.settings.ProxyProvider
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.LocalImageLoader
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.createDefaultImageLoader
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.utils.ktor.UnsafeWrapperHttpClientApi
import me.him188.ani.utils.platform.isMobile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class AniAppViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    private val proxyProvider: ProxyProvider by inject()
    private val httpClientProvider: HttpClientProvider by inject()
    val themeSettings: ThemeSettings? by settings.themeSettings.flow.produceState(null)

    @OptIn(UnsafeWrapperHttpClientApi::class)
    val imageLoaderClient =
        httpClientProvider.get().borrowForever() // This client is needed throughout the app's lifecycle
}

@Composable
fun AniApp(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
//    val proxy by remember {
//        KoinPlatform.getKoin().get<SettingsRepository>().proxySettings.flow.map {
//            it.default.config
//        }
//    }.collectAsStateWithLifecycle(null)
//    val coilContext = LocalPlatformContext.current
//    val imageLoader by remember(coilContext) {
//        derivedStateOf {
//            getDefaultImageLoader(coilContext, proxyConfig = proxy)
//        }
//    }

    val viewModel = viewModel { AniAppViewModel() }

    // 主题读好再进入 APP, 防止黑白背景闪烁
    val themeSettings = viewModel.themeSettings ?: return

    CompositionLocalProvider(
//        LocalImageLoader provides imageLoader,
        LocalImageLoader provides rememberImageLoader(viewModel.imageLoaderClient),
        LocalTimeFormatter provides remember { TimeFormatter() },
        LocalThemeSettings provides themeSettings,
    ) {
        val focusManager by rememberUpdatedState(LocalFocusManager.current)
        val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)

        AniTheme {
            Box(
                modifier = modifier.ifThen(LocalPlatform.current.isMobile()) {
                    focusable(false).clickable(
                        remember { MutableInteractionSource() },
                        null,
                    ) {
                        keyboard?.hide()
                        focusManager.clearFocus()
                    }
                },
            ) {
                Column {
                    content()
                }
            }
        }
    }
}

@Composable
private fun rememberImageLoader(client: HttpClient): ImageLoader {
    val coilContext = LocalPlatformContext.current
    return remember(coilContext, client) {
        derivedStateOf {
            createDefaultImageLoader(coilContext, client)
        }
    }.value
}
