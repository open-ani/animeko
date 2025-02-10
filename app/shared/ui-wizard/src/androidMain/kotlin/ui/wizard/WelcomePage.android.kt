/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.wizard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.compose.rememberNavController
import me.him188.ani.app.ui.foundation.ProvideCompositionLocalsForPreview
import me.him188.ani.app.ui.wizard.navigation.rememberWizardController

@Preview(showBackground = true, device = "spec:width=411dp,height=891dp", showSystemUi = false)
@Preview(showBackground = true, device = "spec:width=1920px,height=1080px,dpi=240", showSystemUi = false)
@Composable
fun PreviewWelcomePage() {
    ProvideCompositionLocalsForPreview {
        val scope = rememberCoroutineScope()
        WelcomePage(
            rememberNavController(),
            rememberWizardController(),
            remember { createTestWizardPresentationState(scope) },
            onFinishWizard = { },
            contactActions = { },
        )
    }
}