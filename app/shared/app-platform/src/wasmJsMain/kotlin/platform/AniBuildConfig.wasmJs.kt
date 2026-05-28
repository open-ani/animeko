/*
 * Copyright (C) 2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.platform

import androidx.compose.runtime.Stable

@PublishedApi
@Stable
internal actual val currentAniBuildConfigImpl: AniBuildConfig = WebAniBuildConfig

private object WebAniBuildConfig : AniBuildConfig {
    override val versionName: String = "0.0.0-web"
    override val isDebug: Boolean = true
    override val dandanplayAppId: String = ""
    override val dandanplayAppSecret: String = ""
    override val sentryDsn: String = ""
    override val distroChannel: String = "web"
    override val sentryEnabled: Boolean = false
    override val analyticsEnabled: Boolean = false
}
