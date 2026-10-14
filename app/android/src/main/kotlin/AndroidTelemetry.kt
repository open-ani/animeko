/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.android

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.analytics.analytics
import dev.gitlive.firebase.initialize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.platform.AppStartupTasks
import me.him188.ani.app.platform.create
import me.him188.ani.app.platform.currentAniBuildConfig
import me.him188.ani.utils.analytics.AnalyticsConfig
import me.him188.ani.utils.analytics.AnalyticsImpl
import org.koin.core.Koin

fun launchTelemetryInitialization(scope: CoroutineScope, context: Context, koin: Koin): Job = scope.launch {
    val settingsRepository = koin.get<SettingsRepository>()
    val userRepository = koin.get<UserRepository>()
    val settings = settingsRepository.analyticsSettings.flow.first()
    settingsRepository.analyticsSettings.update { settings }
    if (settings.isBugReportEnabled) {
        AppStartupTasks.initializeSentry(settings.deviceId)
    }
    if (settings.isAnalyticsEnabled && currentAniBuildConfig.analyticsEnabled && Firebase.initialize(context) != null) {
        AppStartupTasks.initializeAnalytics {
            AnalyticsImpl(AnalyticsConfig.create()).apply {
                init()

                userRepository.selfInfoFlow.first()?.id?.let {
                    Firebase.analytics.setUserId(it.toString())
                }
                scope.launch {
                    userRepository.selfInfoFlow.map { it?.id }.collect {
                        Firebase.analytics.setUserId(it?.toString())
                    }
                }
            }
        }
    }
}
