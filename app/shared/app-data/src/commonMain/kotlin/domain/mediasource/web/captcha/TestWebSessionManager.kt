/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.domain.mediasource.web.captcha

import kotlinx.coroutines.CoroutineScope
import me.him188.ani.app.domain.mediasource.web.PageEvaluator
import me.him188.ani.utils.ktor.asScopedHttpClient
import me.him188.ani.utils.ktor.createDefaultHttpClient
import me.him188.ani.utils.platform.annotations.TestOnly

/**
 * 供 UI 预览与测试使用的 [WebSessionManager]: 无浏览器支持, 直连 HTTP.
 */
@TestOnly
fun createTestWebSessionManager(
    backgroundScope: CoroutineScope,
): WebSessionManager = WebSessionManager(
    browserFactory = UnsupportedCaptchaBrowserFactory,
    evaluator = PageEvaluator(),
    cookieJar = WebSourceCookieJar(),
    identityRegistry = WebSourceIdentityRegistry(),
    client = createDefaultHttpClient().asScopedHttpClient(),
    backgroundScope = backgroundScope,
)
