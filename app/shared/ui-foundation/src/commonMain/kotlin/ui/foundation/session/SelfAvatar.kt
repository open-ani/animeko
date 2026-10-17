/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.session

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgeDefaults
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.him188.ani.app.data.repository.user.UserRepository
import me.him188.ani.app.navigation.AniNavigator
import me.him188.ani.app.navigation.LocalNavigator
import me.him188.ani.app.tools.rememberUiMonoTasker
import me.him188.ani.app.ui.external.placeholder.placeholder
import me.him188.ani.app.ui.foundation.avatar.AvatarImage
import me.him188.ani.app.ui.foundation.ifThen
import me.him188.ani.app.ui.lang.Lang
import me.him188.ani.app.ui.lang.login_sign_in
import me.him188.ani.app.ui.lang.notification_has_unread
import me.him188.ani.app.ui.lang.playback_history_title
import me.him188.ani.app.ui.lang.settings_account_confirm_logout
import me.him188.ani.app.ui.lang.settings_account_logout
import me.him188.ani.app.ui.lang.settings_account_settings
import me.him188.ani.app.ui.lang.subject_collection_cancel
import me.him188.ani.app.ui.user.SelfInfoUiState
import org.jetbrains.compose.resources.stringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.coroutines.CoroutineContext

/**
 * 是否在 [SelfAvatar] 的右上角显示未读红点.
 *
 * 由主界面 (`MainScreen`) 根据通知和公告的未读状态提供, 作用于各个主页面顶栏的头像.
 * 主界面以外的地方 (包括 TV) 不提供, 不显示红点.
 */
val LocalSelfAvatarUnreadDot: ProvidableCompositionLocal<Boolean> = compositionLocalOf { false }

/**
 * 当前用户的头像. 未登录时显示为 "登录" 按钮.
 *
 * 已登录且 [LocalSelfAvatarUnreadDot] 为 `true` 时, 头像右上角显示未读红点, 无障碍描述包含 "有未读通知".
 */
@Composable
fun SelfAvatar(
    state: SelfInfoUiState,
    size: DpSize, // = DpSize(48.dp, 48.dp)
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val signInText = stringResource(Lang.login_sign_in)

    @Composable
    fun AvatarSurface(surfaceModifier: Modifier, content: @Composable () -> Unit) = if (onClick == null) {
        Surface(surfaceModifier, shape = CircleShape, content = content)
    } else {
        Surface(onClick, surfaceModifier, shape = CircleShape, content = content)
    }

    if (state.isLoading) {
        AvatarSurface(modifier) {
            // 加载中时展示 placeholder
            AvatarImage(
                url = state.selfInfo?.avatarUrl,
                Modifier.size(size).clip(CircleShape).placeholder(state.selfInfo == null),
            )
        }
    } else {
        if (state.isSessionValid == false || state.selfInfo == null) {
            TextButton(onClick ?: {}, modifier = modifier) {
                Text(signInText)
            }
        } else {
            val showUnreadDot = LocalSelfAvatarUnreadDot.current
            val unreadDotColor = BadgeDefaults.containerColor
            val unreadDescription = stringResource(Lang.notification_has_unread)
            AvatarSurface(
                modifier.ifThen(showUnreadDot) {
                    unreadDot(size, unreadDotColor)
                        .semantics { contentDescription = unreadDescription }
                },
            ) {
                AvatarImage(
                    url = state.selfInfo.avatarUrl,
                    modifier = Modifier.size(size).clip(CircleShape),
                )
            }
        }
    }
}

/**
 * 未读红点的直径, 与 M3 不带内容的 [Badge] 相同.
 */
private val UnreadDotSize = 6.dp

/**
 * 红点周围从头像上挖去的宽度.
 */
private val UnreadDotRingWidth = 2.dp

private const val Cos45 = 0.70710677f

/**
 * 在位于布局中央, 尺寸为 [avatarSize] 的圆形头像右上角画未读红点.
 *
 * 红点的圆心在头像圆周的右上方 45° 处. [BadgedBox] 把红点放在图标外接正方形的角上, 对圆形头像来说那里在圆外, 红点会悬空.
 * 红点周围从头像上挖去一圈, 露出头像下面的顶栏背景, 使红点在照片头像上也清晰可辨.
 * 挖空而不是描一圈固定颜色, 是因为顶栏可能是毛玻璃, 滚动后也会变色.
 */
private fun Modifier.unreadDot(avatarSize: DpSize, color: Color): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val radius = UnreadDotSize.toPx() / 2
            // 头像在布局中央. 外层的 minimumInteractiveComponentSize 可能让布局比头像大.
            val centerToDotX = avatarSize.width.toPx() / 2 * Cos45
            val centerToDotY = avatarSize.height.toPx() / 2 * Cos45
            val dotCenter = Offset(
                x = if (layoutDirection == LayoutDirection.Ltr) center.x + centerToDotX else center.x - centerToDotX,
                y = center.y - centerToDotY,
            )
            drawCircle(
                Color.Black,
                radius = radius + UnreadDotRingWidth.toPx(),
                center = dotCenter,
                blendMode = BlendMode.Clear,
            )
            drawCircle(color, radius = radius, center = dotCenter)
        }

@Stable
interface SelfAvatarActionHandler {
    fun onClickPlaybackHistory()
    fun onClickSettings()
    suspend fun onLogout()
}

private class DefaultSelfAvatarActionHandler(
    private val navigator: AniNavigator,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : SelfAvatarActionHandler, KoinComponent {
    private val userRepo: UserRepository by inject()
    override fun onClickPlaybackHistory() {
        navigator.navigatePlaybackHistory()
    }

    override fun onClickSettings() {
        navigator.navigateSettings()
    }

    override suspend fun onLogout() {
        withContext(dispatcher) {
            userRepo.clearSelfInfo()
        }
    }
}

@Composable
fun rememberSelfAvatarActionHandler(): SelfAvatarActionHandler {
    val navigator = LocalNavigator.current
    return remember(navigator) { DefaultSelfAvatarActionHandler(navigator) }
}

@Composable
private fun SelfAvatarMenus(
    handler: SelfAvatarActionHandler,
    onClickAny: () -> Unit,
) {
    val playbackHistoryText = stringResource(Lang.playback_history_title)
    val settingsText = stringResource(Lang.settings_account_settings)
    val logoutText = stringResource(Lang.settings_account_logout)
    val confirmLogoutText = stringResource(Lang.settings_account_confirm_logout)
    val cancelText = stringResource(Lang.subject_collection_cancel)

    DropdownMenuItem(
        text = { Text(playbackHistoryText) },
        onClick = {
            handler.onClickPlaybackHistory()
            onClickAny()
        },
        leadingIcon = { Icon(Icons.Rounded.History, null) },
    )

    DropdownMenuItem(
        text = { Text(settingsText) },
        onClick = {
            handler.onClickSettings()
            onClickAny()
        },
        leadingIcon = { Icon(Icons.Rounded.Settings, null) },
    )

    val logoutTasker = rememberUiMonoTasker()
    var showLogoutConfirmation by rememberSaveable { mutableStateOf(false) }
    val running by logoutTasker.isRunning.collectAsStateWithLifecycle()
    DropdownMenuItem(
        text = { Text(logoutText, color = MaterialTheme.colorScheme.error) },
        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) },
        onClick = { showLogoutConfirmation = true },
        enabled = !running,
    )
    if (showLogoutConfirmation) {
        AlertDialog(
            { showLogoutConfirmation = false },
            text = { Text(confirmLogoutText) },
            confirmButton = {
                TextButton(
                    {
                        logoutTasker.launch {
                            handler.onLogout()
                            onClickAny()
                        }
                        showLogoutConfirmation = false
                    },
                ) {
                    Text(logoutText, color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton({ showLogoutConfirmation = false }) {
                    Text(cancelText)
                }
            },
        )
    }
}
