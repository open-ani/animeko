/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.Stable


private object AniBuildConfigAndroid : AniBuildConfig {
    override val versionName: String
        get() = BuildConfig.VERSION_NAME
    override val isDebug: Boolean
        get() = BuildConfig.DEBUG
    override val aniAuthServerUrl: String
        get() = BuildConfig.ANI_AUTH_SERVER_URL
    override val dandanplayAppId: String
        get() = BuildConfig.DANDANPLAY_APP_ID
    override val dandanplayAppSecret: String
        get() = BuildConfig.DANDANPLAY_APP_SECRET
    override val sentryDsn: String
        get() = BuildConfig.SENTRY_DSN
}

@Stable
@PublishedApi
internal actual val currentAniBuildConfigImpl: AniBuildConfig
    get() = AniBuildConfigAndroid
