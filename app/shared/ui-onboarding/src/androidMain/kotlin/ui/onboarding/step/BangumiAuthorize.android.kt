/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.onboarding.step

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.domain.session.AuthState
import me.him188.ani.app.domain.session.TestAuthState
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.utils.platform.annotations.TestOnly

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepInitial() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthState.NotAuthed,
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeByToken = { },
            onSkip = {},
            onClickNavigateToBangumiDev = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepAwaitingResult() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthState.AwaitingToken(""),
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeByToken = { },
            onSkip = {},
            onClickNavigateToBangumiDev = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepError() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthState.NetworkError,
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeByToken = { },
            onSkip = {},
            onClickNavigateToBangumiDev = { },
        )
    }
}

@OptIn(TestOnly::class)
@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepSuccess() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = TestAuthState,
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            onClickAuthorize = { },
            onAuthorizeByToken = { },
            onCancelAuthorize = { },
            contactActions = { },
            onSkip = {},
            onClickNavigateToBangumiDev = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiTokenAuthorizePage() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthState.NotAuthed,
            showTokenAuthorizePage = true,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeByToken = { },
            onSkip = {},
            onClickNavigateToBangumiDev = { },
        )
    }
}