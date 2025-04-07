/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.rendering

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import me.him188.ani.app.data.network.protocol.ReleaseClass


@Composable
fun ReleaseClassIcon(releaseClass: ReleaseClass, modifier: Modifier = Modifier) {
    when (releaseClass) {
        ReleaseClass.ALPHA -> Icon(Icons.Outlined.RocketLaunch, null, modifier)
        ReleaseClass.BETA -> Icon(Icons.Outlined.Science, null, modifier)
        ReleaseClass.RC, ReleaseClass.STABLE -> Icon(Icons.Outlined.Verified, null, modifier)
    }
}

@Stable
fun guessReleaseClass(version: String): ReleaseClass {
    val metadata = version.substringAfter("-", "").lowercase()
    return when {
        metadata.isEmpty() -> ReleaseClass.STABLE
        "alpha" in metadata || "dev" in metadata -> ReleaseClass.ALPHA
        "beta" in metadata -> ReleaseClass.BETA
        "rc" in metadata -> ReleaseClass.RC
        else -> ReleaseClass.STABLE
    }
}
