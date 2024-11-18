/*
 * Copyright (C) 2024 OpenAni and contributors.
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
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.map
import me.him188.ani.app.data.models.preference.DarkMode
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateFactory
import me.him188.ani.app.ui.subject.details.state.SubjectDetailsStateLoader
import me.him188.ani.utils.platform.isMobile
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class AniAppViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    val themeSettings: ThemeSettings? by settings.uiSettings.flow.map { it.theme }.produceState(null)

    private val subjectDetailsStateFactory: SubjectDetailsStateFactory by inject()

    /**
     * 用于加载条目详情页的内容.
     *
     * 可以 (并且强烈建议) 在调用 [me.him188.ani.app.navigation.AniNavigator.navigateSubjectDetails] 之前调用 [SubjectDetailsStateLoader.load]
     * 来提前准备内容, 并且处理网络错误等. 还可以支持 lookahead 的动画, 例如 Container Transform.
     *
     * TODO: 当用户从条目页返回时, state 应当被清除
     *  但目前保留着也不会有很大浪费
     */
    val subjectDetailsStateLoader = SubjectDetailsStateLoader(subjectDetailsStateFactory, backgroundScope)
}

@Composable
fun AniApp(
    modifier: Modifier = Modifier,
    overrideColorTheme: ColorScheme? = null,
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

    CompositionLocalProvider(
//        LocalImageLoader provides imageLoader,
        LocalTimeFormatter provides remember { TimeFormatter() },
    ) {
        val focusManager by rememberUpdatedState(LocalFocusManager.current)
        val keyboard by rememberUpdatedState(LocalSoftwareKeyboardController.current)

        val viewModel = viewModel { AniAppViewModel() }

        // 主题读好再进入 APP, 防止黑白背景闪烁
        val theme = viewModel.themeSettings ?: return@CompositionLocalProvider

        MaterialTheme(
            overrideColorTheme ?: currentPlatformColorTheme(theme.darkMode, theme.dynamicTheme),
        ) {
            Box(
                modifier = modifier
                    .ifThen(LocalPlatform.current.isMobile()) {
                        focusable(false)
                            .clickable(
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
internal expect fun currentPlatformColorTheme(
    darkMode: DarkMode,
    useDynamicTheme: Boolean
): ColorScheme
