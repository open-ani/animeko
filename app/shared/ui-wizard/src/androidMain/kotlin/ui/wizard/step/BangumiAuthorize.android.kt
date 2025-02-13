/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard.step

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import me.him188.ani.app.domain.session.AuthStateNew
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.foundation.layout.currentWindowAdaptiveInfo1
import me.him188.ani.app.ui.wizard.WizardLayoutParams

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepInitial() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthStateNew.Idle,
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepAwaitingResult() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthStateNew.AwaitingResult(""),
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepError() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthStateNew.Timeout,
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiAuthorizeStepSuccess() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthStateNew.Success(
                "StageGuard has long username",
                "https://lain.bgm.tv/pic/cover/l/44/7d/467461_HHw4K.jpg",
                isGuest = false,
            ),
            showTokenAuthorizePage = false,
            onSetShowTokenAuthorizePage = { },
            onClickAuthorize = { },
            onAuthorizeViaToken = { },
            onCancelAuthorize = { },
            contactActions = { },
            onClickNavigateToBangumiDev = { },
        )
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewBangumiTokenAuthorizePage() {
    ProvideCompositionLocalsForPreview {
        BangumiAuthorizeStep(
            authorizeState = AuthStateNew.Idle,
            showTokenAuthorizePage = true,
            onSetShowTokenAuthorizePage = { },
            contactActions = { },
            onClickAuthorize = { },
            onCancelAuthorize = { },
            onAuthorizeViaToken = { },
            onClickNavigateToBangumiDev = { },
        )
    }
}