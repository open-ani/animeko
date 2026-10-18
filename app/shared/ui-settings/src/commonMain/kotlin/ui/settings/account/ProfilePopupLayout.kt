/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import me.him188.ani.app.data.models.user.calculateDisplay
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.IconButton
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.animation.AniAnimatedVisibility
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.interaction.hoverable
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.app.ui.foundation.widgets.HeroIcon
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.notification_has_unread
import me.him188.ani.app.ui.lang.notification_title
import me.him188.ani.app.ui.lang.playback_history_title
import me.him188.ani.app.ui.lang.settings
import me.him188.ani.app.ui.lang.settings_account_popup_edit_profile
import me.him188.ani.app.ui.lang.settings_account_popup_login_register
import me.him188.ani.app.ui.lang.settings_account_popup_logout
import me.him188.ani.app.ui.lang.settings_account_popup_not_logged_in
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource

internal object ProfilePopupTestTags {
    const val NOTIFICATIONS = "profile-popup-notifications"
    const val NOTIFICATIONS_UNREAD_DOT = "profile-popup-notifications-unread-dot"
}

/**
 * @param showNotificationDot 是否在“通知”上显示未读红点, 与顶栏头像上的红点一致.
 */
@Composable
internal fun ProfilePopupLayout(
    state: AccountSettingsState,
    onClickLogin: () -> Unit,
    onClickEditAvatar: () -> Unit,
    onClickEditProfile: () -> Unit,
    showNotificationDot: Boolean,
    onClickNotifications: () -> Unit,
    onClickPlaybackHistory: () -> Unit,
    onClickSettings: () -> Unit,
    onClickLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLogin = remember(state) { state.selfInfo.isSessionValid == true }
    val notLoggedInText = stringResource(Lang.settings_account_popup_not_logged_in)
    val editProfileText = stringResource(Lang.settings_account_popup_edit_profile)
    val loginRegisterText = stringResource(Lang.settings_account_popup_login_register)
    val notificationsText = stringResource(Lang.notification_title)
    val hasUnreadNotificationsText = stringResource(Lang.notification_has_unread)
    val playbackHistoryText = stringResource(Lang.playback_history_title)
    val settingsText = stringResource(Lang.settings)
    val logoutText = stringResource(Lang.settings_account_popup_logout)
    Column(modifier) {
        Box(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            HeroIcon {
                AvatarImage(
                    url = state.selfInfo.selfInfo?.avatarUrl?.takeIf { isLogin },
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .placeholder(state.selfInfo.isLoading),
                )
            }
        }
        val (title, _) = state.selfInfo.selfInfo.calculateDisplay()
        val showEmail = false

        Text(
            if (isLogin) title else notLoggedInText,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(
                    bottom = if (showEmail) 4.dp else 0.dp,
                )
                .fillMaxWidth(),
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.MiddleEllipsis,
        )
        if (showEmail) {
            Text(
                remember(state) {
                    state.selfInfo.selfInfo?.email ?: ""
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 2.dp, bottom = 8.dp)
                    .fillMaxWidth(),
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.MiddleEllipsis,
            )
        }

        SettingsTab(
            modifier = Modifier
                .padding(top = 24.dp)
                .fillMaxWidth(),
        ) {
            Column {
                if (isLogin) {
                    TextItem(
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = editProfileText) },
                        onClick = onClickEditProfile,
                    ) {
                        Text(editProfileText)
                    }
                } else {
                    TextItem(
                        icon = { Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = loginRegisterText) },
                        onClick = onClickLogin,
                    ) {
                        Text(loginRegisterText)
                    }
                }

                // 游客也有这一项: 公告对所有人可见
                TextItem(
                    Modifier.testTag(ProfilePopupTestTags.NOTIFICATIONS),
                    icon = {
                        BadgedBox(
                            badge = {
                                if (showNotificationDot) {
                                    Badge(
                                        Modifier
                                            .testTag(ProfilePopupTestTags.NOTIFICATIONS_UNREAD_DOT)
                                            .semantics { contentDescription = hasUnreadNotificationsText },
                                    )
                                }
                            },
                        ) {
                            Icon(Icons.Outlined.Notifications, contentDescription = notificationsText)
                        }
                    },
                    onClick = onClickNotifications,
                ) {
                    Text(notificationsText)
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.History, contentDescription = playbackHistoryText) },
                    onClick = onClickPlaybackHistory,
                ) {
                    Text(playbackHistoryText)
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = settingsText) },
                    onClick = onClickSettings,
                ) {
                    Text(settingsText)
                }

                if (isLogin) {
                    TextItem(
                        icon = {
                            ProvideContentColor(MaterialTheme.colorScheme.error) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = logoutText,
                                )
                            }
                        },
                        onClick = onClickLogout,
                    ) {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Text(logoutText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditableSelfAvatar(
    selfInfo: SelfInfoUiState,
    onClickEditAvatar: () -> Unit,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(96.dp, 96.dp),
) {
    var showEditAvatarScrim by remember { mutableStateOf(false) }

    Box(
        modifier
            .size(size)
            .hoverable(
                onHover = { showEditAvatarScrim = true },
                onUnhover = { showEditAvatarScrim = false },
            ),
    ) {
        AvatarImage(
            url = selfInfo.selfInfo?.avatarUrl,
            Modifier
                .size(size)
                .clip(CircleShape)
                .placeholder(selfInfo.isLoading),
        )
        AniAnimatedVisibility(
            showEditAvatarScrim,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(DrawerDefaults.scrimColor),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape,
                ) {
                    IconButton(onClickEditAvatar) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = "Edit avatar",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Preview
private fun PreviewAccountSettingsPopupLayout() {
    ProvideCompositionLocalsForPreview {
        Surface {
            ProfilePopupLayout(
                TestAccountSettingsState,
                onClickLogin = { },
                onClickEditAvatar = { },
                onClickEditProfile = { },
                showNotificationDot = true,
                onClickNotifications = { },
                onClickPlaybackHistory = { },
                onClickSettings = { },
                onClickLogout = { },
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}
