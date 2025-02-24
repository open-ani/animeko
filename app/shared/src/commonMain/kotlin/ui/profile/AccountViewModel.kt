/*
 * Copyright (C) 2024 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.profile

import androidx.annotation.UiThread
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.him188.ani.app.domain.session.OpaqueSession
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.domain.session.userInfo
import me.him188.ani.app.ui.foundation.AbstractViewModel
import me.him188.ani.app.ui.foundation.launchInBackground
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

// TODO: review and maybe refactor AccountViewModel
class AccountViewModel : AbstractViewModel(), KoinComponent {
    private val sessionManager: SessionManager by inject()

    @OptIn(OpaqueSession::class)
    val selfInfo by sessionManager.userInfo.produceState(null)

    var logoutEnabled by mutableStateOf(true)
        private set

    // val authState = AuthState()

    @UiThread
    fun logout() {
        logoutEnabled = false
        launchInBackground {
            sessionManager.clearSession()
        }
        logoutEnabled = true
    }
}
