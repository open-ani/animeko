/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import me.him188.ani.app.data.models.danmaku.DanmakuFilterConfig
import me.him188.ani.app.data.models.preference.ThemeSettings
import me.him188.ani.app.data.models.preference.UISettings
import me.him188.ani.app.data.models.preference.UpdateSettings
import me.him188.ani.app.data.models.preference.VideoScaffoldConfig
import me.him188.ani.app.ui.foundation.LocalIsPreviewing
import me.him188.ani.app.ui.settings.danmaku.createTestDanmakuRegexFilterState
import me.him188.ani.app.ui.settings.framework.components.RowButtonItem
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.createTestSettingsState
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.app.ui.update.TestNewVersion
import me.him188.ani.utils.platform.annotations.TestOnly
import kotlin.random.Random


@OptIn(TestOnly::class)
@Preview
@Composable
private fun PreviewAppSettingsTab() {
    AppSettingsTab(
        softwareUpdateGroupState = rememberTestSoftwareUpdateGroupState(),
        uiSettings = rememberTestSettingsState(UISettings.Default),
        themeSettings = rememberTestSettingsState(ThemeSettings.Default),
        videoScaffoldConfig = rememberTestSettingsState(VideoScaffoldConfig.Default),
        danmakuFilterConfig = rememberTestSettingsState(DanmakuFilterConfig.Default),
        danmakuRegexFilterState = createTestDanmakuRegexFilterState(),
        showDebug = true,
    )
}

@TestOnly
@Composable
internal fun rememberTestSoftwareUpdateGroupState(): SoftwareUpdateGroupState {
    val scope = rememberCoroutineScope()
    return remember {
        SoftwareUpdateGroupState(
            updateSettings = createTestSettingsState(UpdateSettings(autoCheckUpdate = true), scope),
            scope,
            onTest = {
                when (Random.nextInt() % 3) {
                    0 -> CheckVersionResult.UpToDate
                    1 -> CheckVersionResult.HasNewVersion(TestNewVersion)
                    else -> CheckVersionResult.Failed(Exception("Test"))
                }
            },
        )
    }
}


@SuppressLint("BatteryLife")
@Composable
internal actual fun SettingsScope.AppSettingsTabPlatform() {
    val context by rememberUpdatedState(newValue = LocalContext.current)
    val powerManager by remember {
        derivedStateOf {
            kotlin.runCatching {
                val manager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager?
                manager?.isIgnoringBatteryOptimizations(context.packageName) // check 
                manager
            }.getOrNull()
        }
    }
    // 禁用电池优化
    if (powerManager != null) {
        Group(
            title = { Text("后台运行") },
            description = { Text(text = "缓存功能需要应用保持在后台运行才能下载视频") },
        ) {
            val isPreviewing = LocalIsPreviewing.current
            var isIgnoring by remember {
                if (isPreviewing) {
                    mutableStateOf(false)
                } else {
                    mutableStateOf(powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true)
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                if (!isPreviewing) {
                    isIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                }
            }
            SwitchItem(
                checked = isIgnoring,
                onCheckedChange = {
                    if (!isIgnoring) {
                        kotlin.runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        }
                    } else {
                        kotlin.runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS,
                                ),
                            )
                        }
                    }
                },
                title = { Text("禁用电池优化") },
                description = { Text("可以帮助保持在后台运行。可能增加耗电") },
            )
        }
    }

    Group(
        title = { Text("通知设置") },
    ) {
        RowButtonItem(
            icon = { Icon(Icons.Rounded.ArrowOutward, contentDescription = null) },
            onClick = {
                kotlin.runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APP_NOTIFICATION_SETTINGS, // since 8.0
                        ).putExtra(
                            Settings.EXTRA_APP_PACKAGE,
                            context.packageName,
                        ),
                    )
                }.onFailure {
                    it.printStackTrace()
                }
            },
        ) { Text(text = "打开设置") }
    }
}
