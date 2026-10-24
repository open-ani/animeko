/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.take
import me.him188.ani.app.data.models.preference.EpisodeProgressSettings
import me.him188.ani.app.data.models.preference.SubjectAppearanceSettings
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.domain.foundation.HttpClientProvider
import me.him188.ani.app.domain.foundation.ScopedHttpClientUserAgent
import me.him188.ani.app.domain.foundation.get
import me.him188.ani.app.domain.media.download.MediaDownloadManager
import me.him188.ani.app.domain.mediasource.web.captcha.WebCaptchaDialogHost
import me.him188.ani.app.domain.mediasource.web.captcha.WebSessionManager
import me.him188.ani.app.domain.session.SessionState
import me.him188.ani.app.domain.session.SessionStateProvider
import me.him188.ani.app.domain.torrent.engines.PikPakEngine
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.navigation.MainScreenPage
import me.him188.ani.app.navigation.NavRoutes
import me.him188.ani.app.tools.LocalTimeFormatter
import me.him188.ani.app.tools.LocalWeekFormatter
import me.him188.ani.app.tools.TimeFormatter
import me.him188.ani.app.tools.TimeFormatterStrings
import me.him188.ani.app.tools.WeekFormatter
import me.him188.ani.app.tools.WeekFormatterStrings
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.LocalPlatform
import me.him188.ani.app.ui.foundation.LocalPlatformFontFamily
import me.him188.ani.app.ui.foundation.LocalEpisodeProgressSettings
import me.him188.ani.app.ui.foundation.LocalSketch
import me.him188.ani.app.ui.foundation.LocalSubjectAppearanceSettings
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.foundation.input.ActiveInputSourceState
import me.him188.ani.app.ui.foundation.input.LocalActiveInputSource
import me.him188.ani.app.ui.foundation.input.trackActiveInputSource
import me.him188.ani.app.ui.foundation.interaction.clearFocusOnUnhandledTap
import me.him188.ani.app.ui.foundation.navigation.LocalBackDispatcher
import me.him188.ani.app.ui.foundation.navigation.onBackNavigationInput
import me.him188.ani.app.ui.foundation.rememberAniSketchInstance
import me.him188.ani.app.ui.foundation.rememberPlatformFontFamily
import me.him188.ani.app.ui.foundation.theme.AniTheme
import me.him188.ani.app.ui.foundation.theme.LocalThemeSettings
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.LocaleZhCN
import me.him188.ani.app.ui.lang.time_days_ago
import me.him188.ani.app.ui.lang.time_days_later
import me.him188.ani.app.ui.lang.time_hours_ago
import me.him188.ani.app.ui.lang.time_hours_later
import me.him188.ani.app.ui.lang.time_just_now
import me.him188.ani.app.ui.lang.time_minutes_ago
import me.him188.ani.app.ui.lang.time_minutes_later
import me.him188.ani.app.ui.lang.time_seconds_ago
import me.him188.ani.app.ui.lang.time_seconds_later
import me.him188.ani.app.ui.lang.week_day_after_tomorrow
import me.him188.ani.app.ui.lang.week_friday
import me.him188.ani.app.ui.lang.week_monday
import me.him188.ani.app.ui.lang.week_next
import me.him188.ani.app.ui.lang.week_other_year_date
import me.him188.ani.app.ui.lang.week_same_year_date
import me.him188.ani.app.ui.lang.week_saturday
import me.him188.ani.app.ui.lang.week_sunday
import me.him188.ani.app.ui.lang.week_thursday
import me.him188.ani.app.ui.lang.week_today
import me.him188.ani.app.ui.lang.week_tomorrow
import me.him188.ani.app.ui.lang.week_tuesday
import me.him188.ani.app.ui.lang.week_wednesday
import me.him188.ani.torrent.pikpak.PikPakNotEnoughSpaceException
import me.him188.ani.utils.ktor.ScopedHttpClient
import me.him188.ani.utils.platform.Platform
import me.him188.ani.utils.platform.currentPlatform
import me.him188.ani.utils.platform.isMobile
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@Stable
class AniAppState(
    val initialNavRoute: NavRoutes,
    val mainSceneInitialPage: MainScreenPage,
    val themeSettings: ThemeSettings,
    val imageLoaderClient: ScopedHttpClient,
    val overlayComposables: List<@Composable () -> Unit>,
    val platformFont: String?,
    val episodeProgressSettings: EpisodeProgressSettings,
    val subjectAppearanceSettings: SubjectAppearanceSettings,
)

@Stable
class AniAppViewModel : AbstractViewModel(), KoinComponent {
    private val settings: SettingsRepository by inject()
    private val httpClientProvider: HttpClientProvider by inject()
    private val downloadManager: MediaDownloadManager by inject()
    private val webSessionManager: WebSessionManager by inject()
    private val userRepository: UserRepository by inject()
    private val sessionStateProvider: SessionStateProvider by inject()
    private val pikPakEngine: PikPakEngine by inject()

    private val imageLoaderClient = httpClientProvider.get(ScopedHttpClientUserAgent.ANI)

    private val mediaCacheComposablesFlow = flowOf(
        downloadManager.storages.map { @Composable { it.engine.ComposeContent() } },
    )

    val browserNavigator by inject<BrowserNavigator>()

    val pikPakNotEnoughSpace: Flow<PikPakNotEnoughSpaceException> get() = pikPakEngine.notEnoughSpace

    val bangumiSessionExpired =
        combine(userRepository.selfInfoFlow, sessionStateProvider.stateFlow) { selfInfo, sessionState ->
            val isBound = selfInfo?.bangumiUsername?.isNotBlank() == true
            val serverTokenInvalid = selfInfo?.isBangumiSessionValid == false
            val localTokenMissing = sessionState is SessionState.Valid && !sessionState.bangumiConnected
            isBound && (serverTokenInvalid || localTokenMissing)
        }.distinctUntilChanged().stateInBackground(false)

    val appState: Flow<AniAppState?> = combine(
        settings.themeSettings.flow,
        settings.uiSettings.flow.take(1).map { it.mainSceneInitialPage }, // 只需要读取一次
        settings.uiSettings.flow,
        mediaCacheComposablesFlow,
    ) { themeSettings, mainSceneInitialPage, uiSettings, mediaCacheComposables ->
        AniAppState(
            NavRoutes.Main(mainSceneInitialPage),
            uiSettings.mainSceneInitialPage,
            themeSettings,
            imageLoaderClient,
            mediaCacheComposables + listOf(@Composable { WebCaptchaDialogHost(webSessionManager) }),
            // Windows 并且 ani 语言为中文的话, 显式使用 Microsoft YaHei UI.
            // 如果 Windows 语言不是中文, 那系统会使用 Microsoft JhengHei UI 作为中文字体, 这个字体对简体中文的支持不好.
            if (currentPlatform() is Platform.Windows && uiSettings.appLanguage == LocaleZhCN) {
                "Microsoft YaHei UI"
            } else null,
            uiSettings.episodeProgress,
            uiSettings.subjectAppearance,
        )
    }.shareInBackground(
        started = SharingStarted.Eagerly,
        replay = 1,
    )

    suspend fun unbindBangumi() {
        userRepository.unbindBangumi()
    }

}

@Composable
fun AniApp(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val viewModel = viewModel { AniAppViewModel() }
    // 主题读好再进入 APP, 防止黑白背景闪烁
    val appState = viewModel.appState.collectAsStateWithLifecycle(null).value ?: return

    val timeFormatterStrings = TimeFormatterStrings(
        justNow = stringResource(Lang.time_just_now),
        secondsAgo = stringResource(Lang.time_seconds_ago),
        secondsLater = stringResource(Lang.time_seconds_later),
        minutesAgo = stringResource(Lang.time_minutes_ago),
        minutesLater = stringResource(Lang.time_minutes_later),
        hoursAgo = stringResource(Lang.time_hours_ago),
        hoursLater = stringResource(Lang.time_hours_later),
        daysAgo = stringResource(Lang.time_days_ago),
        daysLater = stringResource(Lang.time_days_later),
    )

    val weekFormatterStrings = WeekFormatterStrings(
        today = stringResource(Lang.week_today),
        tomorrow = stringResource(Lang.week_tomorrow),
        dayAfterTomorrow = stringResource(Lang.week_day_after_tomorrow),
        monday = stringResource(Lang.week_monday),
        tuesday = stringResource(Lang.week_tuesday),
        wednesday = stringResource(Lang.week_wednesday),
        thursday = stringResource(Lang.week_thursday),
        friday = stringResource(Lang.week_friday),
        saturday = stringResource(Lang.week_saturday),
        sunday = stringResource(Lang.week_sunday),
        nextWeek = stringResource(Lang.week_next),
        sameYearDateFormat = stringResource(Lang.week_same_year_date),
        otherYearDateFormat = stringResource(Lang.week_other_year_date),
    )

    CompositionLocalProvider(
        LocalSketch provides rememberAniSketchInstance(appState.imageLoaderClient),
        LocalTimeFormatter provides remember(timeFormatterStrings) { TimeFormatter(strings = timeFormatterStrings) },
        LocalWeekFormatter provides remember(weekFormatterStrings) { WeekFormatter(strings = weekFormatterStrings) },
        LocalThemeSettings provides appState.themeSettings,
        LocalEpisodeProgressSettings provides appState.episodeProgressSettings,
        LocalSubjectAppearanceSettings provides appState.subjectAppearanceSettings,
        LocalPlatformFontFamily provides rememberPlatformFontFamily(appState.platformFont),
        LocalActiveInputSource provides remember { ActiveInputSourceState() },
    ) {
        val backDispatcher = LocalBackDispatcher.current

        AniTheme {
            Box(
                modifier = modifier
                    .trackActiveInputSource(LocalActiveInputSource.current)
                    .onBackNavigationInput(backDispatcher::onBackPressed)
                    .ifThen(LocalPlatform.current.isMobile()) {
                        clearFocusOnUnhandledTap()
                    },
            ) {
                Box {
                    for (composable in appState.overlayComposables) {
                        composable()
                    }
                }

                Column {
                    content()
                }
            }
        }
    }
}
