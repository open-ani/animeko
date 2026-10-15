/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.notification

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.data.models.preference.NotificationSettings
import me.him188.ani.app.data.repository.user.SettingsRepository
import me.him188.ani.app.domain.usecase.GlobalKoin
import me.him188.ani.app.ui.adaptive.AniTopAppBar
import me.him188.ani.app.ui.adaptive.AniTopAppBarDefaults
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.AniWindowInsets
import me.him188.ani.app.ui.foundation.theme.AniThemeDefaults
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_settings
import me.him188.ani.app.ui.lang.notification_settings_account_security_item
import me.him188.ani.app.ui.lang.notification_settings_announcement_description
import me.him188.ani.app.ui.lang.notification_settings_announcement_item
import me.him188.ani.app.ui.lang.notification_settings_group_announcements
import me.him188.ani.app.ui.lang.notification_settings_group_system
import me.him188.ani.app.ui.lang.notification_settings_report_result_item
import me.him188.ani.app.ui.lang.notification_settings_system_group_description
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.AbstractSettingsViewModel
import me.him188.ani.app.ui.settings.framework.SettingsState
import me.him188.ani.app.ui.settings.framework.components.SettingsScope
import me.him188.ani.app.ui.settings.framework.components.SwitchItem
import me.him188.ani.app.ui.settings.framework.rememberTestSettingsState
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.resources.stringResource
import org.koin.core.Koin

@Stable
class NotificationSettingsViewModel(
    koin: Koin = GlobalKoin,
) : AbstractSettingsViewModel() {
    private val settingsRepository: SettingsRepository by koin.inject()

    val notificationSettings: SettingsState<NotificationSettings> =
        settingsRepository.notificationSettings.stateInBackground(NotificationSettings.Default)
}

object NotificationSettingsTestTags {
    const val REPORT_RESULT = "notification-settings-report-result"
    const val ACCOUNT_SECURITY = "notification-settings-account-security"
    const val ANNOUNCEMENT = "notification-settings-announcement"
    const val GROUP_DIVIDER = "notification-settings-group-divider"
}

/**
 * 通知设置页.
 */
@Composable
fun NotificationSettingsScreen(
    state: SettingsState<NotificationSettings>,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    windowInsets: WindowInsets = AniWindowInsets.forPageContent(),
) {
    Scaffold(
        modifier,
        topBar = {
            AniTopAppBar(
                title = { AniTopAppBarDefaults.Title(stringResource(Lang.notification_settings)) },
                navigationIcon = navigationIcon,
                colors = AniThemeDefaults.topAppBarColors(),
                windowInsets = AniWindowInsets.forTopAppBarWithoutDesktopTitle(),
            )
        },
        containerColor = AniThemeDefaults.pageContentBackgroundColor,
        contentWindowInsets = windowInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
    ) { paddingValues ->
        SettingsTab(
            Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            NotificationSettingsGroups(state)
        }
    }
}

/**
 * 举报结果和账号安全各有一个开关, 关闭后本机不显示也不统计这类通知; 系统消息没有开关. 公告只控制头像上的红点.
 *
 * 两组之间有一条通栏的分隔线.
 */
@Composable
fun SettingsScope.NotificationSettingsGroups(state: SettingsState<NotificationSettings>) {
    val settings by state
    // 不使用外层的组间距, 分隔线与两组的距离只来自组自身的内边距
    Column {
        Group(
            title = { Text(stringResource(Lang.notification_settings_group_system)) },
            description = { Text(stringResource(Lang.notification_settings_system_group_description)) },
            useThinHeader = true,
        ) {
            SwitchItem(
                checked = settings.showReportResults,
                onCheckedChange = { state.update(settings.copy(showReportResults = it)) },
                title = { Text(stringResource(Lang.notification_settings_report_result_item)) },
                Modifier.testTag(NotificationSettingsTestTags.REPORT_RESULT),
            )
            SwitchItem(
                checked = settings.showAccountSecurity,
                onCheckedChange = { state.update(settings.copy(showAccountSecurity = it)) },
                title = { Text(stringResource(Lang.notification_settings_account_security_item)) },
                Modifier.testTag(NotificationSettingsTestTags.ACCOUNT_SECURITY),
            )
        }
        HorizontalDivider(
            Modifier.testTag(NotificationSettingsTestTags.GROUP_DIVIDER),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Group(title = { Text(stringResource(Lang.notification_settings_group_announcements)) }, useThinHeader = true) {
            SwitchItem(
                checked = settings.showAnnouncementDot,
                onCheckedChange = { state.update(settings.copy(showAnnouncementDot = it)) },
                title = { Text(stringResource(Lang.notification_settings_announcement_item)) },
                Modifier.testTag(NotificationSettingsTestTags.ANNOUNCEMENT),
                description = { Text(stringResource(Lang.notification_settings_announcement_description)) },
            )
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@Preview
private fun PreviewNotificationSettingsScreen() = ProvideCompositionLocalsForPreview {
    NotificationSettingsScreen(rememberTestSettingsState(NotificationSettings.Default))
}
