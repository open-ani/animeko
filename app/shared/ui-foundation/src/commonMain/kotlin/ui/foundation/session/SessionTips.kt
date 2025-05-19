/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.foundation.session

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import me.him188.ani.app.domain.session.auth.AuthState
import me.him188.ani.app.domain.session.auth.TestAuthState
import me.him188.ani.app.domain.session.auth.TestGuestAuthState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.paddingIfNotEmpty
import me.him188.ani.app.ui.foundation.text.ProvideContentColor
import me.him188.ani.utils.platform.annotations.TestOnly
import org.jetbrains.compose.ui.tooling.preview.PreviewLightDark

// 留着以后可能要改成支持其他错误类型

//
//@Immutable
//sealed class SessionTipsKind {
//    /**
//     * 正在登录或者正在加载数据
//     */
//    data object Loading : SessionTipsKind()
//
//    /**
//     * 该操作需要登录
//     */
//    data class AuthorizationRequired(
//        /**
//         * 之前是否登录过.
//         * 为 `true` 时意味着之前登录过的会话过期了.
//         * 为 `false` 时意味着没有保存的会话信息.
//         */
//        val wasLoggedIn: Boolean,
//    ) : SessionTipsKind()
//
//    /**
//     * 操作成功
//     */
//    data object Success : SessionTipsKind()
//
//    /**
//     * 客户端网络错误
//     */
//    data object NetworkError : SessionTipsKind()
//
//    /**
//     * 服务器响应了 500
//     */
//    data object ServiceUnavailable : SessionTipsKind()
//}
//
//fun State<SessionStatus>.toSessionTipKind() =
//    when (value) {
//        is SessionStatus.Verified -> SessionTipsKind.Success
//        is SessionStatus.Verifying -> SessionTipsKind.Loading
//        SessionStatus.Refreshing -> SessionTipsKind.Loading
//        SessionStatus.NoToken -> SessionTipsKind.AuthorizationRequired(wasLoggedIn = false)
//        SessionStatus.Expired -> SessionTipsKind.AuthorizationRequired(wasLoggedIn = true)
//        SessionStatus.NetworkError -> SessionTipsKind.NetworkError
//        SessionStatus.ServiceUnavailable -> SessionTipsKind.ServiceUnavailable
//    }

/**
 * 用于显示未登录时的提示和相关动作按钮的区域.
 *
 * 占用两三行的高度, 包含两个按钮, 一个用于登录, 一个用于搜索.
 *
 * @param guest
 */
@Composable
fun SessionTipsArea(
    state: AuthState,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    guest: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.paddingIfNotEmpty(horizontal = 16.dp).fillMaxWidth().widthIn(max = 400.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        when (state) {
            is AuthState.Success -> {
                if (state.isGuest) {
                    guest()
                }
            }

            is AuthState.AwaitingResult -> {
                CircularProgressIndicator()
            }

            is AuthState.TokenExpired -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.HowToReg, null)
                    Text("登录过期，请重新登录")
                }
                FilledTonalButton(onLogin, Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.Login, null)
                    Text("登录")
                }
            }

            is AuthState.NetworkError -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.CloudOff, null)
                    Text("网络错误，请检查网络连接")
                }
                RetryButton(onRetry)
            }

            /*SessionStatus.ServiceUnavailable -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SyncProblem, null)
                    Text("服务异常，请稍后再试")
                }
                RetryButton(onRetry)
            }*/

            is AuthState.NotAuthed -> guest()

            is AuthState.UnknownError -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.SyncProblem, null)
                    Text("未知错误，请重试")
                }
                RetryButton(onRetry)
            }
        }
    }
}

@Composable
private fun RetryButton(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(onRetry, modifier.fillMaxWidth()) {
        Icon(Icons.Rounded.Sync, null)
        Text("重试", Modifier.padding(start = 8.dp))
    }
}

@Stable
private val NO_ACTION = {}

@Composable
fun SessionTipsIcon(
    state: AuthState,
    onLogin: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    showLoading: Boolean = true,
    showLabel: Boolean = true,
) {
    val action = when (state) {
        AuthState.NotAuthed -> onLogin
        is AuthState.Success -> {
            if (state.isGuest) onLogin else NO_ACTION
        }

        is AuthState.AwaitingResult -> NO_ACTION
        AuthState.TokenExpired -> onLogin
        AuthState.NetworkError -> onRetry
        is AuthState.UnknownError -> onLogin
    }
    TextButton(
        action,
        modifier.animateContentSize(),
        enabled = !state.isKnownLoggedIn,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is AuthState.Success -> {
                    if (state.isGuest && showLabel) {
                        Text("游客模式")
                    }
                }

                is AuthState.AwaitingResult -> {
                    if (showLoading) {
                        var rotation by remember { mutableStateOf(0f) }
                        LaunchedEffect(true) {
                            animate(
                                360f,
                                0f,
                                animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
                            ) { value, _ ->
                                rotation = value
                            }
                        }
                        Box(
                            Modifier.graphicsLayer {
                                rotationZ = rotation
                            },
                        ) {
                            Icon(Icons.Rounded.Sync, "正在刷新", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                is AuthState.NotAuthed -> {
                    ProvideContentColor(MaterialTheme.colorScheme.primary) {
                        Icon(Icons.Rounded.HowToReg, "登录")
                        Text("登录")
                    }
                }

                is AuthState.TokenExpired -> {
                    ProvideContentColor(MaterialTheme.colorScheme.error) {
                        Icon(
                            Icons.Rounded.SyncProblem,
                            "登录过期",
                        )
                        if (showLabel) {
                            Text("登录过期")
                        }
                    }
                }

                is AuthState.NetworkError -> {
                    ProvideContentColor(MaterialTheme.colorScheme.error) {
                        Icon(
                            Icons.Rounded.SyncProblem,
                            "网络错误",
                            tint = MaterialTheme.colorScheme.error,
                        )
                        if (showLabel) {
                            Text("网络错误")
                        }
                    }
                }

                is AuthState.UnknownError -> {
                    ProvideContentColor(MaterialTheme.colorScheme.error) {
                        Icon(
                            Icons.Rounded.SyncProblem,
                            "未知错误",
                            tint = MaterialTheme.colorScheme.error,
                        )
                        if (showLabel) {
                            Text("未知错误")
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun PreviewSessionTipsAreaImpl(
    authState: AuthState,
    modifier: Modifier = Modifier,
) {
    Column {
        Text(authState.toString(), Modifier.padding(bottom = 4.dp))
        Surface(Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
            SessionTipsArea(authState, {}, {}, {}, modifier)
        }
    }
}

@Composable
private fun PreviewSessionTipsIconImpl(
    authState: AuthState,
    modifier: Modifier = Modifier,
) {
    Column {
        Text(authState.toString(), Modifier.padding(bottom = 4.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            SessionTipsIcon(authState, {}, {}, modifier)
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSessionTipsArea() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                for (status in TestSessionStatuses) {
                    PreviewSessionTipsAreaImpl(status)
                }
            }
        }
    }
}

@OptIn(TestOnly::class)
@Composable
@PreviewLightDark
private fun PreviewSessionTipsIcon() {
    ProvideCompositionLocalsForPreview {
        Surface {
            Column(verticalArrangement = Arrangement.spacedBy(32.dp)) {
                for (status in TestSessionStatuses) {
                    PreviewSessionTipsIconImpl(status)
                }
            }
        }
    }
}

@Stable
@TestOnly
val TestSessionStatuses
    get() = listOf(
        AuthState.NotAuthed,
        AuthState.AwaitingToken("REFRESH"),
        AuthState.AwaitingUserInfo("REFRESH"),
        AuthState.NetworkError,
        AuthState.TokenExpired,
        AuthState.UnknownError(Exception()),
        TestAuthState,
        TestGuestAuthState,
    )
