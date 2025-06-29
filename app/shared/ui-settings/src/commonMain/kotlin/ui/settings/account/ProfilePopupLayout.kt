/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
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
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import me.him188.ani.app.ui.settings.SettingsTab
import me.him188.ani.app.ui.settings.framework.components.TextItem
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
internal fun ProfilePopupLayout(
    state: AccountSettingsState,
    onClickLogin: () -> Unit,
    onClickEditAvatar: () -> Unit,
    onClickEditProfile: () -> Unit,
    onClickSettings: () -> Unit,
    onClickLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLogin = remember(state) { state.selfInfo.isSessionValid == true }
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
                    url = state.selfInfo.selfInfo?.avatarUrl,
                    Modifier
                        .clip(CircleShape)
                        .placeholder(state.selfInfo.isLoading),
                )
            }
        }
        val (title, _) = state.selfInfo.selfInfo.calculateDisplay()
        val showEmail = false

        Text(
            if (isLogin) {
                title
            } else {
                "未登录"
            },
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
                        icon = { Icon(Icons.Outlined.Edit, contentDescription = "Edit profile settings") },
                        onClick = onClickEditProfile,
                    ) {
                        Text("编辑个人资料")
                    }
                } else {
                    TextItem(
                        icon = { Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = "Login") },
                        onClick = onClickLogin,
                    ) {
                        Text("登录 / 注册")
                    }
                }

                TextItem(
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Settings") },
                    onClick = onClickSettings,
                ) {
                    Text("设置")
                }

                if (isLogin) {
                    TextItem(
                        icon = {
                            ProvideContentColor(MaterialTheme.colorScheme.error) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.Logout,
                                    contentDescription = "Logout",
                                )
                            }
                        },
                        onClick = onClickLogout,
                    ) {
                        ProvideContentColor(MaterialTheme.colorScheme.error) {
                            Text("退出登录")
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
                { },
                { },
                { },
                { },
                { },
                modifier = Modifier.widthIn(max = 360.dp),
            )
        }
    }
}