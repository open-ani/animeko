/*
 * Copyright (C) 2024-2025 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

package me.him188.ani.app.ui.settings.tabs.network

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.flow.Flow
import me.him188.ani.app.data.models.preference.MediaSourceProxySettings
import me.him188.ani.app.data.models.preference.ProxyAuthorization
import me.him188.ani.app.data.models.preference.ProxyConfig
import me.him188.ani.app.data.models.preference.ProxyMode
import me.him188.ani.app.data.models.preference.ProxySettings
import me.him188.ani.app.ui.foundation.icons.Animeko
import me.him188.ani.app.ui.foundation.icons.AnimekoIconColor
import me.him188.ani.app.ui.foundation.icons.BangumiNext
import me.him188.ani.app.ui.foundation.icons.BangumiNextIconColor

@Stable
class ConfigureProxyState(
    val state: Flow<ConfigureProxyUIState>,
    private val onUpdateConfig: (ProxyUIConfig) -> Unit,
    val onRequestReTest: () -> Unit,
) {
    fun updateConfig(
        currentConfig: ProxyUIConfig,
        newConfig: ProxyUIConfig,
        currentSystemProxy: SystemProxyPresentation
    ) {
        if (shouldRerunProxyTestManually(currentConfig, newConfig, currentSystemProxy)) {
            onRequestReTest()
        }
        onUpdateConfig(newConfig)
    }

    private fun shouldRerunProxyTestManually(
        prev: ProxyUIConfig,
        curr: ProxyUIConfig,
        systemProxy: SystemProxyPresentation
    ): Boolean {
        if (prev == curr) return true

        val prevMode = prev.mode
        val currMode = curr.mode
        val noSystemProxy = systemProxy is SystemProxyPresentation.NotDetected

        if (prevMode == ProxyUIMode.SYSTEM && currMode == ProxyUIMode.DISABLED && noSystemProxy) {
            return true
        }
        if (prevMode == ProxyUIMode.DISABLED && currMode == ProxyUIMode.SYSTEM && noSystemProxy) {
            return true
        }
        return false
    }
}

@Immutable
sealed class SystemProxyPresentation {
    @Immutable
    data object Detecting : SystemProxyPresentation()

    @Immutable
    data class Detected(val proxyConfig: ProxyConfig) : SystemProxyPresentation()

    @Immutable
    data object NotDetected : SystemProxyPresentation()
}

@Immutable
enum class ProxyTestCaseEnums {
    ANI,
    BANGUMI,
    BANGUMI_NEXT,
    TMDB,
}

@Immutable
sealed class ProxyTestCase(
    val name: ProxyTestCaseEnums,
    val icon: ImageVector,
    val color: Color
) {
    data object AniDanmakuApi : ProxyTestCase(
        name = ProxyTestCaseEnums.ANI,
        icon = Icons.Default.Animeko,
        color = AnimekoIconColor,
    )

    data object BangumiApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI,
        icon = Icons.Default.BangumiNext,
        color = BangumiNextIconColor,
    )

    data object BangumiNextApi : ProxyTestCase(
        name = ProxyTestCaseEnums.BANGUMI_NEXT,
        icon = Icons.Default.BangumiNext,
        color = BangumiNextIconColor,
    )

    data object TmdbApi : ProxyTestCase(
        name = ProxyTestCaseEnums.TMDB,
        icon = Icons.Default.Movie,
        color = TmdbIconColor,
    )
}

/** TMDB 品牌色. */
private val TmdbIconColor = Color(0x01, 0xB4, 0xE4)

// region transform between ui ProxyUIConfig and data ProxySettings

fun ProxyMode.toUIMode(): ProxyUIMode {
    return when (this) {
        ProxyMode.DISABLED -> ProxyUIMode.DISABLED
        ProxyMode.SYSTEM -> ProxyUIMode.SYSTEM
        ProxyMode.CUSTOM -> ProxyUIMode.CUSTOM
    }
}

fun ProxyUIMode.toDataMode(): ProxyMode {
    return when (this) {
        ProxyUIMode.DISABLED -> ProxyMode.DISABLED
        ProxyUIMode.SYSTEM -> ProxyMode.SYSTEM
        ProxyUIMode.CUSTOM -> ProxyMode.CUSTOM
    }
}

fun ProxySettings.toUIConfig(): ProxyUIConfig {
    return ProxyUIConfig(
        mode = default.mode.toUIMode(),
        manualUrl = default.customConfig.url,
        manualUsername = default.customConfig.authorization?.username,
        manualPassword = default.customConfig.authorization?.password,
    )
}

fun ProxyUIConfig.toDataSettings(): ProxySettings {
    return ProxySettings(
        default = MediaSourceProxySettings(
            mode = mode.toDataMode(),
            customConfig = MediaSourceProxySettings.Default.customConfig.copy(
                url = manualUrl,
                authorization = if (manualUsername != null && manualPassword != null) {
                    ProxyAuthorization(manualUsername, manualPassword)
                } else null,
            ),
        ),
    )
}

// endregion