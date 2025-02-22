/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.main

import me.him188.ani.app.domain.session.AniAuthConfigurator
import me.him188.ani.app.domain.session.NoopAniAuthClient
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MainScreenSharedViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()

    private val authConfigurator = AniAuthConfigurator(
        sessionManager = sessionManager,
        authClient = NoopAniAuthClient,
        onLaunchAuthorize = { },
        parentCoroutineContext = backgroundScope.coroutineContext,
    )

    val authState = authConfigurator.state
}