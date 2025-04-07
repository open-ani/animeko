/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.about

import me.him188.ani.app.domain.media.cache.MediaCacheManager
import me.him188.ani.app.domain.session.SessionManager
import me.him188.ani.app.navigation.BrowserNavigator
import me.him188.ani.app.ui.foundation.AbstractViewModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class AboutTabViewModel : AbstractViewModel(), KoinComponent {
    private val browserNavigator: BrowserNavigator by inject()
    private val sessionManager: SessionManager by inject()
    private val cacheManager: MediaCacheManager by inject()

//    val debugInfo = debugInfoFlow().shareInBackground(started = SharingStarted.Companion.Eagerly)
//
//    @OptIn(OpaqueSession::class)
//    private fun debugInfoFlow() = combine(
//        sessionManager.state,
//        sessionManager.processingRequest.flatMapLatest { it?.state ?: flowOf(null) },
//        sessionManager.isSessionVerified,
//    ) { session, processingRequest, isSessionValid ->
//        DebugInfo(
//            properties = buildMap {
//                val buildConfig = currentAniBuildConfig
//                put("isDebug", buildConfig.isDebug.toString())
//                if (buildConfig.isDebug) {
//                    put("accessToken", session.unverifiedAccessTokenOrNull)
//                    put("domain/session", session.toString())
//                }
//                put("processingRequest.state", processingRequest.toString())
//                put("sessionManager.isSessionValid", isSessionValid.toString())
//            },
//        )
//    }
}
