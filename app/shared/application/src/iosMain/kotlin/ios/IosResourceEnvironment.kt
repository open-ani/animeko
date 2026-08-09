/*
 * Copyright (C) 2024-2026 OpenAni and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license, which can be found at the following link.
 *
 * https://github.com/open-ani/ani/blob/main/LICENSE
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
@file:OptIn(InternalResourceApi::class)

package me.him188.ani.app.ios

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.intl.Locale
import org.jetbrains.compose.resources.ComposeEnvironment
import org.jetbrains.compose.resources.InternalResourceApi
import org.jetbrains.compose.resources.LocalComposeEnvironment
import org.jetbrains.compose.resources.RegionQualifier
import org.jetbrains.compose.resources.ResourceEnvironment
import org.jetbrains.compose.resources.rememberResourceEnvironment

/**
 * Preserves the explicitly selected Chinese script when Compose resolves resources on iOS.
 *
 * Compose Multiplatform 1.10 only considers language and region resource qualifiers. iOS can report
 * locales such as `zh-Hans-SG` and `zh-Hant-SG`, so selecting a region-only Singapore resource would
 * otherwise make both locales use the same script.
 */
@Composable
internal fun ProvideIosResourceEnvironment(content: @Composable () -> Unit) {
    val defaultEnvironment = rememberResourceEnvironment()
    val locale = Locale.current
    val resourceRegion = locale.resourceRegion()
    val resourceEnvironment = remember(defaultEnvironment, resourceRegion) {
        ResourceEnvironment(
            language = defaultEnvironment.language,
            region = RegionQualifier(resourceRegion),
            theme = defaultEnvironment.theme,
            density = defaultEnvironment.density,
        )
    }
    val composeEnvironment = remember(resourceEnvironment) {
        FixedComposeEnvironment(resourceEnvironment)
    }

    CompositionLocalProvider(
        LocalComposeEnvironment provides composeEnvironment,
        content = content,
    )
}

private fun Locale.resourceRegion(): String {
    return resolveIosResourceRegion(language, script, region)
}

internal fun resolveIosResourceRegion(language: String, script: String, region: String): String {
    if (language != "zh") return region

    return when (script) {
        "Hans" -> "CN"
        "Hant" -> when (region) {
            "HK", "MO", "TW" -> region
            else -> "HK"
        }

        else -> region
    }
}

private class FixedComposeEnvironment(
    private val environment: ResourceEnvironment,
) : ComposeEnvironment {
    @Composable
    override fun rememberEnvironment(): ResourceEnvironment = environment
}
